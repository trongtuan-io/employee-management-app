<?php
/** Import parsing and authorization utilities. Keeps existing auth.php unchanged. */
require_once __DIR__ . '/employee_validation.php';
function batch_actor(PDO $pdo, int $requesterId): int {
    $authorization = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
    if (!$authorization && function_exists('getallheaders')) {
        foreach (getallheaders() as $name => $value) {
            if (strcasecmp($name, 'Authorization') === 0) { $authorization = $value; break; }
        }
    }
    // Matches the EXISTING group's demo auth. Do not treat this as secure in production.
    if (!preg_match('/^Bearer\s+demo-token-([1-9][0-9]*)$/i', trim($authorization), $match)) err('Chua dang nhap', 401);
    $actor = (int)$match[1];
    if ($actor !== $requesterId || employee_editor_role($pdo, $actor) !== 'admin') err('Chi admin duoc thuc hien', 403);
    return $actor;
}
function batch_file_info(array $f): string {
    if (($f['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK || !is_uploaded_file($f['tmp_name'])) throw new InvalidArgumentException('Chua chon file');
    if ($f['size'] <= 0 || $f['size'] > 3*1024*1024) throw new InvalidArgumentException('File toi da 3MB');
    $ext = strtolower(pathinfo($f['name'], PATHINFO_EXTENSION));
    if (!in_array($ext, ['csv', 'xlsx'], true)) throw new InvalidArgumentException('Chi nhan .csv/.xlsx');
    return $ext;
}
function batch_employee_hash(array $row): string {
    // Hash all columns, including password/status, so edits outside this import are not silently undone.
    ksort($row);
    return hash('sha256', json_encode($row, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
}
function batch_load_employee(PDO $pdo, int $employeeId): ?array {
    $s = $pdo->prepare('SELECT * FROM employees WHERE id=? FOR UPDATE');
    $s->execute([$employeeId]);
    $row = $s->fetch(PDO::FETCH_ASSOC);
    return $row ?: null;
}
function batch_has_references(PDO $pdo, int $employeeId): bool {
    // An employee with attendance, leave, salary or other FK references cannot be removed.
    $query = $pdo->prepare("SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND REFERENCED_TABLE_NAME='employees' AND REFERENCED_COLUMN_NAME='id' UNION SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND COLUMN_NAME='employee_id'");
    $query->execute();
    foreach ($query->fetchAll(PDO::FETCH_ASSOC) as $row) {
        if (in_array($row['TABLE_NAME'], ['employee_import_batches', 'employee_import_items'], true)) continue;
        $table = str_replace('`', '``', $row['TABLE_NAME']);
        $column = str_replace('`', '``', $row['COLUMN_NAME']);
        $s = $pdo->prepare("SELECT 1 FROM `{$table}` WHERE `{$column}`=? LIMIT 1");
        $s->execute([$employeeId]);
        if ($s->fetchColumn()) return true;
    }
    return false;
}
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
function batch_parse_file(PDO $pdo, array $f): array {
    $ext = batch_file_info($f);
    $rows = $ext === 'xlsx' ? import_xlsx_rows($f['tmp_name']) : import_csv_rows($f['tmp_name']);
    if (count($rows) < 2) throw new InvalidArgumentException('Can dong tieu de va it nhat mot nhan vien');
    $headers = array_map(fn($v)=>strtolower(trim((string)$v)), array_shift($rows));
    $required = ['username','full_name','email'];
    foreach ($required as $h) if (!in_array($h,$headers,true)) throw new InvalidArgumentException("Thieu cot $h");
    if (count($headers) !== count(array_unique($headers))) throw new InvalidArgumentException('Cot bi trung ten');
    $allowed = ['username','full_name','email','phone','department_id','position_id','hire_date'];
    foreach ($headers as $h) if (!in_array($h, $allowed, true)) throw new InvalidArgumentException('Ten cot khong duoc ho tro: ' . $h);
    $seenUsernames = []; $seenEmails = []; $seenPhones = [];
    $input = [];
    foreach ($rows as $i=>$r) {
        if (count($r) > count($headers)) throw new InvalidArgumentException('Dong '.($i+2).': thua cot');
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
        $usernameKey = strtolower($item['username']);
        $emailKey = strtolower($item['email']);
        if (isset($seenUsernames[$usernameKey]) || isset($seenEmails[$emailKey])
            || ($item['phone'] !== null && isset($seenPhones[$item['phone']]))) {
            throw new InvalidArgumentException('Dong '.($i+2).': trung ma NV, email hoac SDT trong file');
        }
        $seenUsernames[$usernameKey] = true;
        $seenEmails[$emailKey] = true;
        if ($item['phone'] !== null) $seenPhones[$item['phone']] = true;
        $input[] = $item;
    }

    return $input;
}
