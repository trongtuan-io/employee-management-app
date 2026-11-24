<?php
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/auth_guard.php';
require_once __DIR__ . '/hr_proposals_lib.php';

$action = $_GET['action'] ?? 'list';
$method = $_SERVER['REQUEST_METHOD'];

$me = require_login($pdo);
if (!review_bod_actor($pdo, $me)) err('Chi Giam doc/Pho Giam doc thuoc BOD duoc su dung API nay', 403);
if ($action === 'access' && $method === 'GET') out(['can_review'=>true]);
hr_require_migration($pdo);
hr_require_internal_review_migration($pdo);

if ($action === 'list' && $method === 'GET') {
    $status = trim((string)($_GET['status'] ?? ''));
    $type = trim((string)($_GET['type'] ?? ''));
    $allowedStatuses = ['pending','approved','rejected','activated'];
    if ($status !== '' && !in_array($status, $allowedStatuses, true)) err('Status khong hop le', 422);
    $normalized = [
        'new_hire'=>'recruitment', 'salary_change'=>'base_salary_change',
        'department_transfer'=>'transfer'
    ];
    if ($type !== '') $type = $normalized[$type] ?? $type;
    if ($type !== '' && !in_array($type,
        ['recruitment','base_salary_change','promotion','transfer','termination'],true)) err('Type khong hop le',422);

    // HR drafts, HR-only ordinary hires and internal reviews are invisible to BOD.
    $where = " WHERE (p.reviewed_by IS NOT NULL OR (p.status='pending'
        AND (p.proposal_type<>'recruitment' OR p.workflow_version<2
            OR (p.hr_reviewed_by IS NOT NULL AND (p.workflow_version<3
                OR JSON_UNQUOTE(JSON_EXTRACT(p.proposed_data,'$.hiring_level'))='senior'))))) ";
    $args = [];
    if ($status !== '') { $where .= ' AND p.status=?'; $args[]=$status; }
    if ($type !== '') { $where .= ' AND p.proposal_type=?'; $args[]=$type; }
    $sql = 'SELECT p.*,e.full_name AS employee_name,hr.full_name AS creator_name,
        reviewer.full_name AS approver_name
        FROM hr_proposals p
        LEFT JOIN employees e ON e.id=p.employee_id
        LEFT JOIN employees hr ON hr.id=p.proposed_by
        LEFT JOIN employees reviewer ON reviewer.id=p.reviewed_by '
        . $where . " ORDER BY (p.status='pending') DESC,p.id DESC LIMIT 100";
    $stmt = $pdo->prepare($sql);
    $stmt->execute($args);
    $depts = $pdo->query('SELECT id,name FROM departments')->fetchAll(PDO::FETCH_KEY_PAIR);
    $positions = $pdo->query('SELECT id,title FROM positions')->fetchAll(PDO::FETCH_KEY_PAIR);
    $list = [];
    foreach ($stmt->fetchAll(PDO::FETCH_ASSOC) as $p) {
        $rawType = (string)$p['proposal_type'];
        $data = json_decode((string)($p['proposed_data'] ?? ''),true) ?: [];
        $before = json_decode((string)($p['before_data'] ?? ''),true) ?: [];
        $typeAlias = ['recruitment'=>'new_hire','base_salary_change'=>'salary_change',
            'transfer'=>'department_transfer'][$rawType] ?? $rawType;
        $d = $data['department_id'] ?? null;
        $position = $data['position_id'] ?? null;
        $oldValue = null;
        $newValue = null;
        if ($rawType === 'recruitment') {
            $newValue = isset($data['username']) ? ('Tao tai khoan: '.$data['username']) : null;
        } elseif ($rawType === 'base_salary_change') {
            $oldValue = isset($before['base_salary']) ? (string)$before['base_salary'] : null;
            $newValue = isset($data['base_salary']) ? (string)$data['base_salary'] : null;
        } elseif ($rawType === 'transfer') {
            $oldValue = isset($depts[$before['department_id'] ?? -1]) ? $depts[$before['department_id']] : null;
            $newValue = isset($depts[$d ?? -1]) ? $depts[$d] : null;
        } elseif ($rawType === 'promotion') {
            $oldValue = isset($positions[$before['position_id'] ?? -1]) ? $positions[$before['position_id']] : null;
            $newValue = isset($positions[$position ?? -1]) ? $positions[$position] : null;
        } elseif ($rawType === 'termination') {
            $newValue = 'De xuat nghi viec';
        }
        $list[] = [
            'id'=>(int)$p['id'], 'version'=>(int)$p['version'],
            'type'=>$typeAlias, 'proposal_type'=>$rawType,
            'employee_id'=>$p['employee_id']===null?null:(int)$p['employee_id'],
            'employee_name'=>$data['full_name'] ?? $p['employee_name'] ?? null,
            'creator_name'=>$p['creator_name'], 'approver_name'=>$p['approver_name'],
            'created_by'=>(int)$p['proposed_by'], 'approved_by'=>$p['reviewed_by'],
            'dept_name'=>$depts[$d] ?? null,
            'pos_title'=>$positions[$position] ?? null,
            'old_value'=>$oldValue, 'new_value'=>$newValue,
            'reason'=>$p['reason'], 'status'=>$p['status'],
            'review_note'=>$p['review_note'], 'applied_at'=>$p['applied_at'],
            'effective_date'=>$p['effective_date'],
            'created_at'=>$p['created_at'], 'processed_at'=>$p['reviewed_at']
        ];
    }
    out($list);
}

if ($action === 'approve' && $method === 'POST') {
    $body = body();
    $id = filter_var($body['id'] ?? null, FILTER_VALIDATE_INT, ['options'=>['min_range'=>1]]);
    $decision = (string)($body['status'] ?? '');
    if (!$id || !in_array($decision,['approved','rejected'],true)) err('ID/status khong hop le',422);
    $note = trim((string)($body['review_note'] ?? ''));
    if (hr_length($note)>1500) err('Ghi chu qua dai',422);
    $hasVersion = array_key_exists('version', $body);
    $clientVersion = $hasVersion ? filter_var($body['version'],FILTER_VALIDATE_INT,
        ['options'=>['min_range'=>0]]) : null;
    if ($hasVersion && $clientVersion === false) err('Version khong hop le',422);
    try {
        $pdo->beginTransaction();
        $stmt = $pdo->prepare('SELECT * FROM hr_proposals WHERE id=? FOR UPDATE');
        $stmt->execute([(int)$id]);
        $p = $stmt->fetch(PDO::FETCH_ASSOC);
        if (!$p) throw new DomainException('Khong tim thay ho so',404);
        if ($p['status'] !== 'pending' || $p['reviewed_by'] !== null) throw new DomainException('Ho so khong con cho BOD duyet',409);
        if ($hasVersion && (int)$clientVersion !== (int)$p['version'])
            throw new DomainException('Ho so da thay doi, vui long tai lai',409);
        if ((int)$p['proposed_by'] === (int)$me['id'])
            throw new DomainException('Khong duoc tu duyet de xuat cua minh',403);
        if ($p['proposal_type'] === 'recruitment') {
            $workflow = (int)$p['workflow_version'];
            if ($workflow >= 4 && (!$hasVersion || $note === ''))
                throw new DomainException('Can phien ban ho so va ghi chu quyet dinh tuyen dung',422);
            if ($workflow >= 2 && empty($p['hr_reviewed_by']))
                throw new DomainException('Ho so chua duoc Truong phong HR duyet',409);
            if ($workflow >= 3) {
                if (hr_recruitment_verified_level($pdo,$p) !== 'senior')
                    throw new DomainException('Tuyen nhan vien thuong khong can BOD duyet',409);
            }
        } elseif (!in_array($p['proposal_type'],
            ['base_salary_change','promotion','transfer','termination'],true)) {
            throw new DomainException('Loai de xuat chua duoc ho tro',422);
        }
        $update = $pdo->prepare("UPDATE hr_proposals SET status=?,reviewed_by=?,reviewed_at=NOW(),review_note=?,version=version+1 WHERE id=? AND status='pending' AND version=?");
        $update->execute([$decision,(int)$me['id'],$note,(int)$id,(int)$p['version']]);
        if ($update->rowCount() !== 1) throw new DomainException('Ho so da duoc xu ly, vui long tai lai',409);
        hr_record_history($pdo,(int)$me['id'],
            $p['employee_id']===null?null:(int)$p['employee_id'],
            'proposal.bod.'.($decision==='approved'?'approved':'rejected'),
            ['status'=>'pending'],['status'=>$decision,'review_note'=>$note],(int)$id);
        if ($decision === 'rejected' && $p['proposal_type'] === 'recruitment') {
            $pdo->prepare('UPDATE hr_proposals SET reservation_username=NULL,reservation_email=NULL WHERE id=?')
                ->execute([(int)$id]);
        }
        $applied = $decision === 'approved' && $p['proposal_type'] !== 'recruitment'
            && ($p['effective_date'] === null || $p['effective_date'] <= date('Y-m-d'));
        if ($applied) {
            $p['status'] = $decision;
            $p['reviewed_by'] = (int)$me['id'];
            hr_apply_personnel_change($pdo, $p, (int)$me['id']);
        }
        $pdo->commit();
        out(['id'=>(int)$id,'status'=>$decision,'version'=>(int)$p['version']+1+(int)$applied,'applied'=>$applied]);
    } catch (Throwable $e) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        if ($e instanceof DomainException) err($e->getMessage(),(int)$e->getCode());
        error_log('BOD HR review failed: '.$e->getMessage());
        err('Khong xu ly duoc phe duyet BOD',500);
    }
}
err('Unknown BOD proposal action',404);
