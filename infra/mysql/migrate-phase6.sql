-- Phase 6：预约时段唯一约束和号源表
USE guiguxiaozhi;

ALTER TABLE appointment
    ADD UNIQUE KEY uk_appointment_slot (user_id, department, date, time);

CREATE TABLE IF NOT EXISTS department_schedule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    department VARCHAR(50) NOT NULL,
    `date` VARCHAR(10) NOT NULL,
    `time` VARCHAR(10) NOT NULL,
    total INT NOT NULL,
    booked INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schedule_slot (department, `date`, `time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 示例排班，按需修改日期后执行
-- INSERT INTO department_schedule (department, `date`, `time`, total)
-- VALUES ('神经内科', DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '%Y-%m-%d'), '上午', 20)
-- ON DUPLICATE KEY UPDATE total = VALUES(total);
