<?php
// Bang tin cong ty (news feed o Trang chu):
// - Bang announcements tu tao neu chua co, tu nap 3 tin mau khi bang rong (de demo co noi dung ngay).
// - Moi nhan vien da dang nhap deu doc duoc; chi admin/manager moi dang tin (action=create).
require_once __DIR__ . "/db.php";
require_once __DIR__ . "/auth_guard.php";
$a = $_GET["action"] ?? "list"; $m = $_SERVER["REQUEST_METHOD"];

function announcements_ensure(PDO $pdo) {
  static $done = false;
  if ($done) return;
  $done = true;
  $pdo->exec("CREATE TABLE IF NOT EXISTS announcements (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    created_by INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  $count = (int)$pdo->query("SELECT COUNT(*) FROM announcements")->fetchColumn();
  if ($count === 0) {
    $stmt = $pdo->prepare("INSERT INTO announcements(title, content, created_at) VALUES(?,?,?)");
    $stmt->execute([
      "Chào mừng bạn đến với hệ thống quản lý nhân viên",
      "Từ hôm nay, mọi thông báo của công ty sẽ được đăng tại Bảng tin này. Hãy mở app mỗi ngày để không bỏ lỡ thông tin quan trọng về lịch làm việc, nghỉ phép và bảng lương.",
      date("Y-m-d H:i:s", strtotime("-2 days"))
    ]);
    $stmt->execute([
      "Nhắc nhở: chấm công trước 8h15",
      "Để tránh bị ghi nhận đi muộn, mọi người nhớ chấm công vào ngay khi tới công ty. Trường hợp quên chấm công, hãy liên hệ phòng IT hoặc quản lý trực tiếp trong ngày để được hỗ trợ ghi nhận bổ sung.",
      date("Y-m-d H:i:s", strtotime("-1 day"))
    ]);
    $stmt->execute([
      "Lịch trả lương tháng này",
      "Bảng lương sẽ được cập nhật lên mục Bảng lương trong app vào ngày cuối tháng. Nếu có thắc mắc về phụ cấp, tăng ca hoặc khấu trừ, vui lòng gửi đơn/gặp phòng nhân sự trước ngày chốt lương.",
      date("Y-m-d H:i:s")
    ]);
  }
}

if ($a === "list" && $m === "GET") {
  require_login($pdo);
  announcements_ensure($pdo);
  $stmt = $pdo->query("SELECT a.id, a.title, a.content, a.created_at, e.full_name AS author_name
    FROM announcements a LEFT JOIN employees e ON e.id = a.created_by
    ORDER BY a.created_at DESC, a.id DESC LIMIT 50");
  out($stmt->fetchAll(PDO::FETCH_ASSOC));
}
if ($a === "create" && $m === "POST") {
  $me = require_role($pdo, ["admin", "manager"]);
  announcements_ensure($pdo);
  $b = body();
  $title = is_string($b["title"] ?? null) ? trim($b["title"]) : "";
  $content = is_string($b["content"] ?? null) ? trim($b["content"]) : "";
  if ($title === "" || $content === "") err("Thieu tieu de hoac noi dung");
  if (strlen($title) > 255) err("Tieu de toi da 255 ky tu", 422);
  if (strlen($content) > 10000) err("Noi dung qua dai", 422);
  $pdo->prepare("INSERT INTO announcements(title, content, created_by) VALUES(?,?,?)")
      ->execute([$title, $content, (int)$me["id"]]);
  out(["id" => (int)$pdo->lastInsertId()]);
}
err("Unknown announcements action");
?>
