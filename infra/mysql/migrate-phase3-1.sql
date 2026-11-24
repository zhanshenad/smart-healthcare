USE `guiguxiaozhi`;

ALTER TABLE `knowledge_chunks`
  ADD COLUMN `outline_path` VARCHAR(1024) DEFAULT NULL AFTER `content`,
  ADD COLUMN `parent_id` VARCHAR(64) DEFAULT NULL AFTER `outline_path`,
  ADD COLUMN `role` VARCHAR(32) DEFAULT NULL AFTER `parent_id`,
  ADD COLUMN `strategy` VARCHAR(64) DEFAULT NULL AFTER `role`,
  ADD KEY `idx_knowledge_chunks_parent` (`parent_id`);
