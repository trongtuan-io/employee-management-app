<?php
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth_guard.php';
require_once __DIR__ . '/positions_logic.php';
require_once __DIR__ . '/hr_proposals_lib.php';
$a=$_GET['action']??'list'; $m=$_SERVER['REQUEST_METHOD'];
$me=hr_auth($pdo);
hr_require_migration($pdo);
hr_require_internal_review_migration($pdo);

function hr_flow_error(string $message, int $code=409): void { throw new DomainException($message,$code); }
function hr_flow_id(array $b): int {
    $id=filter_var($b['id']??null,FILTER_VALIDATE_INT,['options'=>['min_range'=>1]]);
    if(!$id)err('Thieu ID ho so',422);
    return (int)$id;
}
function hr_flow_locked(PDO $pdo,int $id): array {
    $st=$pdo->prepare('SELECT * FROM hr_proposals WHERE id=? FOR UPDATE');$st->execute([$id]);
    $p=$st->fetch(PDO::FETCH_ASSOC);
    if(!$p)hr_flow_error('Khong tim thay ho so',404);
    return $p;
}
function hr_check_recruitment_version(array $p,array $request): void {
    if($p['proposal_type']!=='recruitment')return;
    if(!array_key_exists('version',$request) && (int)$p['workflow_version']<4)return;
    $version=filter_var($request['version']??null,FILTER_VALIDATE_INT,['options'=>['min_range'=>1]]);
    if($version!==(int)$p['version'])hr_flow_error('Ho so da thay doi, vui long tai lai');
}
function hr_flow_transaction(PDO $pdo,callable $fn): void {
    try {
        $pdo->beginTransaction();
        $result=$fn();
        $pdo->commit();out($result);
    } catch (Throwable $e) {
        if($pdo->inTransaction())$pdo->rollBack();
        if($e instanceof DomainException)err($e->getMessage(),(int)$e->getCode());
        if($e instanceof PDOException&&$e->getCode()==='23000')err('Ten dang nhap/email da duoc su dung',409);
        if($e instanceof InvalidArgumentException)err($e->getMessage(),422);
        error_log('HR recruitment failure: '.$e->getMessage());
        err('Khong xu ly duoc ho so HR',500);
    }
}
if($a==='create'&&$m==='POST'){
    $b=body();
    hr_flow_transaction($pdo,fn()=>hr_save_proposal($pdo,$me,(string)($b['proposal_type']??''),$b));
}
if($a==='submit'&&$m==='POST'){
    $b=body();$id=hr_flow_id($b);
    hr_flow_transaction($pdo,function()use($pdo,$me,$id,$b){
        $p=hr_flow_locked($pdo,$id);
        if($p['proposal_type']!=='recruitment'){
            if(!hr_manager_actor($me)||$p['status']!=='draft')hr_flow_error('Khong co quyen gui de xuat nay',403);
            $next='pending'; // Existing non-recruitment proposals still go directly to BOD.
        }else{
            if(!hr_recruitment_can_submit($p,$me))hr_flow_error('Chi duoc gui ho so nhap cua minh',403);
            hr_check_recruitment_version($p,$b);
            $next=hr_recruitment_next($p['status'],'submit');
        }
        $pdo->prepare('UPDATE hr_proposals SET status=?,version=version+1 WHERE id=?')->execute([$next,$id]);
        hr_record_history($pdo,(int)$me['id'],$p['employee_id']===null?null:(int)$p['employee_id'],
            'proposal.submitted',['status'=>$p['status']],['status'=>$next],$id);
        return ['id'=>$id,'status'=>$next,'version'=>(int)$p['version']+1];
    });
}
if($a==='review-internal'&&$m==='POST'){
    if(!hr_manager_actor($me))err('Chi Truong phong HR duoc kiem duyet',403);
    $b=body();$id=hr_flow_id($b);
    $decision=(string)($b['decision']??'');
    if(!in_array($decision,['approve','return'],true))err('Quyet dinh khong hop le',422);
    $note=trim((string)($b['review_note']??''));
    if(hr_length($note)>500||($decision==='return'&&$note===''))err('Tra lai ho so can ly do (toi da 500 ky tu)',422);
    hr_flow_transaction($pdo,function()use($pdo,$me,$id,$decision,$note,$b){
        $p=hr_flow_locked($pdo,$id);
        if($p['proposal_type']!=='recruitment'||(int)$p['workflow_version']<2)
            hr_flow_error('Khong phai ho so tuyen dung moi can duyet noi bo',403);
        hr_check_recruitment_version($p,$b);
        // A draft/import or renamed position must not turn a senior hire into HR-only approval.
        if($decision==='approve' && (int)$p['workflow_version']>=3
            && hr_recruitment_verified_level($pdo,$p)===null)
            hr_flow_error('Chuc vu/cap duyet khong khop; tra ve ho so nhap de sua',409);
        $next=$decision==='return'
            ? hr_recruitment_next((string)$p['status'],'return')
            : hr_recruitment_review_destination($p);
        if($next===null)hr_flow_error('Ho so khong o hang cho Manager HR hoac thieu cap tuyen dung',409);
        // Internal reviewer is deliberately NOT the BOD reviewer (reviewed_by).
        $pdo->prepare('UPDATE hr_proposals SET status=?,hr_reviewed_by=?,hr_reviewed_at=NOW(),hr_review_note=?,version=version+1 WHERE id=?')
            ->execute([$next,(int)$me['id'],$note,$id]);
        hr_record_history($pdo,(int)$me['id'],null,'recruitment.hr.'.$decision,
            ['status'=>$p['status']],['status'=>$next,'review_note'=>$note],$id);
        return ['id'=>$id,'status'=>$next,'version'=>(int)$p['version']+1,
            'next_owner'=>$decision==='return'?'staff_hr':($next==='pending'?'bod':'manager_hr')];
    });
}
if($a==='update-draft'&&$m==='POST'){
    $b=body();$id=hr_flow_id($b);
    hr_lock_hiring($pdo);
    hr_flow_transaction($pdo,function()use($pdo,$me,$id,$b){
        $p=hr_flow_locked($pdo,$id);
        if(!hr_recruitment_can_edit_draft($p,$me))hr_flow_error('Chi duoc sua ho so nhap cua minh',403);
        hr_check_recruitment_version($p,$b);
        [,,$data]=hr_proposal_payload($pdo,'recruitment',$b,(int)$p['workflow_version']);
        if(hr_candidate_reserved($pdo,$data['username'],$data['email'],$id))
            hr_flow_error('Ho so tuyen dung dang giu ma nhan vien hoac email');
        $note=trim((string)($b['reason']??''));
        if($note===''||hr_length($note)>1000)hr_flow_error('Ly do tuyen dung khong hop le',422);
        // Unique index reserves candidate IDs/emails even during HR rework.
        $pdo->prepare('UPDATE hr_proposals SET proposed_data=?,reservation_username=?,reservation_email=?,reason=?,version=version+1 WHERE id=?')
            ->execute([hr_json($data),$data['username'],$data['email'],$note,$id]);
        hr_record_history($pdo,(int)$me['id'],null,'recruitment.draft.edited',null,['proposal_id'=>$id],$id);
        return ['id'=>$id,'status'=>'draft','version'=>(int)$p['version']+1];
    });
}
if($a==='activate'&&$m==='POST'){
    if(!hr_manager_actor($me))err('Chi Truong phong HR duoc hoan tat tuyen dung',403);
    $b=body();$id=hr_flow_id($b);
    hr_lock_hiring($pdo);
    header('Cache-Control: no-store');
    hr_flow_transaction($pdo,function()use($pdo,$me,$id,$b){
        $p=hr_flow_locked($pdo,$id);
        if(!hr_recruitment_can_activate($p))hr_flow_error('Ho so chua duoc duyet dung cap hoac tai khoan da tao',403);
        hr_check_recruitment_version($p,$b);
        $data=json_decode((string)$p['proposed_data'],true);
        if(!is_array($data))hr_flow_error('Ho so ung vien khong hop le',422);
        $username=employee_username($data['username']??null);
        $email=employee_email($data['email']??null);
        $name=employee_name($data['full_name']??null);
        $dep=hr_fk($pdo,'departments',$data['department_id']??null);
        $position=hr_fk($pdo,'positions',$data['position_id']??null);
        if($position!==null){validate_position_for_department($pdo,$position,$dep);hr_require_active_position($pdo,$position);}
        $v=(int)($p['workflow_version']??1);
        if($v>=3){
            if(!$position)hr_flow_error('Ho so thieu chuc vu',422);
            $senior=hr_recruitment_position_requires_bod($pdo,$position,$dep);
            $level=$senior?'senior':'ordinary';
            if(($data['hiring_level']??'')!==$level)hr_flow_error('Chuc vu da doi phan cap; can kiem tra lai ho so',409);
            $expectedRole=$senior?2:3;
        }else $expectedRole=3; // Retain legacy BOD-approved behavior unchanged.
        if((int)($data['role_id']??0)!==$expectedRole)hr_flow_error('Role khong khop cap chuc vu duoc duyet',403);
        $check=$pdo->prepare('SELECT id FROM employees WHERE username=? OR email=? LIMIT 1');
        $check->execute([$username,$email]);
        if($check->fetchColumn())hr_flow_error('Nhan vien da ton tai',409);
        $account=hr_create_employee_account($pdo,['username'=>$username,'email'=>$email,'full_name'=>$name,
            'phone'=>empty($data['phone'])?null:employee_phone($data['phone']),'department_id'=>$dep,
            'position_id'=>$position,'hire_date'=>hr_date($data['hire_date']??null)],
            $expectedRole,$v>=3 && $expectedRole===3);
        $employeeId=$account['employee_id'];
        $pdo->prepare("UPDATE hr_proposals SET employee_id=?,status='activated',applied_at=NOW(),version=version+1,reservation_username=NULL,reservation_email=NULL WHERE id=?")
            ->execute([$employeeId,$id]);
        hr_record_history($pdo,(int)$me['id'],$employeeId,'recruitment.account.activated',null,
            ['proposal_id'=>$id,'username'=>$username],$id);
        $pdo->prepare('UPDATE hr_documents SET employee_id=? WHERE proposal_id=? AND employee_id IS NULL')
            ->execute([$employeeId,$id]);
        $pdo->prepare('UPDATE hr_history SET employee_id=? WHERE proposal_id=? AND employee_id IS NULL AND action=?')
            ->execute([$employeeId,$id,'document.uploaded']);
        return ['id'=>$id,'status'=>'activated']+$account;
    });
}
if($a==='apply'&&$m==='POST'){
    if(!hr_manager_actor($me))err('Chi Truong phong HR duoc ap dung de xuat da duyet',403);
    $id=hr_flow_id(body());
    hr_flow_transaction($pdo,function()use($pdo,$me,$id){
        hr_apply_personnel_change($pdo,hr_flow_locked($pdo,$id),(int)$me['id']);
        return ['id'=>$id,'status'=>'approved','applied'=>true];
    });
}
if($a==='list'&&$m==='GET'){
    $status=(string)($_GET['status']??'');
    if($status!==''&&!in_array($status,['draft','hr_pending','pending','approved','rejected','activated'],true))err('Trang thai khong hop le',422);
    $where=' WHERE 1=1 ';$args=[];
    if($status!==''){$where.=' AND p.status=?';$args[]=$status;}
    if(hr_staff_actor($me)){$where.=" AND p.proposal_type='recruitment' AND p.proposed_by=?";$args[]=(int)$me['id'];}
    $sql='SELECT p.id,p.proposal_type,p.employee_id,p.proposed_by,p.proposed_data,p.reason,p.effective_date,p.status,p.hr_reviewed_by,p.hr_reviewed_at,p.hr_review_note,p.reviewed_by,p.reviewed_at,p.review_note,p.applied_at,p.workflow_version,p.version,p.created_at FROM hr_proposals p'
        .$where.' ORDER BY p.created_at DESC,p.id DESC LIMIT 200';
    $st=$pdo->prepare($sql);$st->execute($args);$items=$st->fetchAll(PDO::FETCH_ASSOC);
    foreach($items as &$row){$row['proposed_data']=json_decode($row['proposed_data'],true);}
    unset($row);out($items);
}
err('Unknown HR proposals action',404);
