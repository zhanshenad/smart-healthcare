USE `guiguxiaozhi`;

CREATE TABLE IF NOT EXISTS `knowledge_documents` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `doc_key` VARCHAR(128) NOT NULL,
  `title` VARCHAR(255) DEFAULT NULL,
  `source` VARCHAR(500) NOT NULL,
  `doc_type` VARCHAR(32) NOT NULL,
  `department` VARCHAR(64) DEFAULT NULL,
  `doc_hash` VARCHAR(64) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  `chunk_count` INT NOT NULL DEFAULT 0,
  `error_message` VARCHAR(1000) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_documents_doc_key` (`doc_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `knowledge_chunks` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT NOT NULL,
  `chunk_id` VARCHAR(64) NOT NULL,
  `doc_key` VARCHAR(128) NOT NULL,
  `chunk_index` INT NOT NULL,
  `content_hash` VARCHAR(64) NOT NULL,
  `content` MEDIUMTEXT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_chunks_chunk_id` (`chunk_id`),
  KEY `idx_knowledge_chunks_document` (`document_id`),
  KEY `idx_knowledge_chunks_doc_key` (`doc_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
