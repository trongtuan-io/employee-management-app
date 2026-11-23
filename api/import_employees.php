<?php
/** Upload Excel .xlsx or UTF-8 CSV. Validate all rows BEFORE transaction commit. */
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/employee_validation.php';
require_once __DIR__ . '/auth_guard.php';
require_once __DIR__ . '/positions_logic.php';
require_once __DIR__ . '/hr_proposals_lib.php';
require_once __DIR__ . '/hr_import_policy.php';
$importer=hr_auth($pdo);
hr_require_migration($pdo);
if($_SERVER['REQUEST_METHOD']==='GET'&&($_GET['action']??'')==='batches'){
    $st=$pdo->query('SELECT id,source_name,submitted_by,rows_total,rows_proposed,rows_updated,rows_refreshed,created_at FROM hr_import_batches ORDER BY id DESC LIMIT 100');
    out($st->fetchAll(PDO::FETCH_ASSOC));
}
if($_SERVER['REQUEST_METHOD']!=='POST')err('Method not allowed',405);
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
        // SimpleXML namespaces are registered per node, not inherited by children.
        $xpath = static function (SimpleXMLElement $node, string $expression): array {
            $node->registerXPathNamespace('x', 'http://schemas.openxmlformats.org/spreadsheetml/2006/main');
            $result = $node->xpath($expression);
            return is_array($result) ? $result : [];
        };
        $shared = [];
        if ($zip->locateName('xl/sharedStrings.xml') !== false) {
            $xml = $read('xl/sharedStrings.xml');
            foreach ($xpath($xml, '//x:si') as $si) {
                $part = '';
                foreach ($xpath($si, './/x:t') as $t) $part .= (string)$t;
                $shared[] = $part;
            }
        }
        // Use first worksheet only. No macros, external links, or formulas evaluated.
        $xml = $read('xl/worksheets/sheet1.xml');
        $rows = [];
        foreach ($xpath($xml, '//x:sheetData/x:row') as $row) {
            $columns = [];
            foreach ($xpath($row, './x:c') as $cell) {
                $ref = (string)$cell['r'];
                if (!preg_match('/^([A-Z]+)\d+$/', $ref, $m)) continue;
                $idx = 0;
                foreach (str_split($m[1]) as $char) $idx = $idx * 26 + ord($char) - 64;
                $idx--;
                if ($idx >= 32) continue;
                $val = $xpath($cell, './x:v');
                $raw = $val ? (string)$val[0] : '';
                if ((string)$cell['t'] === 's') $raw = $shared[(int)$raw] ?? '';
                if ((string)$cell['t'] === 'inlineStr') {
                    $raw = ''; foreach ($xpath($cell, './/x:t') as $t) $raw .= (string)$t;
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
    if (count(array_unique($headers)) !== count($headers))
        throw new InvalidArgumentException('File co cot tieu de trung');
    $allowed = ['username','full_name','email','phone','department_id','position_id','hire_date'];
    $input = [];
    foreach ($rows as $i=>$r) {
        // Guard BEFORE array_combine: PHP 8 throws ValueError on extra cells.
        if (count($r) > count($headers))
            throw new InvalidArgumentException('Dong '.($i+2).': du cot so voi tieu de');
        $data = array_combine($headers, array_pad($r, count($headers), ''));
        $item = [];
        foreach ($allowed as $key) $item[$key] = $data[$key] ?? null;
        try {
            $item['username'] = employee_username($item['username']);
            $item['full_name'] = employee_name($item['full_name']);
            $item['email'] = employee_email($item['email']);
            $item['phone'] = hr_import_phone($item['phone']);
            $item['department_id'] = employee_optional_fk($pdo, $item['department_id'], 'departments');
            $item['position_id'] = employee_optional_fk($pdo, $item['position_id'], 'positions');
            // Chuc vu phai thuoc phong ban cua nhan vien (nhat quan voi create/update).
            if ($item['position_id'] !== null) {
                positions_ensure($pdo);
                $pq = $pdo->prepare('SELECT department_id FROM positions WHERE id=?');
                $pq->execute([$item['position_id']]);
                $pDept = $pq->fetchColumn();
                if ($pDept !== false && $pDept !== null && (int)$pDept !== (int)$item['department_id']) {
                    throw new InvalidArgumentException('Chuc vu khong thuoc phong ban cua nhan vien');
                }
            }
            // Excel may store dates as day serials, even when displayed as dates.
            if ($ext === 'xlsx' && is_string($item['hire_date']) && preg_match('/^\d{5}(?:\.0+)?$/', $item['hire_date'])) {
                $origin = new DateTimeImmutable('1899-12-30');
                $item['hire_date'] = $origin->modify('+' . (int)$item['hire_date'] . ' days')->format('Y-m-d');
            }
            $item['hire_date'] = employee_hire_date($item['hire_date']);
        } catch (InvalidArgumentException $e) { throw new InvalidArgumentException('Dong '.($i+2).': '.$e->getMessage()); }
        $input[] = $item;
    }
    // Transaction is all-or-nothing. New hires remain proposals; approval level follows the position.
    // No account is activated by importing a file.
    $seenUsers=[];$seenEmails=[];
    foreach($input as $it){
        $u=strtolower($it['username']);$e=strtolower($it['email']);
        if(isset($seenUsers[$u])||isset($seenEmails[$e]))throw new InvalidArgumentException('Ma NV/email trung trong file');
        $seenUsers[$u]=1;$seenEmails[$e]=1;
    }
    $pdo->beginTransaction();
    $st=$pdo->prepare('INSERT INTO hr_import_batches(source_name,submitted_by,rows_total) VALUES(?,?,?)');
    $st->execute([basename($f['name']),(int)$importer['id'],count($input)]);
    $batchId=(int)$pdo->lastInsertId();
    $proposed=0;$updated=0;$refreshed=0;
    foreach($input as $item){
        $st=$pdo->prepare('SELECT * FROM employees WHERE username=? OR email=? FOR UPDATE');
        $st->execute([$item['username'],$item['email']]);$rowsFound=$st->fetchAll(PDO::FETCH_ASSOC);
        if(count($rowsFound)>1)throw new InvalidArgumentException('Username/email thuoc nhieu nhan vien khac nhau');
        if($rowsFound){
            $existing=$rowsFound[0];
            if(strcasecmp($existing['username'],$item['username'])!==0)throw new InvalidArgumentException('Email trung voi ma nhan vien khac');
            if((int)$existing['role_id']===1)throw new InvalidArgumentException('Khong duoc import de sua tai khoan Admin');
            if($item['department_id']!==null&&(int)$item['department_id']!==(int)$existing['department_id'])throw new InvalidArgumentException('Chuyen phong phai tao de xuat BOD');
            if($item['position_id']!==null&&(int)$item['position_id']!==(int)$existing['position_id'])throw new InvalidArgumentException('Thang chuc phai tao de xuat BOD');
            $changed=hr_import_existing_changes($pdo,$item,$existing,hr_manager_actor($importer));
            if($changed){
                $before=[];foreach($changed as $key=>$value)$before[$key]=$existing[$key]??null;
                $sql='UPDATE employees SET '.implode(',',array_map(fn($k)=>$k.'=?',array_keys($changed))).' WHERE id=?';
                $pdo->prepare($sql)->execute([...array_values($changed),(int)$existing['id']]);
                hr_record_history($pdo,(int)$importer['id'],(int)$existing['id'],'employee.import.updated',$before,$changed);
            }
            $updated++;continue;
        }
        // Reimport an existing pending/draft candidate: refresh that proposal and increase
        // version so BOD can detect changed content. Do not create duplicate candidates.
        $st=$pdo->query("SELECT id,proposed_data,status,proposed_by,workflow_version FROM hr_proposals WHERE proposal_type='recruitment' AND status IN ('draft','hr_pending','pending','approved') FOR UPDATE");
        $existingProposal=null;
        foreach($st->fetchAll(PDO::FETCH_ASSOC) as $p){$old=json_decode($p['proposed_data'],true)?:[];
            if(($old['username']??null)===$item['username']||strcasecmp((string)($old['email']??''),$item['email'])===0){$existingProposal=$p;break;}}
        if($existingProposal){
            if($existingProposal['status']!=='draft')throw new InvalidArgumentException('Ho so da gui duyet; khong duoc import de thay doi');
            if((int)$existingProposal['proposed_by']!==(int)$importer['id'])throw new InvalidArgumentException('Chi duoc sua ho so nhap cua minh qua import');
            $old=json_decode($existingProposal['proposed_data'],true)?:[];
            if(($old['username']??'')!==$item['username'])throw new InvalidArgumentException('Email dang cho duyet voi ma NV khac');
            $new=hr_import_merge_draft($pdo,$old,$item,(int)($existingProposal['workflow_version']??1));
            $pdo->prepare('UPDATE hr_proposals SET proposed_data=?,reservation_email=?,version=version+1 WHERE id=?')
                ->execute([hr_json($new),$new['email'],(int)$existingProposal['id']]);
            hr_record_history($pdo,(int)$importer['id'],null,'proposal.import.refreshed',null,['proposal_id'=>(int)$existingProposal['id']],(int)$existingProposal['id']);
            $refreshed++;continue;
        }
        if(empty($item['position_id']))throw new InvalidArgumentException('Ho so tuyen dung moi phai co position_id');
        $item['reason']='De xuat tuyen dung qua import: '.basename($f['name']);
        hr_save_proposal($pdo,$importer,'recruitment',$item,$batchId);
        $proposed++;
    }
    $pdo->prepare('UPDATE hr_import_batches SET rows_proposed=?,rows_updated=?,rows_refreshed=? WHERE id=?')
        ->execute([$proposed,$updated,$refreshed,$batchId]);
    $pdo->commit();
    out(['imported'=>0,'proposed'=>$proposed,'updated'=>$updated,'refreshed'=>$refreshed,
         'batch_id'=>$batchId,'status'=>hr_manager_actor($importer)?'hr_pending':'draft',
         'pending_approval'=>true,'temporary_credentials'=>[]]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    if ($e instanceof PDOException && $e->getCode() === '23000') err('Trung username/email; da rollback ca dot',409);
    if ($e instanceof InvalidArgumentException) err($e->getMessage(),422);
    err('Import that bai; da rollback ca dot',500);
}
