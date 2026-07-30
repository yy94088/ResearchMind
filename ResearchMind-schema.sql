CREATE DATABASE IF NOT EXISTS researchmind;

USE researchmind;

CREATE TABLE user (
  id VARCHAR(36) PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(100) NOT NULL,
  email VARCHAR(100) NOT NULL,
  role ENUM('USER', 'ADMIN', 'MANAGER') NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE paper (
  id VARCHAR(36) PRIMARY KEY,
  title VARCHAR(500) NOT NULL,
  abstract TEXT,
  doi VARCHAR(100),
  upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE author (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(200) NOT NULL
);

CREATE TABLE keyword (
  id VARCHAR(36) PRIMARY KEY,
  word VARCHAR(100) NOT NULL,
  research_area_id VARCHAR(36) REFERENCES research_area(id)
);

CREATE TABLE research_area (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(200) NOT NULL
);

-- Relationship tables
CREATE TABLE paper_author (
  paper_id VARCHAR(36) REFERENCES paper(id),
  author_id VARCHAR(36) REFERENCES author(id),
  PRIMARY KEY (paper_id, author_id)
);

CREATE TABLE paper_keyword (
  paper_id VARCHAR(36) REFERENCES paper(id),
  keyword_id VARCHAR(36) REFERENCES keyword(id),
  PRIMARY KEY (paper_id, keyword_id)
);

CREATE TABLE paper_area (
  paper_id VARCHAR(36) REFERENCES paper(id),
  area_id VARCHAR(36) REFERENCES research_area(id),
  PRIMARY KEY (paper_id, area_id)
);

CREATE TABLE upload_record (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) REFERENCES user(id),
  paper_id VARCHAR(36) REFERENCES paper(id),
  upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);