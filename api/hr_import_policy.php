<?php
/** HR import helpers. No DB writes. */
function hr_import_phone($value): ?string {
    if ($value === null || $value === '') return null;
    if (!is_string($value)) throw new InvalidArgumentException('So dien thoai khong hop le');
    return employee_phone($value);
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
