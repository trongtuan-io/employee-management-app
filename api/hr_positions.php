<?php
require_once __DIR__.'/db.php';
require_once __DIR__.'/auth_guard.php';
require_once __DIR__.'/positions_logic.php';
require_once __DIR__.'/hr_core.php';
$me=hr_auth($pdo);
hr_require_migration($pdo);
$a=$_GET['action']??'list';$m=$_SERVER['REQUEST_METHOD'];
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
if($a==='list'&&$m==='GET'){
    $st=$pdo->query('SELECT p.id,p.title,p.department_id,p.is_active, COUNT(e.id) AS assigned_count FROM positions p LEFT JOIN employees e ON e.position_id=p.id GROUP BY p.id,p.title,p.department_id,p.is_active ORDER BY p.title,p.id');
    out($st->fetchAll(PDO::FETCH_ASSOC));
}
hr_auth($pdo,true);
if(!in_array($a,['create','update','deactivate'],true)||$m!=='POST')err('Unknown HR positions action',404);
$b=body();$id=(int)($b['id']??0);
if($a!=='create'&&$id<=0)err('Thieu id',422);
if($a!=='deactivate'){
    $title=trim((string)($b['title']??''));
    if($title===''||hr_length($title)>100)err('Ten chuc vu khong hop le',422);
    $dep=hr_fk($pdo,'departments',$b['department_id']??null);
}
if($a!=='create'){
    $st=$pdo->prepare('SELECT title,department_id,is_active FROM positions WHERE id=?');
    $st->execute([$id]);$old=$st->fetch(PDO::FETCH_ASSOC);
    if(!$old)err('Khong tim thay chuc vu',404);
    if($a==='update'&&(string)($old['department_id']??'')!==(string)($dep??'')){
        $chk=$pdo->prepare('SELECT COUNT(*) FROM employees WHERE position_id=?');$chk->execute([$id]);
        if((int)$chk->fetchColumn()>0)err('Chuc vu dang duoc gan; khong duoc doi phong',409);
    }
}
try{
    $pdo->beginTransaction();
    if($a==='deactivate'){
        $pdo->prepare('UPDATE positions SET is_active=0 WHERE id=?')->execute([$id]);
        hr_record_history($pdo,(int)$me['id'],null,'position.deactivated',$old,['position_id'=>$id,'is_active'=>0]);
        $result=['id'=>$id,'is_active'=>0];
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
}catch(PDOException $e){
    if($pdo->inTransaction())$pdo->rollBack();
    err($e->getCode()==='23000'?'Trung ten chuc vu':'Khong cap nhat duoc chuc vu',$e->getCode()==='23000'?409:500);
}
