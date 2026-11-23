<?php
require_once __DIR__.'/db.php';
require_once __DIR__.'/auth_guard.php';
require_once __DIR__.'/hr_core.php';
$me=require_login($pdo);
hr_require_migration($pdo);
$a=$_GET['action']??'list';$m=$_SERVER['REQUEST_METHOD'];
$empId=(int)($_GET['employee_id']??$_POST['employee_id']??$me['id']);
if($empId<=0)err('employee_id khong hop le',422);
$emp=hr_employee($pdo,$empId);
$self=(int)$me['id']===$empId;
$hr=hr_employee_actor($me);
$allowedTypes=['cccd_front','cccd_back','insurance','degree','certificate','contract','other'];
function hr_document_storage(): string {
    // Configure a path OUTSIDE www/htdocs via environment (never serve directly by URL).
    $dir=getenv('HR_PRIVATE_STORAGE_DIR');
    if(!$dir||!is_string($dir)||!preg_match('/^(?:[A-Za-z]:[\\\\\/]|\/)/',$dir))err('HR_PRIVATE_STORAGE_DIR chua duoc cau hinh ngoai webroot',503);
    if(!is_dir($dir)&&!@mkdir($dir,0700,true)&&!is_dir($dir))err('Khong tao duoc thu muc bao mat HR',503);
    $abs=realpath($dir);
    $docroot=realpath($_SERVER['DOCUMENT_ROOT']??'')?:null;
    if(!$abs || ($docroot&&($abs===$docroot||str_starts_with(strtolower(str_replace('\\','/',$abs)).'/',strtolower(str_replace('\\','/',$docroot)).'/'))))
      err('Thu muc HR phai nam ngoai document root',503);
    return $abs;
}
if($a==='summary'&&$m==='GET'){
    if(!employee_can_view($me,$emp))err('Khong co quyen xem trang thai ho so',403);
    $st=$pdo->prepare('SELECT document_type,status FROM hr_documents WHERE employee_id=? ORDER BY id DESC');$st->execute([$empId]);
    $rows=$st->fetchAll(PDO::FETCH_ASSOC);$valid=0;$pending=0;$update=0;$seen=[];
    $required=['cccd_front','cccd_back','insurance','degree','certificate','contract'];
    foreach($rows as $r){
        $type=$r['document_type'];if(isset($seen[$type]))continue;$seen[$type]=true;
        if($r['status']==='valid')$valid++;elseif($r['status']==='pending')$pending++;else $update++;
    }
    out(['valid'=>$valid,'pending'=>$pending,'needs_update'=>$update,'missing'=>count(array_diff($required,array_keys($seen)))]);
}
hr_doc_access($me,$empId);
if($a==='list'&&$m==='GET'){
    $st=$pdo->prepare('SELECT id,employee_id,document_type,original_filename,mime_type,byte_size,status,review_note,reviewed_by,reviewed_at,uploaded_by,created_at FROM hr_documents WHERE employee_id=? ORDER BY id DESC');
    $st->execute([$empId]);out($st->fetchAll(PDO::FETCH_ASSOC));
}
if($a==='upload'&&$m==='POST'){
    if(!$self&&!$hr)err('Chi nhan vien hoac HR duoc tai len',403);
    $type=(string)($_POST['document_type']??'');
    if(!in_array($type,$allowedTypes,true))err('Loai giay to khong hop le',422);
    if(!isset($_FILES['file'])||$_FILES['file']['error']!==UPLOAD_ERR_OK)err('Thieu file',422);
    $file=$_FILES['file'];if($file['size']<1||$file['size']>10*1024*1024)err('File toi da 10 MB',422);
    $mime=(new finfo(FILEINFO_MIME_TYPE))->file($file['tmp_name']);
    if(!in_array($mime,['application/pdf','image/jpeg','image/png','image/webp'],true))err('Chi nhan PDF/JPG/PNG/WebP',422);
    if($mime==='application/pdf'){
        $handle=fopen($file['tmp_name'],'rb');$header=fread($handle,5);fclose($handle);
        if($header!=='%PDF-')err('PDF khong dung dinh dang',422);
    }elseif(!@getimagesize($file['tmp_name']))err('Anh khong hop le',422);
    $dir=hr_document_storage();$key=bin2hex(random_bytes(24));
    $target=$dir.DIRECTORY_SEPARATOR.$key;
    if(!move_uploaded_file($file['tmp_name'],$target))err('Luu giay to that bai',500);
    @chmod($target,0600);
    // Do not delete earlier submissions: preserve document history and review trail.
    $name=basename(str_replace('\\','/',(string)$file['name']));$name=(function_exists('mb_substr')?mb_substr($name,0,255):substr($name,0,255));
    try{
        $pdo->beginTransaction();
        $st=$pdo->prepare("INSERT INTO hr_documents(employee_id,document_type,original_filename,storage_key,mime_type,byte_size,status,uploaded_by) VALUES(?,?,?,?,?,?,'pending',?)");
        $st->execute([$empId,$type,$name,$key,$mime,(int)$file['size'],(int)$me['id']]);
        $id=(int)$pdo->lastInsertId();
        hr_record_history($pdo,(int)$me['id'],$empId,'document.uploaded',null,['document_id'=>$id,'type'=>$type]);
        $pdo->commit();out(['id'=>$id,'status'=>'pending']);
    }catch(Throwable $e){if($pdo->inTransaction())$pdo->rollBack();@unlink($target);err('Khong ghi duoc giay to',500);}
}
if($a==='review'&&$m==='POST'){
    if(!$hr)err('Chi HR duoc xac nhan giay to',403);
    if($self)err('Khong tu xac nhan giay to cua minh',403);
    $b=body();$id=(int)($b['id']??0);$status=(string)($b['status']??'');
    if(!in_array($status,['valid','needs_update'],true))err('Trang thai khong hop le',422);
    $note=trim((string)($b['review_note']??''));
    if($status==='needs_update'&&$note==='')err('Can ghi ro ly do bo sung',422);
    if(hr_length($note)>500)err('Ghi chu qua dai',422);
    $pdo->beginTransaction();
    try{
        $st=$pdo->prepare('SELECT id,employee_id,document_type,status FROM hr_documents WHERE id=? AND employee_id=? FOR UPDATE');$st->execute([$id,$empId]);$d=$st->fetch(PDO::FETCH_ASSOC);
        if(!$d)err('Khong tim thay tai lieu',404);
        $latest=$pdo->prepare('SELECT MAX(id) FROM hr_documents WHERE employee_id=? AND document_type=?');
        $latest->execute([$empId,$d['document_type']]);
        if((int)$latest->fetchColumn()!==$id)err('Da co tai lieu moi hon, khong duoc xac nhan ban cu',409);
        $pdo->prepare('UPDATE hr_documents SET status=?,review_note=?,reviewed_by=?,reviewed_at=NOW() WHERE id=?')->execute([$status,$note?:null,(int)$me['id'],$id]);
        hr_record_history($pdo,(int)$me['id'],$empId,'document.reviewed',['status'=>$d['status']],['document_id'=>$id,'status'=>$status]);
        $pdo->commit();out(['id'=>$id,'status'=>$status]);
    }catch(PDOException $e){if($pdo->inTransaction())$pdo->rollBack();err('Khong xac nhan duoc giay to',500);}
}
if($a==='download'&&$m==='GET'){
    $id=(int)($_GET['id']??0);
    $st=$pdo->prepare('SELECT storage_key,original_filename,mime_type FROM hr_documents WHERE id=? AND employee_id=?');$st->execute([$id,$empId]);$d=$st->fetch(PDO::FETCH_ASSOC);
    if(!$d)err('Khong tim thay giay to',404);
    $file=hr_document_storage().DIRECTORY_SEPARATOR.$d['storage_key'];
    if(!is_file($file))err('Khong tim thay file tren may chu',404);
    header('Content-Type: '.$d['mime_type']);header('X-Content-Type-Options: nosniff');
    header('Cache-Control: no-store, private');
    header('Content-Disposition: attachment; filename="document-'.$id.'"');
    header('Content-Length: '.filesize($file));readfile($file);exit;
}
err('Unknown HR documents action',404);
