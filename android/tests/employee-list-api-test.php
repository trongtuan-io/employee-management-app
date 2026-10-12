<?php
// CLI integration tests. Fixture tables are TEMPORARY and disappear with each connection.
// Usage: php employee-list-api-test.php <path-to-local-employee-api/db.php>
if ($argc < 2) { fwrite(STDERR, "Pass the local db.php path.\n"); exit(2); }

if (($argv[2] ?? "") === "--case") {
  $case = json_decode(base64_decode($argv[3]), true);
  $_SERVER["REQUEST_METHOD"] = "GET";
  require $argv[1];
  // TEMPORARY tables shadow persistent names only within this test connection.
  $pdo->exec("CREATE TEMPORARY TABLE departments (id INT PRIMARY KEY, name VARCHAR(100)) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
  $pdo->exec("CREATE TEMPORARY TABLE positions (id INT PRIMARY KEY, title VARCHAR(100))");
  $pdo->exec("CREATE TEMPORARY TABLE roles (id INT PRIMARY KEY, name VARCHAR(100))");
  $pdo->exec("CREATE TEMPORARY TABLE employees (id INT PRIMARY KEY, username VARCHAR(100), full_name VARCHAR(100), phone VARCHAR(30), email VARCHAR(100), base_salary DECIMAL(10,2), status INT, department_id INT, position_id INT, role_id INT) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
  $pdo->exec("INSERT INTO departments VALUES (1,'HR'),(2,'IT'),(3,'Empty')");
  $insert = $pdo->prepare("INSERT INTO employees VALUES (?,?,?,?,?,0,1,?,NULL,NULL)");
  for ($id = 1; $id <= 47; $id++) {
    $name = $id <= 23 ? "An" : ($id <= 30 ? "Bình" : ($id <= 40 ? "Đào" : "Yến"));
    $insert->execute([$id, sprintf("NV%03d", $id), $name, "", sprintf("nv%03d@fixture.invalid", $id),
      $id === 47 ? null : ($id <= 25 ? 1 : 2)]);
  }
  if (!empty($case["broken_employees"])) $pdo->exec("ALTER TABLE employees DROP COLUMN full_name");
  if (!empty($case["broken_departments"])) $pdo->exec("ALTER TABLE departments DROP COLUMN name");
  $_GET = $case["query"];
  $_SERVER["REQUEST_METHOD"] = "GET";
  http_response_code(200);
  ob_start(function ($output) {
    return json_encode(["status" => http_response_code(), "body" => json_decode($output, true)]);
  });
  $source = file_get_contents(__DIR__ . "/../../api/employees.php");
  // Reuse the test connection above, instead of reconnecting and losing its temporary tables.
  $source = preg_replace('/^require_once[^\r\n]+;/m', '', $source, 1, $replaced);
  if ($replaced !== 1) throw new RuntimeException("Expected the existing db.php include");
  eval("?>" . $source);
  exit;
}

$checks = 0;
function check($condition, $label) {
  global $checks;
  if (!$condition) { fwrite(STDERR, "FAIL: $label\n"); exit(1); }
  $checks++;
}
function request($query = [], $extra = []) {
  global $argv;
  $case = base64_encode(json_encode(array_merge(["query" => array_merge(["action" => "list"], $query)], $extra)));
  $pipes = [];
  $process = proc_open([PHP_BINARY, __FILE__, $argv[1], "--case", $case],
    [0 => ["pipe", "r"], 1 => ["pipe", "w"], 2 => ["pipe", "w"]], $pipes);
  fclose($pipes[0]);
  $output = stream_get_contents($pipes[1]); fclose($pipes[1]);
  $error = stream_get_contents($pipes[2]); fclose($pipes[2]);
  $exit = proc_close($process);
  if ($exit !== 0) throw new RuntimeException($error . $output);
  $result = json_decode($output, true);
  if (!is_array($result)) throw new RuntimeException("Invalid JSON: " . $output . $error);
  return $result;
}
function ids($result) { return array_column($result["body"]["data"], "id"); }

foreach (["name_asc", "name_desc", "code_asc", "code_desc"] as $sort) {
  $collected = [];
  for ($page = 1; $page <= 3; $page++) {
    $result = request(["sort" => $sort, "page" => $page, "limit" => 20]);
    check($result["status"] === 200 && $result["body"]["success"], "$sort page $page HTTP");
    check($result["body"]["pagination"] === ["page" => $page, "limit" => 20, "total" => 47, "total_pages" => 3],
      "$sort page $page metadata");
    $collected = array_merge($collected, ids($result));
  }
  $expected = str_ends_with($sort, "desc") ? range(47, 1) : range(1, 47);
  check($collected === $expected, "$sort global order, duplicate names and no overlaps");
}
$result = request();
check(ids($result) === range(47, 28), "legacy default keeps ID descending and data array");
$result = request(["dept_id" => "1", "search" => "Bình", "sort" => "name_desc", "limit" => 1, "page" => 2]);
check(ids($result) === [24] && $result["body"]["pagination"]["total"] === 2, "search + department + sort + page");
$result = request(["dept_id" => "2", "sort" => "code_asc"]);
check(ids($result) === range(26, 45) && $result["body"]["pagination"]["total"] === 21, "department totals");
$result = request(["search" => "NV047"]);
check(ids($result) === [47], "all departments includes unassigned employees");
$result = request(["search" => "nv003@fixture.invalid"]);
check(ids($result) === [3], "existing email search preserved");
foreach ([["search" => "nothing"], ["dept_id" => "3"], ["dept_id" => "999"]] as $query) {
  $result = request($query + ["page" => 9]);
  check(ids($result) === [] && $result["body"]["pagination"] ===
    ["page" => 1, "limit" => 20, "total" => 0, "total_pages" => 1], "empty query");
}
$result = request(["page" => 999, "sort" => "name_asc"]);
check(ids($result) === range(41, 47) && $result["body"]["pagination"]["page"] === 3, "out-of-range page clamps to last");
$result = request(["page" => -2, "limit" => 0]);
check(count(ids($result)) === 1 && $result["body"]["pagination"]["page"] === 1, "lower bounds");
$result = request(["limit" => 999]);
check(count(ids($result)) === 47 && $result["body"]["pagination"]["limit"] === 50, "upper limit");
foreach ([["sort" => "full_name; DROP TABLE employees"], ["dept_id" => "1 OR 1=1"],
          ["dept_id" => "0"], ["page" => "bad"], ["limit" => []], ["search" => []], ["sort" => []]] as $query) {
  $result = request($query);
  check($result["status"] === 400 && !$result["body"]["success"], "invalid parameter rejected");
}
$result = request(["search" => "' OR 1=1 --"]);
check(ids($result) === [], "search remains parameterized");
$result = request(["action" => "departments"]);
check(array_column($result["body"]["data"], "name") === ["Empty", "HR", "IT"], "existing departments endpoint");
$result = request([], ["broken_employees" => true]);
check($result["status"] === 500 && !$result["body"]["success"], "employee SQL failure is JSON");
$result = request(["action" => "departments"], ["broken_departments" => true]);
check($result["status"] === 500 && !$result["body"]["success"], "department SQL failure is JSON");
echo "PASS: $checks PHP/MySQL checks; only session-local temporary tables used.\n";
