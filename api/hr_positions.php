<?php
require_once __DIR__.'/db.php';
require_once __DIR__.'/auth_guard.php';
require_once __DIR__.'/positions_logic.php';
require_once __DIR__.'/hr_core.php';
require_once __DIR__.'/hr_recruitment_flow.php';
$a=$_GET['action']??'list';$m=$_SERVER['REQUEST_METHOD'];
$listing=$a==='list' && $m==='GET';
$me=$listing?require_login($pdo):hr_auth($pdo);
$adminList=$listing && $me['role']==='admin';
if(!$adminList && !hr_employee_actor($me))err('Chi phong Nhan su moi co quyen',403);
hr_require_migration($pdo);
if($a==='candidate-options'&&$m==='GET'){
    $department=hr_fk($pdo,'departments',$_GET['department_id']??null);
    if(!$department)err('Can chon phong ban',422);
    $st=$pdo->prepare('SELECT id,title FROM positions WHERE is_active=1 AND (department_id=? OR department_id IS NULL) ORDER BY title');
    $st->execute([$department]);
    out(array_values(array_filter($st->fetchAll(PDO::FETCH_ASSOC),
        fn($position)=>hr_recruitment_position_requires_bod($pdo,(int)$position['id'],$department))));
}
if($a==='manager-options'&&$m==='GET'){
    // Read-only list for Manager HR. No salary, tokens, email or other personal data.
    if(!hr_manager_actor($me))err('Chi Truong phong HR duoc chon quan ly truc tiep',403);
    $employeeId=(int)($_GET['employee_id']??0);
    if($employeeId<=0)err('Thieu ma nhan vien',422);
    $target=hr_employee($pdo,$employeeId);
    if(($target['role_id']??null)===null)err('Ho so chua co vai tro',422);
    $st=$pdo->prepare("SELECT e.id,e.full_name,d.name AS department_name
         FROM employees e JOIN roles r ON r.id=e.role_id
         LEFT JOIN departments d ON d.id=e.department_id
         WHERE LOWER(r.name)='manager' AND e.status=1
           AND e.employment_status IN ('working','probation') AND e.id<>?
         ORDER BY d.name,e.full_name,e.id");
    $st->execute([$employeeId]);out($st->fetchAll(PDO::FETCH_ASSOC));
}
if($listing){
    $department=null;
    if($adminList || isset($_GET['department_id'])){
        $department=hr_fk($pdo,'departments',$_GET['department_id']??null);
        if(!$department)err('Can chon phong ban',422);
    }
    $where=$department===null?'':' WHERE p.department_id=?';
    $st=$pdo->prepare('SELECT p.id,p.title,p.department_id,p.is_active, COUNT(e.id) AS assigned_count
        FROM positions p LEFT JOIN employees e ON e.position_id=p.id'.$where.'
        GROUP BY p.id,p.title,p.department_id,p.is_active ORDER BY p.title,p.id');
    $st->execute($department===null?[]:[$department]);
    out($st->fetchAll(PDO::FETCH_ASSOC));
}
hr_auth($pdo,true);
if(!in_array($a,['create','update','deactivate'],true)||$m!=='POST')err('Unknown HR positions action',404);
$b=body();$id=(int)($b['id']??0);
if($a!=='create'&&$id<=0)err('Thieu id',422);
$statusOnly=$a==='update' && !array_diff(array_keys($b),['id','is_active']) && array_key_exists('is_active',$b);
if($statusOnly && !in_array($b['is_active'],[0,1,true,false],true))err('Trang thai chuc vu khong hop le',422);
if($a!=='deactivate' && !$statusOnly){
    $title=trim((string)($b['title']??''));
    if($title===''||hr_length($title)>100)err('Ten chuc vu khong hop le',422);
    $dep=hr_fk($pdo,'departments',$b['department_id']??null);
}
try{
    $pdo->beginTransaction();
    if($a!=='create'){
        $st=$pdo->prepare('SELECT title,department_id,is_active FROM positions WHERE id=? FOR UPDATE');
        $st->execute([$id]);$old=$st->fetch(PDO::FETCH_ASSOC);
        if(!$old)throw new DomainException('Khong tim thay chuc vu',404);
        if($a==='update' && !$statusOnly && (string)($old['department_id']??'')!==(string)($dep??'')){
            $chk=$pdo->prepare('SELECT COUNT(*) FROM employees WHERE position_id=?');$chk->execute([$id]);
            if((int)$chk->fetchColumn()>0)throw new DomainException('Chuc vu dang duoc gan; khong duoc doi phong',409);
        }
    }
    if($a==='deactivate'||$statusOnly){
        $active=$statusOnly?(int)$b['is_active']:0;
        if((int)$old['is_active']!==$active){
            $pdo->prepare('UPDATE positions SET is_active=? WHERE id=?')->execute([$active,$id]);
            hr_record_history($pdo,(int)$me['id'],null,$active?'position.updated':'position.deactivated',$old,
                ['position_id'=>$id,'is_active'=>$active]);
        }
        $result=['id'=>$id,'is_active'=>$active];
    }elseif($a==='create'){
        $pdo->prepare('INSERT INTO positions(title,department_id,is_active) VALUES(?,?,1)')->execute([$title,$dep]);
        $id=(int)$pdo->lastInsertId();
        hr_record_history($pdo,(int)$me['id'],null,'position.created',null,['position_id'=>$id,'title'=>$title,'department_id'=>$dep]);
        $result=['id'=>$id];
    }else{
        $active=isset($b['is_active'])?(int)(bool)$b['is_active']:(int)$old['is_active'];
        $pdo->prepare('UPDATE positions SET title=?,department_id=?,is_active=? WHERE id=?')->execute([$title,$dep,$active,$id]);
        hr_record_history($pdo,(int)$me['id'],null,'position.updated',$old,['position_id'=>$id,'title'=>$title,'department_id'=>$dep,'is_active'=>$active]);
        $result=['id'=>$id,'updated'=>true];
    }
    $pdo->commit();out($result);
}catch(DomainException $e){
    $pdo->rollBack();
    err($e->getMessage(),$e->getCode());
}catch(PDOException $e){
    if($pdo->inTransaction())$pdo->rollBack();
    err($e->getCode()==='23000'?'Trung ten chuc vu':'Khong cap nhat duoc chuc vu',$e->getCode()==='23000'?409:500);
}
