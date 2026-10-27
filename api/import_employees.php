<?php
/** Upload Excel .xlsx or UTF-8 CSV. Validate all rows BEFORE transaction commit. */
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/employee_validation.php';
if ($_SERVER['REQUEST_METHOD'] !== 'POST') err('Method not allowed', 405);
if (employee_editor_role($pdo, (int)($_POST['requester_id'] ?? 0)) !== 'admin') err('Chi admin duoc import', 403);
if (!isset($_FILES['file']) || $_FILES['file']['error'] !== UPLOAD_ERR_OK) err('Chua chon file', 422);
$f = $_FILES['file'];
if ($f['size'] <= 0 || $f['size'] > 3*1024*1024) err('File toi da 3MB', 422);
$ext = strtolower(pathinfo($f['name'], PATHINFO_EXTENSION));
if (!in_array($ext, ['csv','xlsx'], true)) err('Chi nhan Excel .xlsx hoac CSV', 422);
function import_xlsx_rows($path) {
    if (!class_exists('ZipArchive') || !function_exists('simplexml_load_string')) {
        throw new RuntimeException('PHP can bat extension zip va simplexml de doc XLSX');
    }
    $zip = new ZipArchive();
    if ($zip->open($path) !== true) throw new InvalidArgumentException('File XLSX khong hop le');
    try {
        $read = function($path) use ($zip) {
            $xml = $zip->getFromName($path);
            if ($xml === false || strlen($xml) > 8*1024*1024) throw new InvalidArgumentException('File XLSX thieu sheet hoac qua lon');
            $dom = simplexml_load_string($xml, 'SimpleXMLElement', LIBXML_NONET | LIBXML_NOCDATA);
            if ($dom === false) throw new InvalidArgumentException('XML khong hop le');
            $dom->registerXPathNamespace('x','http://schemas.openxmlformats.org/spreadsheetml/2006/main');
            return $dom;
        };
        $shared = [];
        if ($zip->locateName('xl/sharedStrings.xml') !== false) {
            $xml = $read('xl/sharedStrings.xml');
            foreach ($xml->xpath('//x:si') as $si) {
                $part = '';
                foreach ($si->xpath('.//x:t') as $t) $part .= (string)$t;
                $shared[] = $part;
            }
        }
        // Use first worksheet only. No macros, external links, or formulas evaluated.
        $xml = $read('xl/worksheets/sheet1.xml');
        $rows = [];
        foreach ($xml->xpath('//x:sheetData/x:row') as $row) {
            $columns = [];
            foreach ($row->xpath('./x:c') as $cell) {
                $ref = (string)$cell['r'];
                if (!preg_match('/^([A-Z]+)\d+$/', $ref, $m)) continue;
                $idx = 0;
                foreach (str_split($m[1]) as $char) $idx = $idx * 26 + ord($char) - 64;
                $idx--;
                if ($idx >= 32) continue;
                $val = $cell->xpath('./x:v');
                $raw = $val ? (string)$val[0] : '';
                if ((string)$cell['t'] === 's') $raw = $shared[(int)$raw] ?? '';
                if ((string)$cell['t'] === 'inlineStr') {
                    $raw = ''; foreach ($cell->xpath('.//x:t') as $t) $raw .= (string)$t;
                }
                $columns[$idx] = $raw;
            }
            if ($columns) {
                $res = [];
                for ($i=0; $i <= max(array_keys($columns)); $i++) $res[] = $columns[$i] ?? '';
                $rows[] = $res;
            }
            if (count($rows) > 501) throw new InvalidArgumentException('Toi da 500 nhan vien');
        }
        return $rows;
    } finally { $zip->close(); }
}
function import_csv_rows($path) {
    $fp = fopen($path,'rb'); if (!$fp) throw new InvalidArgumentException('Khong mo duoc CSV');
    try {
        $first = fgets($fp); rewind($fp);
        $separator = substr_count($first, ';') > substr_count($first, ',') ? ';' : ',';
        $rows = [];
        while (($row = fgetcsv($fp, 4096, $separator, '"', '')) !== false) {
            if (count($rows) === 0 && isset($row[0])) $row[0] = preg_replace('/^\xEF\xBB\xBF/', '', $row[0]);
            if (count($row) === 1 && trim($row[0]) === '') continue;
            $rows[] = $row;
            if (count($rows) > 501) throw new InvalidArgumentException('Toi da 500 nhan vien');
        }
        return $rows;
    } finally { fclose($fp); }
}
try {
    $rows = $ext === 'xlsx' ? import_xlsx_rows($f['tmp_name']) : import_csv_rows($f['tmp_name']);
    if (count($rows) < 2) throw new InvalidArgumentException('Can dong tieu de va it nhat mot nhan vien');
    $headers = array_map(fn($v)=>strtolower(trim((string)$v)), array_shift($rows));
    $required = ['username','full_name','email'];
    foreach ($required as $h) if (!in_array($h,$headers,true)) throw new InvalidArgumentException("Thieu cot $h");
    $allowed = ['username','full_name','email','phone','department_id','position_id','hire_date'];
    $input = [];
    foreach ($rows as $i=>$r) {
        $data = array_combine($headers, array_pad($r, count($headers), ''));
        if ($data === false) throw new InvalidArgumentException('Dong CSV/XLSX khong dung so cot');
        $item = [];
        foreach ($allowed as $key) $item[$key] = $data[$key] ?? null;
        try {
            $item['username'] = employee_username($item['username']);
            $item['full_name'] = employee_name($item['full_name']);
            $item['email'] = employee_email($item['email']);
            $item['phone'] = employee_phone($item['phone']);
            $item['department_id'] = employee_optional_fk($pdo, $item['department_id'], 'departments');
            $item['position_id'] = employee_optional_fk($pdo, $item['position_id'], 'positions');
            // Excel may store dates as day serials, even when displayed as dates.
            if ($ext === 'xlsx' && is_string($item['hire_date']) && preg_match('/^\d{5}(?:\.0+)?$/', $item['hire_date'])) {
                $origin = new DateTimeImmutable('1899-12-30');
                $item['hire_date'] = $origin->modify('+' . (int)$item['hire_date'] . ' days')->format('Y-m-d');
            }
            $item['hire_date'] = employee_hire_date($item['hire_date']);
        } catch (InvalidArgumentException $e) { throw new InvalidArgumentException('Dong '.($i+2).': '.$e->getMessage()); }
        $input[] = $item;
    }
    $staffRoleId = $pdo->query("SELECT id FROM roles WHERE name='staff' LIMIT 1")->fetchColumn();
    if (!$staffRoleId) throw new RuntimeException('Chua co role staff');
    $pdo->beginTransaction();
    $stmt = $pdo->prepare('INSERT INTO employees(username,password_hash,full_name,phone,email,role_id,department_id,position_id,hire_date,status) VALUES(?,?,?,?,?,?,?,?,?,1)');
    $credentials = [];
    foreach ($input as $item) {
        $temporary = bin2hex(random_bytes(8));
        $stmt->execute([$item['username'], password_hash($temporary, PASSWORD_BCRYPT), $item['full_name'], $item['phone'], $item['email'], (int)$staffRoleId, $item['department_id'], $item['position_id'], $item['hire_date']]);
        $credentials[] = ['username'=>$item['username'], 'temporary_password'=>$temporary];
    }
    $pdo->commit();
    // Temporary passwords shown once to admin. Keep result off logs; require reset on first use in production.
    out(['imported'=>count($input), 'temporary_credentials'=>$credentials]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    if ($e instanceof PDOException && $e->getCode() === '23000') err('Trung username/email. Khong nhap bat ky dong nao', 409);
    if ($e instanceof InvalidArgumentException) err($e->getMessage(), 422);
    err('Import that bai: kiem tra file, extension PHP va migration', 500);
}
