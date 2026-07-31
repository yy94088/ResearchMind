-- ResearchMind 智能科研文献管理平台
-- MySQL 8.0 数据库结构 V1.0

CREATE DATABASE IF NOT EXISTS researchmind
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE researchmind;

-- 用户与团队
CREATE TABLE users (
  id CHAR(36) PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  email VARCHAR(100) NOT NULL UNIQUE,
  real_name VARCHAR(50) NOT NULL,
  avatar_url VARCHAR(500),
  institution VARCHAR(200),
  research_direction VARCHAR(300),
  bio VARCHAR(500),
  role ENUM('USER', 'MANAGER', 'ADMIN') NOT NULL DEFAULT 'USER',
  status ENUM('ACTIVE', 'DISABLED', 'PENDING') NOT NULL DEFAULT 'ACTIVE',
  last_login_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_users_status (status),
  INDEX idx_users_create_time (create_time)
) ENGINE=InnoDB COMMENT='用户信息';

CREATE TABLE team (
  id CHAR(36) PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(500),
  institution VARCHAR(200),
  owner_id CHAR(36) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_team_owner FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB COMMENT='科研团队';

CREATE TABLE team_member (
  team_id CHAR(36) NOT NULL,
  user_id CHAR(36) NOT NULL,
  member_role ENUM('OWNER', 'MANAGER', 'MEMBER', 'GUEST') NOT NULL DEFAULT 'MEMBER',
  join_status ENUM('PENDING', 'ACCEPTED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
  join_time DATETIME,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (team_id, user_id),
  CONSTRAINT fk_team_member_team FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE,
  CONSTRAINT fk_team_member_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_team_member_user (user_id)
) ENGINE=InnoDB COMMENT='团队成员';

-- 文献元数据
CREATE TABLE research_area (
  id CHAR(36) PRIMARY KEY,
  name VARCHAR(200) NOT NULL UNIQUE,
  description VARCHAR(500),
  parent_id CHAR(36),
  color VARCHAR(20),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_area_parent FOREIGN KEY (parent_id) REFERENCES research_area(id) ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='研究领域';

CREATE TABLE paper (
  id CHAR(36) PRIMARY KEY,
  owner_id CHAR(36) NOT NULL,
  title VARCHAR(500) NOT NULL,
  title_zh VARCHAR(500),
  abstract_text LONGTEXT,
  doi VARCHAR(150),
  journal VARCHAR(300),
  publish_year SMALLINT,
  language VARCHAR(20) DEFAULT 'en',
  file_name VARCHAR(500),
  file_url VARCHAR(1000),
  file_size BIGINT UNSIGNED,
  page_count INT UNSIGNED,
  parse_status ENUM('PENDING', 'PARSING', 'SUCCESS', 'FAILED') NOT NULL DEFAULT 'PENDING',
  visibility ENUM('PRIVATE', 'TEAM', 'PUBLIC') NOT NULL DEFAULT 'PRIVATE',
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  upload_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_paper_owner FOREIGN KEY (owner_id) REFERENCES users(id),
  UNIQUE KEY uk_paper_owner_doi (owner_id, doi),
  FULLTEXT INDEX ft_paper_title_abstract (title, title_zh, abstract_text),
  INDEX idx_paper_year (publish_year),
  INDEX idx_paper_upload_time (upload_time),
  INDEX idx_paper_status (owner_id, deleted, parse_status)
) ENGINE=InnoDB COMMENT='科研文献';

CREATE TABLE author (
  id CHAR(36) PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  normalized_name VARCHAR(200) NOT NULL,
  institution VARCHAR(300),
  orcid VARCHAR(50),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_author_identity (normalized_name, institution),
  INDEX idx_author_name (name)
) ENGINE=InnoDB COMMENT='文献作者';

CREATE TABLE keyword (
  id CHAR(36) PRIMARY KEY,
  word VARCHAR(100) NOT NULL,
  normalized_word VARCHAR(100) NOT NULL,
  research_area_id CHAR(36),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_keyword_area FOREIGN KEY (research_area_id) REFERENCES research_area(id) ON DELETE SET NULL,
  UNIQUE KEY uk_keyword_normalized (normalized_word),
  INDEX idx_keyword_area (research_area_id)
) ENGINE=InnoDB COMMENT='文献关键词';

CREATE TABLE tag (
  id CHAR(36) PRIMARY KEY,
  owner_id CHAR(36) NOT NULL,
  name VARCHAR(100) NOT NULL,
  color VARCHAR(20),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_tag_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
  UNIQUE KEY uk_tag_owner_name (owner_id, name)
) ENGINE=InnoDB COMMENT='用户自定义标签';

CREATE TABLE paper_author (
  paper_id CHAR(36) NOT NULL,
  author_id CHAR(36) NOT NULL,
  author_order SMALLINT UNSIGNED NOT NULL DEFAULT 1,
  is_corresponding TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (paper_id, author_id),
  CONSTRAINT fk_paper_author_paper FOREIGN KEY (paper_id) REFERENCES paper(id) ON DELETE CASCADE,
  CONSTRAINT fk_paper_author_author FOREIGN KEY (author_id) REFERENCES author(id) ON DELETE CASCADE,
  INDEX idx_paper_author_author (author_id)
) ENGINE=InnoDB COMMENT='文献作者关系';

CREATE TABLE paper_keyword (
  paper_id CHAR(36) NOT NULL,
  keyword_id CHAR(36) NOT NULL,
  weight DECIMAL(6, 5) DEFAULT 1.00000,
  source ENUM('AUTHOR', 'AI', 'USER') NOT NULL DEFAULT 'AUTHOR',
  PRIMARY KEY (paper_id, keyword_id),
  CONSTRAINT fk_paper_keyword_paper FOREIGN KEY (paper_id) REFERENCES paper(id) ON DELETE CASCADE,
  CONSTRAINT fk_paper_keyword_keyword FOREIGN KEY (keyword_id) REFERENCES keyword(id) ON DELETE CASCADE,
  INDEX idx_paper_keyword_keyword (keyword_id)
) ENGINE=InnoDB COMMENT='文献关键词关系';

CREATE TABLE paper_area (
  paper_id CHAR(36) NOT NULL,
  area_id CHAR(36) NOT NULL,
  confidence DECIMAL(6, 5) DEFAULT 1.00000,
  is_primary TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (paper_id, area_id),
  CONSTRAINT fk_paper_area_paper FOREIGN KEY (paper_id) REFERENCES paper(id) ON DELETE CASCADE,
  CONSTRAINT fk_paper_area_area FOREIGN KEY (area_id) REFERENCES research_area(id) ON DELETE CASCADE,
  INDEX idx_paper_area_area (area_id)
) ENGINE=InnoDB COMMENT='文献研究领域关系';

CREATE TABLE paper_tag (
  paper_id CHAR(36) NOT NULL,
  tag_id CHAR(36) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (paper_id, tag_id),
  CONSTRAINT fk_paper_tag_paper FOREIGN KEY (paper_id) REFERENCES paper(id) ON DELETE CASCADE,
  CONSTRAINT fk_paper_tag_tag FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE,
  INDEX idx_paper_tag_tag (tag_id)
) ENGINE=InnoDB COMMENT='文献标签关系';

-- 用户阅读行为
CREATE TABLE user_favorite (
  user_id CHAR(36) NOT NULL,
  paper_id CHAR(36) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, paper_id),
  CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_favorite_paper FOREIGN KEY (paper_id) REFERENCES paper(id) ON DELETE CASCADE,
  INDEX idx_favorite_paper (paper_id)
) ENGINE=InnoDB COMMENT='文献收藏';

CREATE TABLE reading_record (
  user_id CHAR(36) NOT NULL,
  paper_id CHAR(36) NOT NULL,
  progress TINYINT UNSIGNED NOT NULL DEFAULT 0,
  current_page INT UNSIGNED NOT NULL DEFAULT 0,
  total_read_seconds INT UNSIGNED NOT NULL DEFAULT 0,
  is_finished TINYINT(1) NOT NULL DEFAULT 0,
  last_read_time DATETIME,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, paper_id),
  CONSTRAINT fk_reading_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_reading_paper FOREIGN KEY (paper_id) REFERENCES paper(id) ON DELETE CASCADE,
  CONSTRAINT chk_reading_progress CHECK (progress BETWEEN 0 AND 100),
  INDEX idx_reading_last_time (user_id, last_read_time)
) ENGINE=InnoDB COMMENT='阅读进度';

CREATE TABLE paper_note (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  paper_id CHAR(36) NOT NULL,
  page_number INT UNSIGNED,
  selected_text TEXT,
  note_content TEXT NOT NULL,
  visibility ENUM('PRIVATE', 'TEAM') NOT NULL DEFAULT 'PRIVATE',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_note_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_note_paper FOREIGN KEY (paper_id) REFERENCES paper(id) ON DELETE CASCADE,
  INDEX idx_note_paper_user (paper_id, user_id)
) ENGINE=InnoDB COMMENT='阅读笔记与批注';

-- 团队共享
CREATE TABLE collection (
  id CHAR(36) PRIMARY KEY,
  team_id CHAR(36),
  owner_id CHAR(36) NOT NULL,
  name VARCHAR(150) NOT NULL,
  description VARCHAR(500),
  cover_color VARCHAR(20),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_collection_team FOREIGN KEY (team_id) REFERENCES team(id) ON DELETE CASCADE,
  CONSTRAINT fk_collection_owner FOREIGN KEY (owner_id) REFERENCES users(id),
  INDEX idx_collection_team (team_id)
) ENGINE=InnoDB COMMENT='文献专题库';

CREATE TABLE collection_paper (
  collection_id CHAR(36) NOT NULL,
  paper_id CHAR(36) NOT NULL,
  added_by CHAR(36) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (collection_id, paper_id),
  CONSTRAINT fk_collection_paper_collection FOREIGN KEY (collection_id) REFERENCES collection(id) ON DELETE CASCADE,
  CONSTRAINT fk_collection_paper_paper FOREIGN KEY (paper_id) REFERENCES paper(id) ON DELETE CASCADE,
  CONSTRAINT fk_collection_paper_user FOREIGN KEY (added_by) REFERENCES users(id),
  INDEX idx_collection_paper_paper (paper_id)
) ENGINE=InnoDB COMMENT='专题文献关系';

-- 知识图谱
CREATE TABLE graph_node (
  id CHAR(36) PRIMARY KEY,
  owner_id CHAR(36) NOT NULL,
  node_type ENUM('PAPER', 'AUTHOR', 'KEYWORD', 'AREA', 'INSTITUTION') NOT NULL,
  reference_id CHAR(36),
  name VARCHAR(500) NOT NULL,
  properties JSON,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_graph_node_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
  UNIQUE KEY uk_graph_node_reference (owner_id, node_type, reference_id),
  INDEX idx_graph_node_type (owner_id, node_type),
  INDEX idx_graph_node_name (name(100))
) ENGINE=InnoDB COMMENT='知识图谱节点';

CREATE TABLE graph_relation (
  id CHAR(36) PRIMARY KEY,
  owner_id CHAR(36) NOT NULL,
  source_node_id CHAR(36) NOT NULL,
  target_node_id CHAR(36) NOT NULL,
  relation_type ENUM('AUTHORED_BY', 'HAS_KEYWORD', 'BELONGS_TO', 'CITES', 'COOPERATES_WITH', 'RELATED_TO') NOT NULL,
  weight DECIMAL(8, 5) NOT NULL DEFAULT 1.00000,
  properties JSON,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_graph_relation_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_graph_relation_source FOREIGN KEY (source_node_id) REFERENCES graph_node(id) ON DELETE CASCADE,
  CONSTRAINT fk_graph_relation_target FOREIGN KEY (target_node_id) REFERENCES graph_node(id) ON DELETE CASCADE,
  UNIQUE KEY uk_graph_relation (owner_id, source_node_id, target_node_id, relation_type),
  INDEX idx_graph_relation_target (target_node_id)
) ENGINE=InnoDB COMMENT='知识图谱关系';

-- AI 分析与任务
CREATE TABLE ai_analysis (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  paper_id CHAR(36) NOT NULL,
  analysis_type ENUM('SUMMARY', 'CONTRIBUTION', 'INNOVATION', 'QA') NOT NULL,
  model_name VARCHAR(100),
  input_hash CHAR(64),
  result_content LONGTEXT,
  token_usage INT UNSIGNED,
  status ENUM('PENDING', 'RUNNING', 'SUCCESS', 'FAILED') NOT NULL DEFAULT 'PENDING',
  error_message VARCHAR(1000),
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finish_time DATETIME,
  CONSTRAINT fk_ai_analysis_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_ai_analysis_paper FOREIGN KEY (paper_id) REFERENCES paper(id) ON DELETE CASCADE,
  INDEX idx_ai_analysis_paper (paper_id, analysis_type, status),
  INDEX idx_ai_analysis_user_time (user_id, create_time)
) ENGINE=InnoDB COMMENT='AI 文献分析记录';

CREATE TABLE upload_record (
  id CHAR(36) PRIMARY KEY,
  user_id CHAR(36) NOT NULL,
  paper_id CHAR(36),
  original_file_name VARCHAR(500) NOT NULL,
  file_size BIGINT UNSIGNED,
  batch_no VARCHAR(64),
  status ENUM('UPLOADING', 'PARSING', 'SUCCESS', 'FAILED') NOT NULL DEFAULT 'UPLOADING',
  error_message VARCHAR(1000),
  upload_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finish_time DATETIME,
  CONSTRAINT fk_upload_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_upload_paper FOREIGN KEY (paper_id) REFERENCES paper(id) ON DELETE SET NULL,
  INDEX idx_upload_user_time (user_id, upload_time),
  INDEX idx_upload_batch (batch_no)
) ENGINE=InnoDB COMMENT='文献上传与解析记录';

-- 安全与审计
CREATE TABLE login_log (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id CHAR(36),
  login_account VARCHAR(100) NOT NULL,
  ip_address VARCHAR(64),
  user_agent VARCHAR(1000),
  login_status ENUM('SUCCESS', 'FAILED') NOT NULL,
  failure_reason VARCHAR(300),
  login_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_login_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_login_log_user_time (user_id, login_time),
  INDEX idx_login_log_ip_time (ip_address, login_time)
) ENGINE=InnoDB COMMENT='登录日志';

CREATE TABLE operation_log (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  user_id CHAR(36),
  module VARCHAR(50) NOT NULL,
  operation VARCHAR(100) NOT NULL,
  target_type VARCHAR(50),
  target_id VARCHAR(100),
  request_method VARCHAR(10),
  request_path VARCHAR(500),
  ip_address VARCHAR(64),
  success TINYINT(1) NOT NULL DEFAULT 1,
  detail JSON,
  operation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_operation_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_operation_log_user_time (user_id, operation_time),
  INDEX idx_operation_log_module_time (module, operation_time)
) ENGINE=InnoDB COMMENT='系统操作审计日志';
