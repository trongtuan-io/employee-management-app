<?php
require_once __DIR__ . '/hr_core.php';
require_once __DIR__ . '/hr_recruitment_flow.php';

/** Migration 013 is required for HR internal review; never change live database at runtime. */
function hr_require_internal_review_migration(PDO $pdo): void {
    try { $pdo->query('SELECT workflow_version,hr_reviewed_by FROM hr_proposals LIMIT 0'); }
    catch (PDOException $e) { err('Can chay migration 013 tren database test truoc khi su dung HR',503); }
}
/** An inactive position remains attached to existing employees but cannot be newly proposed. */
function hr_require_active_position(PDO $pdo, ?int $positionId): void {
    if ($positionId === null) return;
    $st=$pdo->prepare('SELECT is_active FROM positions WHERE id=? LIMIT 1');
    $st->execute([$positionId]);
    if ((int)$st->fetchColumn()!==1) err('Chuc vu da ngung su dung',422);
}
/** Prepare but NEVER apply a BOD-governed employee change. No activation or employee update here. */
function hr_proposal_payload(PDO $pdo, string $type, array $b): array {
    $allowed=['recruitment','base_salary_change','promotion','transfer','termination'];
    if (!in_array($type,$allowed,true)) err('Loai de xuat khong hop le',422);
    if ($type === 'recruitment') {
        foreach (['username','full_name','email'] as $k) if(empty($b[$k])) err('Thieu '.$k,422);
        $username=employee_username($b['username']); $email=employee_email($b['email']);
        $st=$pdo->prepare('SELECT id FROM employees WHERE username=? OR email=? LIMIT 1');
        $st->execute([$username,$email]);
        if($st->fetchColumn()) err('Nhan vien da ton tai; dung cap nhat thong tin ho so',409);
        $department=hr_fk($pdo,'departments',$b['department_id']??null);
        $position=hr_fk($pdo,'positions',$b['position_id']??null);
        if ($position !== null) { $position=validate_position_for_department($pdo,$position,$department); hr_require_active_position($pdo,$position); }
        if(!$position)err('Bat buoc chon chuc vu de phan cap duyet tuyen dung',422);
        $senior=hr_recruitment_position_requires_bod($pdo,$position);
        $data=[
            'hiring_level'=>$senior?'senior':'ordinary',
            'username'=>$username,'full_name'=>employee_name($b['full_name']),'email'=>$email,
            'phone'=>empty($b['phone'])?null:employee_phone($b['phone']),
            'department_id'=>$department,'position_id'=>$position,
            'hire_date'=>hr_date($b['hire_date']??null),
            'role_id'=>$senior?2:3, // Determined by verified position, not input.
        ];
        return [null,null,$data];
    }
    $id=filter_var($b['employee_id']??null,FILTER_VALIDATE_INT,['options'=>['min_range'=>1]]);
    if(!$id)err('Thieu employee_id',422);
    $emp=hr_employee($pdo,(int)$id);
    if((int)$emp['role_id']===1)err('Khong lap de xuat nhan su tren tai khoan Admin ky thuat',403);
    if($type === 'base_salary_change') {
        $new=$b['base_salary']??null;
        if(!is_scalar($new)||!is_numeric($new)||!is_finite((float)$new)||(float)$new<0||(float)$new>9999999999)err('Luong khong hop le',422);
        $before=['base_salary'=>$emp['base_salary']];$data=['base_salary'=>(float)$new];
    } elseif($type==='promotion') {
        $pid=hr_fk($pdo,'positions',$b['position_id']??null);
        if(!$pid)err('Thieu chuc vu moi',422);
        $dep=isset($emp['department_id'])?(int)$emp['department_id']:null;
        validate_position_for_department($pdo,$pid,$dep); hr_require_active_position($pdo,$pid);
        $before=['position_id'=>$emp['position_id'],'role_id'=>$emp['role_id']];
        $data=['position_id'=>$pid];
        if(isset($b['role_id'])) {
            if(!in_array((int)$b['role_id'],[2,3],true))err('Khong duoc de xuat role Admin',403);
            $data['role_id']=(int)$b['role_id'];
        }
    } elseif($type==='transfer') {
        $dep=hr_fk($pdo,'departments',$b['department_id']??null);
        if(!$dep)err('Thieu phong ban moi',422);
        $before=['department_id'=>$emp['department_id'],'position_id'=>$emp['position_id']];
        // Explicitly clear incompatible old position; BOD later sets a new position.
        $data=['department_id'=>$dep,'position_id'=>null];
        if(isset($b['position_id'])&&$b['position_id']!==null){$p=hr_fk($pdo,'positions',$b['position_id']);$data['position_id']=validate_position_for_department($pdo,$p,$dep); hr_require_active_position($pdo,$p);}
    } else {
        $before=['employment_status'=>$emp['employment_status']??'working','status'=>$emp['status']];
        $data=['employment_status'=>'left','disable_login'=>true];
    }
    return [(int)$id,$before,$data];
}
function hr_save_proposal(PDO $pdo, array $me, string $type, array $b, ?int $importBatchId = null, bool $forceDraft = false): array {
    if (!hr_employee_actor($me)) err('Chi HR duoc lap de xuat',403);
    if(hr_staff_actor($me)&&$type!=='recruitment')err('Staff HR chi duoc soan nhap ho so tuyen dung',403);
    [$employee,$before,$data]=hr_proposal_payload($pdo,$type,$b);
    hr_require_internal_review_migration($pdo);
    $reason=trim((string)($b['reason']??''));
    if($reason===''||hr_length($reason)>1000)err('Can ly do 1-1000 ky tu',422);
    $date=hr_date($b['effective_date']??null);
    // Do not trust status, approver or result provided by the client.
    $status=$forceDraft||hr_staff_actor($me)?'draft':($type==='recruitment'?'hr_pending':'pending');
    // Avoid duplicate pending recruitment from both UI and imports.
    if($type==='recruitment'){
        $st=$pdo->query("SELECT id, proposed_data FROM hr_proposals WHERE proposal_type='recruitment' AND status IN ('draft','hr_pending','pending','approved')");
        foreach($st->fetchAll(PDO::FETCH_ASSOC) as $p){
            $candidate=json_decode($p['proposed_data'],true)?:[];
            if(($candidate['username']??null)===$data['username']||strcasecmp((string)($candidate['email']??''),$data['email'])===0)
                err('Ho so tuyen dung dang cho duyet hoac soan nhap; khong tao trung',409);
        }
    }
    $st=$pdo->prepare('INSERT INTO hr_proposals(proposal_type,employee_id,proposed_by,before_data,proposed_data,reservation_username,reservation_email,reason,effective_date,status,import_batch_id,workflow_version) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)');
    $st->execute([$type,$employee,(int)$me['id'],$before===null?null:hr_json($before),hr_json($data),
        $type==='recruitment'?$data['username']:null,$type==='recruitment'?$data['email']:null,
        $reason,$date,$status,$importBatchId,$type==='recruitment'?3:1]);
    $id=(int)$pdo->lastInsertId();
    hr_record_history($pdo,(int)$me['id'],$employee,'proposal.created.'.$type,null,['proposal_id'=>$id,'status'=>$status],$id);
    return ['id'=>$id,'status'=>$status,'proposal_type'=>$type,'employee_id'=>$employee];
}
