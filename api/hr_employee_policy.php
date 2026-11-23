<?php
/** Employee directory/profile operations only. BOD approvals are not implemented here. */
function hr_employee_actor(array $u): bool {
    return in_array($u['role'] ?? '', ['manager', 'staff'], true) && is_hr($u);
}
function hr_manager_actor(array $u): bool {
    return ($u['role'] ?? '') === 'manager' && is_hr($u);
}
function hr_staff_actor(array $u): bool {
    return ($u['role'] ?? '') === 'staff' && is_hr($u);
}
function employee_can_list(array $me): bool {
    return in_array($me['role'] ?? '', ['admin','manager'], true) || hr_staff_actor($me);
}
function employee_same_department(array $me, array $target): bool {
    return isset($me['department_id'], $target['department_id'])
        && (int)$me['department_id'] === (int)$target['department_id'];
}
function employee_can_view(array $me, array $target): bool {
    if ((int)($me['id'] ?? 0) === (int)($target['id'] ?? -1)) return true;
    if (($me['role'] ?? '') === 'admin' || hr_employee_actor($me)) return true;
    return ($me['role'] ?? '') === 'manager' && employee_same_department($me, $target);
}
function employee_can_edit_contact(array $me, array $target): bool {
    if ((int)($me['id'] ?? 0) === (int)($target['id'] ?? -1)) return true;
    if (($me['role'] ?? '') === 'admin') return true;
    // HR can update contact fields company-wide, except technical admin accounts.
    if (hr_employee_actor($me) && in_array($target['target_role'] ?? '', ['staff','manager'], true)) return true;
    // Managers in other departments may view work records, not modify staff contact records.
    return false;
}
