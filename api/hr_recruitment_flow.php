<?php
/** New candidate records require HR and BOD approval. Existing workflow versions retain their routes. */
function hr_recruitment_next(string $status, string $action): ?string {
    if ($status === 'draft' && $action === 'submit') return 'hr_pending';
    if ($status === 'hr_pending' && $action === 'approve') return 'pending'; // legacy V4 (v2)
    if ($status === 'hr_pending' && $action === 'return') return 'draft';
    if ($status === 'approved' && $action === 'activate') return 'activated';
    return null;
}
/** Senior classification is derived from DB position title, NEVER a client-supplied switch.
 *  Explicit demo rule: department heads, chief accountant and higher.
 *  "Trưởng nhóm" is below department head and therefore ordinary here.
 *  Unknown/new job titles must be reviewed before production deployment.
 */
function hr_recruitment_title_requires_bod(string $title): bool {
    // Deterministic Vietnamese accent folding; does not depend on iconv/OS locale.
    $groups=[
        'a'=>'àáảãạăằắẳẵặâầấẩẫậÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬ',
        'e'=>'èéẻẽẹêềếểễệÈÉẺẼẸÊỀẾỂỄỆ',
        'i'=>'ìíỉĩịÌÍỈĨỊ',
        'o'=>'òóỏõọôồốổỗộơờớởỡợÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢ',
        'u'=>'ùúủũụưừứửữựÙÚỦŨỤƯỪỨỬỮỰ',
        'y'=>'ỳýỷỹỵỲÝỶỸỴ',
        'd'=>'đĐ',
    ];
    $map=[];
    foreach($groups as $plain=>$chars)
        foreach(preg_split('//u',$chars,-1,PREG_SPLIT_NO_EMPTY) as $char)$map[$char]=$plain;
    $ascii=strtr($title,$map);
    $name=trim(preg_replace('/[^a-z0-9]+/',' ',strtolower($ascii)));
    if(preg_match('/\b(manager|management|mgr|admin|administrator|bod|board|president|chairman|executive|head|quan tri he thong|quan ly|ban giam doc|hoi dong quan tri)\b/',$name))return true;
    // Direct assistants and team leads remain ordinary unless another senior title is present.
    if(preg_match('/^(tro ly|thu ky) (?:cua )?(?:(?:tong )?giam doc|chu tich|ceo)$/',$name))return false;
    // Department-head level and above; TP IT is a common short form for Truong phong IT.
    if(preg_match('/(?:^tp [a-z0-9]|\bpho (truong )?(phong|ban|bo phan|giam doc)\b|\btruong (phong|ban|bo phan|khoi)\b|\bke toan truong\b|\b(tong )?giam doc\b|\bchu tich\b|\b(ceo|cfo|coo|chief|director|head of|department manager)\b)/',$name))return true;
    // Existing demo positions and ordinary staff grades.
    if(preg_match('/\b(nhan vien|chuyen vien|ky su|ke toan|thuc tap|cong tac vien|developer|tester|staff|intern|engineer|qa|qc|chuyen gia|tro ly|thu ky|truong nhom|team lead)\b/',$name))return false;
    // Unrecognized titles cannot silently bypass BOD; HR should review/rename them.
    return true;
}
function hr_recruitment_position_requires_bod(PDO $pdo,int $positionId, ?int $departmentId = null): bool {
    $st=$pdo->prepare("SELECT p.title, p.department_id,
        EXISTS (SELECT 1 FROM employees e JOIN roles r ON r.id=e.role_id
          WHERE e.position_id=p.id AND LOWER(r.name)<>'staff') AS elevated_role,
        EXISTS (SELECT 1 FROM departments d WHERE d.id IN (p.department_id,?) AND d.name='BOD') AS bod
        FROM positions p WHERE p.id=? AND p.is_active=1 FOR SHARE");
    $st->execute([$departmentId,$positionId]);$position=$st->fetch(PDO::FETCH_ASSOC);
    if(!$position) err('Chuc vu khong ton tai hoac da ngung',422);
    if($departmentId!==null && $position['department_id']!==null && (int)$position['department_id']!==$departmentId)
        err('Chuc vu khong thuoc phong ban da chon',422);
    return (bool)$position['elevated_role'] || (bool)$position['bod']
        || hr_recruitment_title_requires_bod($position['title']);
}
function hr_recruitment_level(array $proposal): ?string {
    $data=$proposal['proposed_data']??null;
    if(is_string($data))$data=json_decode($data,true);
    $level=is_array($data)?($data['hiring_level']??null):null;
    return in_array($level,['ordinary','senior'],true)?$level:null;
}
/** Check the stored V5 level AND role against the current active DB position. */
function hr_recruitment_verified_level(PDO $pdo, array $proposal): ?string {
    $data=$proposal['proposed_data']??null;
    if(is_string($data))$data=json_decode($data,true);
    if(!is_array($data)||empty($data['position_id']))return null;
    $senior=hr_recruitment_position_requires_bod($pdo,(int)$data['position_id'],isset($data['department_id'])?(int)$data['department_id']:null);
    $level=$senior?'senior':'ordinary';
    if(($data['hiring_level']??null)!==$level || (int)($data['role_id']??0)!==($senior?2:3))return null;
    return $level;
}
/** Internal HR decision: ordinary = approved; senior = pending for BOD. */
function hr_recruitment_review_destination(array $proposal): ?string {
    if(($proposal['status']??'')!=='hr_pending'||($proposal['proposal_type']??'')!=='recruitment')return null;
    if((int)($proposal['workflow_version']??1)<3)return 'pending'; // never auto-approve old V4
    $level=hr_recruitment_level($proposal);
    if($level==='ordinary' && (int)$proposal['workflow_version']===3)return 'approved';
    if($level==='senior')return 'pending';
    return null;
}
function hr_recruitment_can_submit(array $proposal, array $actor): bool {
    if (($proposal['proposal_type'] ?? '') !== 'recruitment' || ($proposal['status'] ?? '') !== 'draft') return false;
    return hr_manager_actor($actor) ||
        (hr_staff_actor($actor) && (int)($proposal['proposed_by'] ?? 0) === (int)$actor['id']);
}
function hr_recruitment_can_edit_draft(array $proposal, array $actor): bool {
    if (($proposal['proposal_type'] ?? '') !== 'recruitment' || ($proposal['status'] ?? '') !== 'draft') return false;
    return hr_manager_actor($actor) ||
        (hr_staff_actor($actor) && (int)($proposal['proposed_by'] ?? 0) === (int)$actor['id']);
}
/** Version 1 and 2 continue to require BOD approval. V5 ordinary may be HR-approved. */
function hr_recruitment_can_activate(array $proposal): bool {
    if (($proposal['proposal_type'] ?? '') !== 'recruitment' || ($proposal['status'] ?? '') !== 'approved') return false;
    if (!empty($proposal['employee_id'])) return false;
    $v=(int)($proposal['workflow_version']??1);
    if($v<3)return !empty($proposal['reviewed_by']) && ($v<2 || !empty($proposal['hr_reviewed_by']));
    if(empty($proposal['hr_reviewed_by']))return false;
    $level=hr_recruitment_level($proposal);
    if($level==='ordinary')return $v===3;
    if($level==='senior')return !empty($proposal['reviewed_by']);
    return false;
}

function review_bod_actor(PDO $pdo, array $user): bool {
    if ($user['role'] !== 'manager' || !is_dept($user, 'BOD')) return false;
    $stmt = $pdo->prepare('SELECT p.title FROM employees e JOIN positions p ON p.id=e.position_id
        WHERE e.id=? AND p.is_active=1 AND (p.department_id=e.department_id OR p.department_id IS NULL)');
    $stmt->execute([(int)$user['id']]);
    $title = trim((string)$stmt->fetchColumn());
    return preg_match('/^(?:(?:phó |pho )?(?:tổng |tong )?(?:giám đốc|giam doc)|(?:deputy )?director|ceo)$/ui', $title) === 1;
}
