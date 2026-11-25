<?php
require_once __DIR__.'/db.php';
require_once __DIR__.'/auth_guard.php';
require_once __DIR__.'/hr_core.php';
require_once __DIR__.'/hr_recruitment_flow.php';

function hr_document_storage(): string {
    $dir=getenv('HR_PRIVATE_STORAGE_DIR');
    if(!$dir||!preg_match('~^(?:[A-Za-z]:[/\\\\]|/)~',$dir))
        err('HR_PRIVATE_STORAGE_DIR chua duoc cau hinh ngoai webroot',503);
    if(!is_dir($dir)&&!@mkdir($dir,0700,true)&&!is_dir($dir))
        err('Khong tao duoc thu muc bao mat HR',503);
    $abs=realpath($dir);
    $docroot=realpath($_SERVER['DOCUMENT_ROOT']??'')?:null;
    $normalize=fn($path)=>strtolower(str_replace('\\','/',$path)).'/';
    if(!$abs || ($docroot && str_starts_with($normalize($abs),$normalize($docroot))))
        err('Thu muc HR phai nam ngoai document root',503);
    return $abs;
}

$me=require_login($pdo);
hr_require_migration($pdo);
$a=$_GET['action']??'list';
$m=$_SERVER['REQUEST_METHOD'];
$types=['portrait','cccd_front','cccd_back','insurance','degree','certificate','contract','other'];
$candidateTypes=['portrait','cccd_front','cccd_back','degree','certificate','other'];
$proposalId=filter_var($_GET['proposal_id']??$_POST['proposal_id']??null,FILTER_VALIDATE_INT,['options'=>['min_range'=>1]]);
$empId=null;
$hr=hr_employee_actor($me);
$bod=false;
$self=false;
if($proposalId){
    $st=$pdo->prepare("SELECT * FROM hr_proposals WHERE id=? AND proposal_type='recruitment'");
    $st->execute([$proposalId]);
    $proposal=$st->fetch(PDO::FETCH_ASSOC);
    if(!$proposal)err('Khong tim thay ho so ung vien',404);
    $bod=review_bod_actor($pdo,$me);
    if($bod){
        if($proposal['status']!=='pending' || empty($proposal['hr_reviewed_by'])
            || ((int)$proposal['workflow_version']>=3 && hr_recruitment_level($proposal)!=='senior')
            || $m!=='GET' || !in_array($a,['list','download'],true))
            err('Khong co quyen xem giay to ung vien nay',403);
    }elseif(!$hr || (!hr_manager_actor($me) && (int)$proposal['proposed_by']!==(int)$me['id'])){
        err('Khong co quyen xem giay to ung vien nay',403);
    }
    $scope='proposal_id';
    $ownerId=$proposalId;
}else{
    if(isset($_GET['proposal_id'])||isset($_POST['proposal_id']))err('proposal_id khong hop le',422);
    $empId=filter_var($_GET['employee_id']??$_POST['employee_id']??$me['id'],FILTER_VALIDATE_INT,['options'=>['min_range'=>1]]);
    if(!$empId)err('employee_id khong hop le',422);
    hr_doc_access($me,$empId);
    $emp=hr_employee($pdo,$empId);
    $self=(int)$me['id']===$empId;
    $scope='employee_id';
    $ownerId=$empId;
}

$latest="NOT EXISTS (SELECT 1 FROM hr_documents newer
    WHERE newer.$scope=d.$scope AND newer.document_type=d.document_type AND newer.id>d.id)";
$bodFilter=$bod?" AND d.document_type IN ('portrait','degree','certificate') AND $latest":'';

if($a==='summary'&&$m==='GET'&&!$proposalId){
    $st=$pdo->prepare('SELECT document_type FROM hr_required_employee_documents WHERE employee_id=?');
    $st->execute([$empId]);
    $required=$st->fetchAll(PDO::FETCH_COLUMN);
    $st=$pdo->prepare("SELECT d.document_type,d.status FROM hr_documents d WHERE d.employee_id=? AND $latest");
    $st->execute([$empId]);
    $status=$st->fetchAll(PDO::FETCH_KEY_PAIR);
    $counts=['missing'=>0,'pending'=>0,'valid'=>0,'needs_update'=>0];
    $checklist=[];
    foreach($types as $type){
        $state=$status[$type]??'missing';
        $isRequired=in_array($type,$required,true);
        if($isRequired)$counts[$state]++;
        $checklist[]=['document_type'=>$type,'status'=>$state,'required'=>$isRequired];
    }
    out($counts+['required_count'=>count($required),'due_at'=>$emp['hr_documents_due_at'],
        'checklist'=>$checklist]);
}
if($a==='list'&&$m==='GET'){
    $fields=$bod?'d.id,d.document_type,d.mime_type,d.byte_size':
        'd.id,d.employee_id,d.proposal_id,d.document_type,d.original_filename,d.mime_type,d.byte_size,
        d.status,d.review_note,d.reviewed_by,d.reviewed_at,d.uploaded_by,d.created_at';
    $st=$pdo->prepare("SELECT $fields, ($latest) AS is_latest FROM hr_documents d
        WHERE d.$scope=?$bodFilter ORDER BY d.id DESC");
    $st->execute([$ownerId]);
    out($st->fetchAll(PDO::FETCH_ASSOC));
}
if($a==='upload'&&$m==='POST'){
    $type=(string)($_POST['document_type']??'');
    if(!in_array($type,$proposalId?$candidateTypes:$types,true))err('Loai giay to khong hop le',422);
    if(!isset($_FILES['file'])||$_FILES['file']['error']!==UPLOAD_ERR_OK)err('Thieu file',422);
    $file=$_FILES['file'];
    if($file['size']<1||$file['size']>10*1024*1024)err('File toi da 10 MB',422);
    $mime=(new finfo(FILEINFO_MIME_TYPE))->file($file['tmp_name']);
    if(!in_array($mime,['application/pdf','image/jpeg','image/png','image/webp'],true)
        || ($type==='portrait' && $mime==='application/pdf'))err('Dinh dang file khong hop le',422);
    if($mime==='application/pdf'){
        if(file_get_contents($file['tmp_name'],false,null,0,5)!=='%PDF-')err('PDF khong dung dinh dang',422);
    }elseif(!@getimagesize($file['tmp_name']))err('Anh khong hop le',422);
    $dir=hr_document_storage();
    $key=bin2hex(random_bytes(24));
    $target=$dir.DIRECTORY_SEPARATOR.$key;
    $name=basename(str_replace('\\','/',(string)$file['name']));
    $name=function_exists('mb_substr')?mb_substr($name,0,255):substr($name,0,255);
    $moved=false;
    try{
        $pdo->beginTransaction();
        if($proposalId){
            $st=$pdo->prepare('SELECT * FROM hr_proposals WHERE id=? FOR UPDATE');
            $st->execute([$proposalId]);
            $locked=$st->fetch(PDO::FETCH_ASSOC);
            if(!$locked || !hr_recruitment_can_edit_draft($locked,$me))
                throw new DomainException('Chi duoc dinh kem giay to khi ho so o ban nhap',409);
        }else{
            $st=$pdo->prepare('SELECT id FROM employees WHERE id=? FOR UPDATE');
            $st->execute([$empId]);
        }
        if(!move_uploaded_file($file['tmp_name'],$target))
            throw new RuntimeException('Khong luu duoc file');
        $moved=true;
        @chmod($target,0600);
        $st=$pdo->prepare("INSERT INTO hr_documents(employee_id,proposal_id,document_type,original_filename,storage_key,mime_type,byte_size,status,uploaded_by)
            VALUES(?,?,?,?,?,?,?,'pending',?)");
        $st->execute([$empId,$proposalId?:null,$type,$name,$key,$mime,(int)$file['size'],(int)$me['id']]);
        $id=(int)$pdo->lastInsertId();
        if($proposalId)$pdo->prepare('UPDATE hr_proposals SET version=version+1 WHERE id=?')->execute([$proposalId]);
        hr_record_history($pdo,(int)$me['id'],$empId,'document.uploaded',null,['document_id'=>$id,'type'=>$type],$proposalId?:null);
        $pdo->commit();
        out(['id'=>$id,'status'=>'pending']);
    }catch(Throwable $e){
        if($pdo->inTransaction())$pdo->rollBack();
        if($moved)@unlink($target);
        if($e instanceof DomainException)err($e->getMessage(),$e->getCode());
        err('Khong ghi duoc giay to',500);
    }
}
if($a==='review'&&$m==='POST'&&!$proposalId){
    if(!$hr||$self)err('Chi HR duoc xac nhan giay to cua nhan vien khac',403);
    $b=body();$id=(int)($b['id']??0);$status=(string)($b['status']??'');
    if(!in_array($status,['valid','needs_update'],true))err('Trang thai khong hop le',422);
    $note=trim((string)($b['review_note']??''));
    if($status==='needs_update'&&$note==='')err('Can ghi ro ly do bo sung',422);
    if(hr_length($note)>500)err('Ghi chu qua dai',422);
    try{
        $pdo->beginTransaction();
        $st=$pdo->prepare('SELECT id FROM employees WHERE id=? FOR UPDATE');
        $st->execute([$empId]);
        $st=$pdo->prepare('SELECT id,proposal_id,document_type,status,review_note FROM hr_documents WHERE id=? AND employee_id=? FOR UPDATE');
        $st->execute([$id,$empId]);
        $d=$st->fetch(PDO::FETCH_ASSOC);
        if(!$d)throw new DomainException('Khong tim thay tai lieu',404);
        $newest=$pdo->prepare('SELECT MAX(id) FROM hr_documents WHERE employee_id=? AND document_type=?');
        $newest->execute([$empId,$d['document_type']]);
        if((int)$newest->fetchColumn()!==$id)throw new DomainException('Da co tai lieu moi hon, khong duoc xac nhan ban cu',409);
        $pdo->prepare('UPDATE hr_documents SET status=?,review_note=?,reviewed_by=?,reviewed_at=NOW() WHERE id=?')
            ->execute([$status,$note?:null,(int)$me['id'],$id]);
        hr_record_history($pdo,(int)$me['id'],$empId,'document.reviewed',
            ['status'=>$d['status'],'review_note'=>$d['review_note']],
            ['document_id'=>$id,'status'=>$status,'review_note'=>$note?:null],$d['proposal_id']===null?null:(int)$d['proposal_id']);
        $pdo->commit();
        out(['id'=>$id,'status'=>$status]);
    }catch(Throwable $e){
        if($pdo->inTransaction())$pdo->rollBack();
        if($e instanceof DomainException)err($e->getMessage(),$e->getCode());
        err('Khong xac nhan duoc giay to',500);
    }
}
if($a==='download'&&$m==='GET'){
    $id=(int)($_GET['id']??0);
    $st=$pdo->prepare("SELECT d.storage_key,d.mime_type FROM hr_documents d WHERE d.id=? AND d.$scope=?$bodFilter");
    $st->execute([$id,$ownerId]);
    $d=$st->fetch(PDO::FETCH_ASSOC);
    if(!$d)err('Khong tim thay giay to',404);
    $file=hr_document_storage().DIRECTORY_SEPARATOR.$d['storage_key'];
    if(!is_file($file))err('Khong tim thay file tren may chu',404);
    header('Content-Type: '.$d['mime_type']);
    header('X-Content-Type-Options: nosniff');
    header('Cache-Control: no-store, private');
    header('Content-Disposition: attachment; filename="document-'.$id.'"');
    header('Content-Length: '.filesize($file));
    readfile($file);
    exit;
}
err('Unknown HR documents action',404);
