<?php
/** HR import helpers. No DB writes. */
function hr_import_phone($value): ?string {
    if ($value === null || $value === '') return null;
    if (!is_string($value)) throw new InvalidArgumentException('So dien thoai khong hop le');
    return employee_phone($value);
}
/** Revalidate the FINAL department+position combination after merging an import with a pending candidate. */
function hr_import_validate_position(PDO $pdo, array $candidate): void {
    $positionId = $candidate['position_id'] ?? null;
    if ($positionId === null || $positionId === '' || (int)$positionId === 0) return;
    $deptId = $candidate['department_id'] ?? null;
    validate_position_for_department($pdo, (int)$positionId, $deptId);
    hr_require_active_position($pdo, (int)$positionId);
}

/** Changes for an existing employee. Import is patch-like, not a full replacement. */
function hr_import_existing_changes(PDO $pdo, array $item, array $existing, bool $manager): array {
    $input=['full_name'=>$item['full_name'],'email'=>$item['email']];
    if(($item['phone']??null)!==null)$input['phone']=$item['phone'];
    $canEditHire=($item['hire_date']??null)!==null && $manager;
    if($canEditHire)$input['hire_date']=$item['hire_date'];
    $changes=hr_profile_changes($pdo,$input,$existing);
    if($canEditHire && (string)($existing['hire_date']??'')!==$item['hire_date'])
        $changes['hire_date']=$item['hire_date'];
    return $changes;
}
/** Refresh only a DRAFT. Recalculate the authority level from the final position. */
function hr_import_merge_draft(PDO $pdo, array $old, array $item, int $workflowVersion): array {
    $new=array_merge($old,array_filter($item,fn($value)=>$value!==null));
    hr_import_validate_position($pdo,$new);
    if($workflowVersion>=3){
        if(empty($new['position_id']))throw new InvalidArgumentException('Ho so tuyen dung phai co chuc vu');
        $senior=hr_recruitment_position_requires_bod($pdo,(int)$new['position_id']);
        $new['hiring_level']=$senior?'senior':'ordinary';
        $new['role_id']=$senior?2:3;
    }
    return $new;
}
