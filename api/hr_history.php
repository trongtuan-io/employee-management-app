<?php
require_once __DIR__.'/db.php';require_once __DIR__.'/auth_guard.php';require_once __DIR__.'/hr_core.php';
$me=hr_auth($pdo);hr_require_migration($pdo);
if($_SERVER['REQUEST_METHOD']!=='GET')err('Method not allowed',405);
$employee=filter_var($_GET['employee_id']??null,FILTER_VALIDATE_INT,['options'=>['min_range'=>1]]);
$where=[];$params=[];
if($employee){$where[]='h.employee_id=?';$params[]=(int)$employee;}
if(hr_staff_actor($me)){$where[]='h.actor_id=?';$params[]=(int)$me['id'];}
// HR-only read view: names and proposal context, without changing any approval or history records.
$sql='SELECT h.id,h.employee_id,h.actor_id,h.action,h.before_data,h.after_data,h.proposal_id,h.created_at,'
    .'a.full_name AS actor_name,a.username AS actor_username,'
    .'target.full_name AS employee_name,'
    .'p.proposal_type,p.status AS proposal_status,p.reason AS proposal_reason,'
    .'p.effective_date AS proposal_effective_date,p.before_data AS proposal_before_data,'
    .'p.proposed_data AS proposal_proposed_data,p.created_at AS proposal_created_at,'
    .'creator.full_name AS proposal_creator_name,creator.username AS proposal_creator_username '
    .'FROM hr_history h LEFT JOIN employees a ON a.id=h.actor_id '
    .'LEFT JOIN employees target ON target.id=h.employee_id '
    .'LEFT JOIN hr_proposals p ON p.id=h.proposal_id '
    .'LEFT JOIN employees creator ON creator.id=p.proposed_by';
if($where)$sql.=' WHERE '.implode(' AND ',$where);
$sql.=' ORDER BY h.id DESC LIMIT 200';
$st=$pdo->prepare($sql);$st->execute($params);$rows=$st->fetchAll(PDO::FETCH_ASSOC);
foreach($rows as &$r){
    // A recruitment proposal has no employee_id until account activation.
    $proposed=$r['proposal_proposed_data']===null?null:json_decode($r['proposal_proposed_data'],true);
    $r['candidate_name']=is_array($proposed)?($proposed['full_name']??null):null;
    if(hr_staff_actor($me)){
        // Keep the same Staff HR limits on before/after details as the original API.
        unset($r['before_data'],$r['after_data'],$r['proposal_before_data'],$r['proposal_proposed_data']);
        continue;
    }
    $r['before_data']=$r['before_data']===null?null:json_decode($r['before_data'],true);
    $r['after_data']=$r['after_data']===null?null:json_decode($r['after_data'],true);
    $r['proposal_before_data']=$r['proposal_before_data']===null?null:json_decode($r['proposal_before_data'],true);
    $r['proposal_proposed_data']=$proposed;
}
unset($r);out($rows);
