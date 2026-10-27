-- phpMyAdmin SQL Dump
-- version 5.2.2
-- https://www.phpmyadmin.net/
--
-- Host: localhost:3306
-- Generation Time: Oct 24, 2026 at 09:08 AM
-- Server version: 8.4.3
-- PHP Version: 8.3.28

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `employee_management`
--

-- --------------------------------------------------------

--
-- Table structure for table `attendances`
--

CREATE TABLE `attendances` (
  `id` int UNSIGNED NOT NULL,
  `employee_id` int UNSIGNED NOT NULL,
  `work_date` date NOT NULL,
  `check_in_time` datetime DEFAULT NULL,
  `check_out_time` datetime DEFAULT NULL,
  `work_hours` decimal(5,2) DEFAULT '0.00',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'present'
) ;

--
-- Dumping data for table `attendances`
--

INSERT INTO `attendances` (`id`, `employee_id`, `work_date`, `check_in_time`, `check_out_time`, `work_hours`, `status`) VALUES
(54, 2, '2026-10-16', '2026-10-16 22:26:00', '2026-10-16 22:30:25', 0.07, 'late'),
(56, 2, '2026-10-17', '2026-10-17 11:58:27', '2026-10-17 20:03:59', 8.09, 'late'),
(57, 4, '2026-10-17', '2026-10-17 11:59:23', '2026-10-17 20:04:57', 8.09, 'late'),
(58, 1, '2026-10-17', NULL, NULL, 0.00, 'absent'),
(59, 3, '2026-10-17', NULL, NULL, 0.00, 'absent'),
(61, 2, '2026-10-18', '2026-10-18 15:11:02', '2026-10-18 22:06:34', 6.93, 'late'),
(68, 4, '2026-10-18', '2026-10-18 15:57:00', NULL, 0.00, 'late'),
(73, 1, '2026-10-18', NULL, NULL, 0.00, 'absent');

-- --------------------------------------------------------

--
-- Table structure for table `departments`
--

CREATE TABLE `departments` (
  `id` int UNSIGNED NOT NULL,
  `name` varchar(250) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `departments`
--

INSERT INTO `departments` (`id`, `name`, `description`) VALUES
(1, 'IT', 'Cong nghe thong tin'),
(2, 'HR', 'Nhan su');

-- --------------------------------------------------------

--
-- Table structure for table `employees`
--

CREATE TABLE `employees` (
  `id` int UNSIGNED NOT NULL,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(15) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `base_salary` decimal(12,2) NOT NULL DEFAULT '0.00',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '1: active, 0: inactive',
  `role_id` int UNSIGNED DEFAULT NULL,
  `department_id` int UNSIGNED DEFAULT NULL,
  `position_id` int UNSIGNED DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `avatar_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL
) ;

--
-- Dumping data for table `employees`
--

INSERT INTO `employees` (`id`, `username`, `password_hash`, `full_name`, `phone`, `email`, `base_salary`, `status`, `role_id`, `department_id`, `position_id`, `created_at`, `avatar_url`) VALUES
(1, 'admin', '$2y$10$4btt3.Poi9RQSnhGDffy5umFZ86Cd5llDd.5tvRXQ.rwR2lOAsHGa', 'Admin', '', 'admin@cty.vn', 0.00, 1, 1, 1, NULL, '2026-10-02 01:57:01', NULL),
(2, 'tuannt03', '$2y$10$XPSl6X689o3bBTTYI0V32OBBZcwrk5/k9qPfEzSSAQC4Ddxg7XsC2', 'Nguyen Trong Tuan', '0975120205', 'tuannguyentrong1202@gmail.com', 15000000.00, 1, 3, 1, NULL, '2026-10-03 16:43:23', 'uploads/avatars/emp_2_1791022657.png'),
(3, 'sep_hr', '$2y$10$PiKHDvIX4MLgMv37uXGQKuJdvvkhLoDWlJM7ARNHJR06nthxC5L2O', 'Sep Phong HR', '0901111111', 'sephr@cty.vn', 0.00, 1, 2, 2, NULL, '2026-10-03 17:26:20', NULL),
(4, 'nv_hr', '$2y$10$4kPudxQBb93iNsdO0iDcoOEq9lrSffh7d3U7VsuUZ0tc7SiJ0jNwm', 'Nhan Vien HR', '0902222222', 'nvhr@cty.vn', 0.00, 1, 3, 2, NULL, '2026-10-03 17:26:20', NULL);

-- --------------------------------------------------------

--
-- Table structure for table `leave_requests`
--

CREATE TABLE `leave_requests` (
  `id` int UNSIGNED NOT NULL,
  `employee_id` int UNSIGNED NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `reason` text COLLATE utf8mb4_unicode_ci,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending',
  `approved_by` int UNSIGNED DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ;

-- --------------------------------------------------------

--
-- Table structure for table `password_resets`
--

CREATE TABLE `password_resets` (
  `id` int UNSIGNED NOT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(15) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `code` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reset_token` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `expires_at` datetime NOT NULL,
  `used` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ;

--
-- Dumping data for table `password_resets`
--

INSERT INTO `password_resets` (`id`, `email`, `phone`, `code`, `reset_token`, `expires_at`, `used`, `created_at`) VALUES
(1, 'admin@cty.vn', NULL, '283140', NULL, '2026-10-03 16:45:31', 1, '2026-10-03 16:35:31'),
(2, 'admin@cty.vn', NULL, '614121', '539232fdb67230e5c8a09027fdc10a1c66540522', '2026-10-03 16:45:46', 1, '2026-10-03 16:35:46'),
(3, 'admin@cty.vn', NULL, '182146', 'f6218982ca45d44bed5bcb3666038f6b4f6dfd02', '2026-10-03 16:48:54', 1, '2026-10-03 16:38:54'),
(4, 'tuannguyentrong1202@gmail.com', NULL, '833594', NULL, '2026-10-03 16:53:45', 1, '2026-10-03 16:43:45'),
(5, 'tuannguyentrong1202@gmail.com', NULL, '355753', NULL, '2026-10-03 16:55:52', 1, '2026-10-03 16:45:52'),
(6, 'tuannguyentrong1202@gmail.com', '0975120205', '693503', '482bf2e0df6b8d4928c227639df38873fb634155', '2026-10-03 17:00:03', 1, '2026-10-03 16:50:03'),
(7, 'tuannguyentrong1202@gmail.com', '0975120205', '165579', NULL, '2026-10-03 17:01:38', 1, '2026-10-03 16:51:38'),
(8, 'tuannguyentrong1202@gmail.com', '0975120205', '841685', NULL, '2026-10-03 17:07:51', 1, '2026-10-03 16:57:51'),
(9, 'tuannguyentrong1202@gmail.com', '0975120205', '007694', '3489d8f4351bf9f5ff3e7954a38b92686a36d71a', '2026-10-03 17:11:08', 1, '2026-10-03 17:01:08'),
(10, 'tuannguyentrong1202@gmail.com', '0975120205', '762261', '8670e68cf68ad314dbc278bd03adb0d7654ef469', '2026-10-03 17:17:18', 1, '2026-10-03 17:07:18');

-- --------------------------------------------------------

--
-- Table structure for table `positions`
--

CREATE TABLE `positions` (
  `id` int UNSIGNED NOT NULL,
  `title` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `positions`
--

INSERT INTO `positions` (`id`, `title`) VALUES
(1, 'Developer'),
(3, 'HR Staff'),
(2, 'Tester');

-- --------------------------------------------------------

--
-- Table structure for table `roles`
--

CREATE TABLE `roles` (
  `id` int UNSIGNED NOT NULL,
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(250) COLLATE utf8mb4_unicode_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `roles`
--

INSERT INTO `roles` (`id`, `name`, `description`) VALUES
(1, 'admin', 'Quan tri he thong'),
(2, 'manager', 'Truong phong'),
(3, 'staff', 'Nhan vien');

-- --------------------------------------------------------

--
-- Table structure for table `salaries`
--

CREATE TABLE `salaries` (
  `id` int UNSIGNED NOT NULL,
  `employee_id` int UNSIGNED NOT NULL,
  `month` tinyint UNSIGNED NOT NULL,
  `year` smallint UNSIGNED NOT NULL,
  `base_salary` decimal(12,2) NOT NULL DEFAULT '0.00',
  `allowance` decimal(12,2) NOT NULL DEFAULT '0.00',
  `overtime_pay` decimal(12,2) NOT NULL DEFAULT '0.00',
  `bonus` decimal(12,2) NOT NULL DEFAULT '0.00',
  `deduction` decimal(12,2) NOT NULL DEFAULT '0.00',
  `net_salary` decimal(12,2) NOT NULL DEFAULT '0.00'
) ;

--
-- Indexes for dumped tables
--

--
-- Indexes for table `attendances`
--
ALTER TABLE `attendances`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_attendances_employee_date` (`employee_id`,`work_date`),
  ADD KEY `idx_attendances_date` (`work_date`);

--
-- Indexes for table `departments`
--
ALTER TABLE `departments`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_departments_name` (`name`);

--
-- Indexes for table `employees`
--
ALTER TABLE `employees`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_employees_username` (`username`),
  ADD UNIQUE KEY `uq_employees_email` (`email`),
  ADD KEY `idx_employees_role` (`role_id`),
  ADD KEY `idx_employees_department` (`department_id`),
  ADD KEY `idx_employees_position` (`position_id`);

--
-- Indexes for table `leave_requests`
--
ALTER TABLE `leave_requests`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_leave_requests_employee` (`employee_id`),
  ADD KEY `idx_leave_requests_approver` (`approved_by`),
  ADD KEY `idx_leave_requests_status` (`status`);

--
-- Indexes for table `password_resets`
--
ALTER TABLE `password_resets`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_password_resets_email_code` (`email`,`code`),
  ADD KEY `idx_password_resets_token` (`reset_token`),
  ADD KEY `idx_password_resets_expires` (`expires_at`);

--
-- Indexes for table `positions`
--
ALTER TABLE `positions`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_positions_title` (`title`);

--
-- Indexes for table `roles`
--
ALTER TABLE `roles`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_roles_name` (`name`);

--
-- Indexes for table `salaries`
--
ALTER TABLE `salaries`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_salaries_employee_month` (`employee_id`,`month`,`year`),
  ADD KEY `idx_salaries_year_month` (`year`,`month`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `attendances`
--
ALTER TABLE `attendances`
  MODIFY `id` int UNSIGNED NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `departments`
--
ALTER TABLE `departments`
  MODIFY `id` int UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

--
-- AUTO_INCREMENT for table `employees`
--
ALTER TABLE `employees`
  MODIFY `id` int UNSIGNED NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `leave_requests`
--
ALTER TABLE `leave_requests`
  MODIFY `id` int UNSIGNED NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `password_resets`
--
ALTER TABLE `password_resets`
  MODIFY `id` int UNSIGNED NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `positions`
--
ALTER TABLE `positions`
  MODIFY `id` int UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;

--
-- AUTO_INCREMENT for table `roles`
--
ALTER TABLE `roles`
  MODIFY `id` int UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;

--
-- AUTO_INCREMENT for table `salaries`
--
ALTER TABLE `salaries`
  MODIFY `id` int UNSIGNED NOT NULL AUTO_INCREMENT;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `attendances`
--
ALTER TABLE `attendances`
  ADD CONSTRAINT `fk_attendances_employee` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints for table `employees`
--
ALTER TABLE `employees`
  ADD CONSTRAINT `fk_employees_department` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT `fk_employees_position` FOREIGN KEY (`position_id`) REFERENCES `positions` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT `fk_employees_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE SET NULL ON UPDATE CASCADE;

--
-- Constraints for table `leave_requests`
--
ALTER TABLE `leave_requests`
  ADD CONSTRAINT `fk_leave_requests_approver` FOREIGN KEY (`approved_by`) REFERENCES `employees` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  ADD CONSTRAINT `fk_leave_requests_employee` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

--
-- Constraints for table `salaries`
--
ALTER TABLE `salaries`
  ADD CONSTRAINT `fk_salaries_employee` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`) ON DELETE CASCADE ON UPDATE CASCADE;

DELIMITER $$
--
-- Events
--
CREATE DEFINER=`root`@`localhost` EVENT `ev_auto_absent` ON SCHEDULE EVERY 1 DAY STARTS '2026-10-24 23:59:00' ON COMPLETION NOT PRESERVE ENABLE DO BEGIN
    INSERT INTO attendances (employee_id, work_date, check_in_time, check_out_time, work_hours, status)
    SELECT id, CURDATE(), NULL, NULL, 0.00, 'absent'
    FROM employees
    WHERE id NOT IN (
        SELECT employee_id 
        FROM attendances 
        WHERE work_date = CURDATE()
    );
END$$

DELIMITER ;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
