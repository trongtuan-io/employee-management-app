<?php
require_once __DIR__ . '/hr_core.php';
require_once __DIR__ . '/hr_recruitment_flow.php';
require_once __DIR__ . '/positions_logic.php';

/** Serialize candidate reservations and account creation across the two HR entry points. */
function hr_lock_hiring(PDO $pdo): void {
    static $locked = false;
    if ($locked) return;
    if ((int)$pdo->query("SELECT GET_LOCK(CONCAT(DATABASE(),':hr-hiring'),10)")->fetchColumn()!==1)
        err('He thong dang xu ly tuyen dung, vui long thu lai',409);
    $locked = true; // Released when the non-persistent request connection closes.
}

function hr_candidate_reserved(PDO $pdo, string $username, string $email, int $except = 0): bool {
    $st=$pdo->prepare("SELECT id FROM hr_proposals
        WHERE proposal_type='recruitment' AND status IN ('draft','hr_pending','pending','approved') AND id<>?
        AND (COALESCE(reservation_username,JSON_UNQUOTE(JSON_EXTRACT(proposed_data,'$.username'))) COLLATE utf8mb4_unicode_ci=?
          OR COALESCE(reservation_email,JSON_UNQUOTE(JSON_EXTRACT(proposed_data,'$.email'))) COLLATE utf8mb4_unicode_ci=?)
        LIMIT 1 FOR UPDATE");
    $st->execute([$except,$username,$email]);
    return (bool)$st->fetchColumn();
}

function hr_create_employee_account(PDO $pdo, array $data, int $roleId, bool $ordinary): array {
    $password=rtrim(strtr(base64_encode(random_bytes(18)),'+/','-_'),'=');
    $deadline=$ordinary?'DATE_ADD(NOW(), INTERVAL 15 DAY)':'NULL';
    $pdo->prepare("INSERT INTO employees(username,password_hash,full_name,phone,email,base_salary,status,
        role_id,department_id,position_id,hire_date,employment_status,created_at,hr_documents_due_at)
        VALUES(?,?,?,?,?,0,1,?,?,?,?,'working',NOW(),$deadline)")
        ->execute([$data['username'],password_hash($password,PASSWORD_BCRYPT),$data['full_name'],
            $data['phone'],$data['email'],$roleId,$data['department_id'],$data['position_id'],$data['hire_date']]);
    return ['employee_id'=>(int)$pdo->lastInsertId(),'username'=>$data['username'],'temporary_password'=>$password];
}

/** Apply an approved HR change inside the caller's transaction, with the proposal locked. */
function hr_apply_personnel_change(PDO $pdo, array $proposal, int $actorId): void {
    $type = $proposal['proposal_type'];
    $fields = [
        'base_salary_change' => ['base_salary'],
        'promotion' => ['position_id', 'role_id'],
        'transfer' => ['department_id', 'position_id'],
        'termination' => ['employment_status', 'status'],
    ][$type] ?? null;
    if ($fields === null || $proposal['status'] !== 'approved' || !$proposal['reviewed_by']
        || !$proposal['employee_id'] || $proposal['applied_at']
        || ($proposal['effective_date'] && $proposal['effective_date'] > date('Y-m-d'))) {
        throw new DomainException('De xuat chua den han hoac khong the ap dung', 409);
    }
    $employeeId = (int)$proposal['employee_id'];
    $stmt = $pdo->prepare('SELECT * FROM employees WHERE id=? FOR UPDATE');
    $stmt->execute([$employeeId]);
    $employee = $stmt->fetch(PDO::FETCH_ASSOC);
    if (!$employee || (int)$employee['role_id'] === 1 || $employee['employment_status'] === 'left') {
        throw new DomainException('Nhan vien khong con phu hop voi de xuat', 409);
    }
    $before = json_decode($proposal['before_data'] ?? '', true);
    foreach ($fields as $field) {
        if (!is_array($before) || !array_key_exists($field, $before)
            || (string)$before[$field] !== (string)$employee[$field]) {
            throw new DomainException('Du lieu nhan vien da thay doi, can lap de xuat moi', 409);
        }
    }
    if ($type === 'termination') {
        $data = ['employment_status'=>'left', 'status'=>0,
            'termination_effective_date'=>$proposal['effective_date'] ?? date('Y-m-d')];
    } else {
        $data = json_decode($proposal['proposed_data'], true);
        $required = $fields[0];
        if (!is_array($data) || !isset($data[$required])) {
            throw new DomainException('De xuat thieu gia tri thay doi', 422);
        }
        $data = array_intersect_key($data, array_flip($fields));
        if (isset($data['role_id']) && !in_array((int)$data['role_id'], [2, 3], true)) {
            throw new DomainException('Vai tro de xuat khong hop le', 422);
        }
        if ($type === 'transfer') {
            if (!hr_fk($pdo, 'departments', $data['department_id'])) {
                throw new DomainException('De xuat thieu phong ban moi', 422);
            }
            $data['position_id'] = $data['position_id'] ?? null;
        }
        if (isset($data['position_id'])) {
            $department = $data['department_id'] ?? $employee['department_id'];
            validate_position_for_department($pdo, $data['position_id'], $department);
            hr_require_active_position($pdo, $data['position_id']);
        }
    }
    $assignments = implode('=?, ', array_keys($data)) . '=?';
    $pdo->prepare("UPDATE employees SET $assignments WHERE id=?")
        ->execute([...array_values($data), $employeeId]);
    if ($type === 'termination') {
        $pdo->prepare('DELETE FROM api_tokens WHERE employee_id=?')->execute([$employeeId]);
    }
    $pdo->prepare('UPDATE hr_proposals SET applied_at=NOW(), version=version+1 WHERE id=?')
        ->execute([(int)$proposal['id']]);
    hr_record_history($pdo, $actorId, $employeeId, 'proposal.applied',
        array_intersect_key($employee, $data), $data, (int)$proposal['id']);
}

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
function hr_proposal_payload(PDO $pdo, string $type, array $b, int $workflow = 4): array {
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
        if(!$position || !$department)err('Bat buoc chon phong ban va chuc vu',422);
        $senior=hr_recruitment_position_requires_bod($pdo,$position,$department);
        if(!$senior && $workflow>=4)err('Ho so ung vien chi dung cho nhan su cap cao. Hay import nhan vien Staff.',422);
        $data=[
            'hiring_level'=>$senior?'senior':'ordinary',
            'username'=>$username,'full_name'=>employee_name($b['full_name']),'email'=>$email,
            'phone'=>empty($b['phone'])?null:employee_phone($b['phone']),
            'department_id'=>$department,'position_id'=>$position,
            'hire_date'=>hr_date($b['hire_date']??null),
            'role_id'=>$senior?2:3,
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
function hr_save_proposal(PDO $pdo, array $me, string $type, array $b): array {
    if (!hr_employee_actor($me)) err('Chi HR duoc lap de xuat',403);
    if(hr_staff_actor($me)&&$type!=='recruitment')err('Staff HR chi duoc soan nhap ho so tuyen dung',403);
    if($type==='recruitment')hr_lock_hiring($pdo);
    [$employee,$before,$data]=hr_proposal_payload($pdo,$type,$b);
    hr_require_internal_review_migration($pdo);
    $reason=trim((string)($b['reason']??''));
    if($reason===''||hr_length($reason)>1000)err('Can ly do 1-1000 ky tu',422);
    $date=hr_date($b['effective_date']??null);
    // Do not trust status, approver or result provided by the client.
    $status=$type==='recruitment'?'draft':'pending';
    if($type==='recruitment'&&hr_candidate_reserved($pdo,$data['username'],$data['email']))
        err('Ho so tuyen dung dang cho duyet hoac soan nhap; khong tao trung',409);
    $st=$pdo->prepare('INSERT INTO hr_proposals(proposal_type,employee_id,proposed_by,before_data,proposed_data,reservation_username,reservation_email,reason,effective_date,status,workflow_version) VALUES(?,?,?,?,?,?,?,?,?,?,?)');
    $st->execute([$type,$employee,(int)$me['id'],$before===null?null:hr_json($before),hr_json($data),
        $type==='recruitment'?$data['username']:null,$type==='recruitment'?$data['email']:null,
        $reason,$date,$status,$type==='recruitment'?4:1]);
    $id=(int)$pdo->lastInsertId();
    hr_record_history($pdo,(int)$me['id'],$employee,'proposal.created.'.$type,null,['proposal_id'=>$id,'status'=>$status],$id);
    return ['id'=>$id,'status'=>$status,'proposal_type'=>$type,'employee_id'=>$employee,'version'=>1];
}
