/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;


# Dump of table answer_option
# ------------------------------------------------------------

DROP TABLE IF EXISTS `answer_option`;

CREATE TABLE `answer_option` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `is_correct` bit(1) DEFAULT NULL,
  `text` varchar(255) DEFAULT NULL,
  `question_id` bigint(20) DEFAULT NULL,
  `explanation` varchar(255) DEFAULT NULL,
  `hint` varchar(255) DEFAULT NULL,
  `answer_options_order` int(11) DEFAULT NULL,
  `invalid` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKfqeqisl0e28xp3yn9bmlgkhej` (`question_id`),
  CONSTRAINT `FKfqeqisl0e28xp3yn9bmlgkhej` FOREIGN KEY (`question_id`) REFERENCES `quiz_question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table apollon_diagram
# ------------------------------------------------------------

DROP TABLE IF EXISTS `apollon_diagram`;

CREATE TABLE `apollon_diagram` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `json_representation` longtext,
  `title` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table attachment
# ------------------------------------------------------------

DROP TABLE IF EXISTS `attachment`;

CREATE TABLE `attachment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `attachment_type` varchar(255) DEFAULT NULL,
  `jhi_link` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `release_date` datetime DEFAULT NULL,
  `exercise_id` bigint(20) DEFAULT NULL,
  `lecture_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK840kdpn5r9l2wyhq83gl6sx2g` (`exercise_id`),
  KEY `FKa6apwe76xlljxtm3nimrlvppf` (`lecture_id`),
  CONSTRAINT `FK840kdpn5r9l2wyhq83gl6sx2g` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `FKa6apwe76xlljxtm3nimrlvppf` FOREIGN KEY (`lecture_id`) REFERENCES `lecture` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table complaint
# ------------------------------------------------------------

DROP TABLE IF EXISTS `complaint`;

CREATE TABLE `complaint` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `accepted` bit(1) DEFAULT NULL,
  `complaint_text` varchar(2000) DEFAULT NULL,
  `result_before_complaint` longtext,
  `submitted_time` datetime DEFAULT NULL,
  `result_id` bigint(20) DEFAULT NULL,
  `student_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_COMPLAINTRESULT_ID_COL` (`result_id`),
  UNIQUE KEY `UC_COMPLAINTSTUDENT_ID_COL` (`student_id`),
  CONSTRAINT `FKgyh851bnmsti3b0tmvtdf1y97` FOREIGN KEY (`result_id`) REFERENCES `result` (`id`),
  CONSTRAINT `FKjodokcxrnd8igpwe9g36a26p3` FOREIGN KEY (`student_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table complaint_response
# ------------------------------------------------------------

DROP TABLE IF EXISTS `complaint_response`;

CREATE TABLE `complaint_response` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `response_text` varchar(2000) DEFAULT NULL,
  `submitted_time` datetime DEFAULT NULL,
  `complaint_id` bigint(20) DEFAULT NULL,
  `reviewer_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_COMPLAINT_RESPONSECOMPLAINT_ID_COL` (`complaint_id`),
  UNIQUE KEY `UC_COMPLAINT_RESPONSEREVIEWER_ID_COL` (`reviewer_id`),
  CONSTRAINT `FKb864d65horyth8i17crcdco2j` FOREIGN KEY (`reviewer_id`) REFERENCES `jhi_user` (`id`),
  CONSTRAINT `FKrwjscojfvjqea2hl24gvl5yvb` FOREIGN KEY (`complaint_id`) REFERENCES `complaint` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table course
# ------------------------------------------------------------

DROP TABLE IF EXISTS `course`;

CREATE TABLE `course` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) DEFAULT NULL,
  `student_group_name` varchar(255) DEFAULT NULL,
  `teaching_assistant_group_name` varchar(255) DEFAULT NULL,
  `end_date` datetime(6) DEFAULT NULL,
  `instructor_group_name` varchar(255) DEFAULT NULL,
  `online_course` bit(1) DEFAULT NULL,
  `start_date` datetime(6) DEFAULT NULL,
  `short_name` varchar(255) DEFAULT NULL,
  `max_complaints` int(11) DEFAULT NULL,
  `description` longtext,
  `color` varchar(255) DEFAULT NULL,
  `course_icon` varchar(255) DEFAULT NULL,
  `registration_enabled` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table DATABASECHANGELOG
# ------------------------------------------------------------

DROP TABLE IF EXISTS `DATABASECHANGELOG`;

CREATE TABLE `DATABASECHANGELOG` (
  `ID` varchar(255) NOT NULL,
  `AUTHOR` varchar(255) NOT NULL,
  `FILENAME` varchar(255) NOT NULL,
  `DATEEXECUTED` datetime NOT NULL,
  `ORDEREXECUTED` int(11) NOT NULL,
  `EXECTYPE` varchar(10) NOT NULL,
  `MD5SUM` varchar(35) DEFAULT NULL,
  `DESCRIPTION` varchar(255) DEFAULT NULL,
  `COMMENTS` varchar(255) DEFAULT NULL,
  `TAG` varchar(255) DEFAULT NULL,
  `LIQUIBASE` varchar(20) DEFAULT NULL,
  `CONTEXTS` varchar(255) DEFAULT NULL,
  `LABELS` varchar(255) DEFAULT NULL,
  `DEPLOYMENT_ID` varchar(10) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

LOCK TABLES `DATABASECHANGELOG` WRITE;
/*!40000 ALTER TABLE `DATABASECHANGELOG` DISABLE KEYS */;

INSERT INTO `DATABASECHANGELOG` (`ID`, `AUTHOR`, `FILENAME`, `DATEEXECUTED`, `ORDEREXECUTED`, `EXECTYPE`, `MD5SUM`, `DESCRIPTION`, `COMMENTS`, `TAG`, `LIQUIBASE`, `CONTEXTS`, `LABELS`, `DEPLOYMENT_ID`)
VALUES
	('00000000000001','krusche','classpath:config/liquibase/changelog/00000000000000_initial_schema.xml','2019-03-19 08:48:25',1,'EXECUTED','7:eeffd7387432ea164fadce0ecdb244f3','createTable tableName=answer_option; createTable tableName=apollon_diagram; createTable tableName=course; createTable tableName=drag_and_drop_mapping; createTable tableName=drag_item; createTable tableName=drop_location; createTable tableName=exer...','',NULL,'3.5.4',NULL,NULL,'2981703737'),
	('1540544592272-1','krusche','classpath:config/liquibase/changelog/20181026110308_changelog.xml','2019-03-19 08:48:25',2,'EXECUTED','7:e5e380ada7446fd7bcde59447da51dcb','addColumn tableName=exercise; addColumn tableName=course','',NULL,'3.5.4',NULL,NULL,'2981703737'),
	('1541592773036-1','krusche','classpath:config/liquibase/changelog/20181107131246_changelog.xml','2019-03-19 08:48:25',3,'EXECUTED','7:5c0e6736ac0c7bcc394db7784ebe1ced','createTable tableName=short_answer_submitted_text; createTable tableName=short_answer_mapping; createTable tableName=short_answer_solution; createTable tableName=short_answer_spot; addColumn tableName=statistic_counter; addColumn tableName=statist...','',NULL,'3.5.4',NULL,NULL,'2981703737'),
	('1543924264951-1','jpbernius','classpath:config/liquibase/changelog/20181204125049_changelog.xml','2019-03-19 08:48:25',4,'EXECUTED','7:b9b577362ae4d90e94798a4baca6e19d','addColumn tableName=feedback; modifyDataType columnName=text, tableName=feedback','',NULL,'3.5.4',NULL,NULL,'2981703737'),
	('1544786174713','krusche','classpath:config/liquibase/changelog/20181214121608_changelog.xml','2019-03-19 08:48:26',5,'EXECUTED','7:bb5b842f893ef8ce8f6759e87efe95f1','createTable tableName=complaint; createTable tableName=complaint_response; createTable tableName=example_submission; createTable tableName=tutor_participation; addColumn tableName=result; addColumn tableName=submission; addColumn tableName=course;...','',NULL,'3.5.4',NULL,NULL,'2981703737'),
	('1548099226633','krusche','classpath:config/liquibase/changelog/20190121203339_changelog.xml','2019-03-19 08:48:26',6,'EXECUTED','7:4dd8bd6119ec1ea4c30de12c05b26efa','addColumn tableName=exercise; dropColumn columnName=released, tableName=statistic','',NULL,'3.5.4',NULL,NULL,'2981703737'),
	('1548585910503-01','krusche','classpath:config/liquibase/changelog/20190127114504_changelog.xml','2019-03-19 08:48:26',7,'EXECUTED','7:33b25548db05939aa763881245b4d9c5','dropForeignKeyConstraint baseTableName=tutor_participation, constraintName=FK3hpwu78yd7lmteft5itac6t1k; dropForeignKeyConstraint baseTableName=tutor_participation, constraintName=FKqxdo67bt084eag6onrwkww8vq; dropUniqueConstraint constraintName=UC_...','',NULL,'3.5.4',NULL,NULL,'2981703737'),
	('1548585910503-02','krusche','classpath:config/liquibase/changelog/20190127114504_changelog.xml','2019-03-19 08:48:26',8,'EXECUTED','7:d702e8471b4b6d0d444ee95017408083','addColumn tableName=course; addColumn tableName=short_answer_submitted_text; addColumn tableName=short_answer_spot','',NULL,'3.5.4',NULL,NULL,'2981703737'),
	('1548777349810','krusche','classpath:config/liquibase/changelog/20190129165404_changelog.xml','2019-03-19 08:48:26',9,'EXECUTED','7:c5c9258536aff7a086f79df85b8a7d4c','dropForeignKeyConstraint baseTableName=short_answer_submitted_text, constraintName=FKpkb6e1yjqhma5tgvabb9smyv3; dropUniqueConstraint constraintName=UC_SHORT_ANSWER_SUBMITTED_TEXTSPOT_ID_COL, tableName=short_answer_submitted_text; addForeignKeyCons...','',NULL,'3.5.4',NULL,NULL,'2981703737'),
	('1551295289847-1','krusche','classpath:config/liquibase/changelog/20190227202121_changelog.xml','2019-03-19 08:48:26',10,'EXECUTED','7:5031461725e91613c4c16c51196d8358','addColumn tableName=exercise; addUniqueConstraint constraintName=UC_EXERCISESOLUTION_PARTICIPATION_ID_COL, tableName=exercise; addUniqueConstraint constraintName=UC_EXERCISETEMPLATE_PARTICIPATION_ID_COL, tableName=exercise; addForeignKeyConstraint...','',NULL,'3.5.4',NULL,NULL,'2981703737'),
	('1551295875207','krusche','classpath:config/liquibase/changelog/20190227203021_changelog.xml','2019-03-19 08:48:26',11,'EXECUTED','7:085f5201d6c009e9968ae9262ecdcc1b','dropColumn columnName=base_build_plan_id, tableName=exercise; dropColumn columnName=base_repository_url, tableName=exercise; dropColumn columnName=solution_build_plan_id, tableName=exercise; dropColumn columnName=solution_repository_url, tableName...','',NULL,'3.5.4',NULL,NULL,'2981703737'),
	('1552664538429','krusche','classpath:config/liquibase/changelog/20190315164212_changelog.xml','2019-03-19 08:48:26',12,'EXECUTED','7:06fb51a3ede8bb41aafd8ee1b78fa5a1','renameTable newTableName=quiz_question, oldTableName=question; renameTable newTableName=quiz_statistic, oldTableName=statistic; renameTable newTableName=quiz_statistic_counter, oldTableName=statistic_counter; renameColumn newColumnName=quiz_questi...','',NULL,'3.5.4',NULL,NULL,'2981703737');

/*!40000 ALTER TABLE `DATABASECHANGELOG` ENABLE KEYS */;
UNLOCK TABLES;


# Dump of table DATABASECHANGELOGLOCK
# ------------------------------------------------------------

DROP TABLE IF EXISTS `DATABASECHANGELOGLOCK`;

CREATE TABLE `DATABASECHANGELOGLOCK` (
  `ID` int(11) NOT NULL,
  `LOCKED` bit(1) NOT NULL,
  `LOCKGRANTED` datetime DEFAULT NULL,
  `LOCKEDBY` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

LOCK TABLES `DATABASECHANGELOGLOCK` WRITE;
/*!40000 ALTER TABLE `DATABASECHANGELOGLOCK` DISABLE KEYS */;

INSERT INTO `DATABASECHANGELOGLOCK` (`ID`, `LOCKED`, `LOCKGRANTED`, `LOCKEDBY`)
VALUES
	(1,b'0',NULL,NULL);

/*!40000 ALTER TABLE `DATABASECHANGELOGLOCK` ENABLE KEYS */;
UNLOCK TABLES;


# Dump of table drag_and_drop_mapping
# ------------------------------------------------------------

DROP TABLE IF EXISTS `drag_and_drop_mapping`;

CREATE TABLE `drag_and_drop_mapping` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `drag_item_index` int(11) DEFAULT NULL,
  `drop_location_index` int(11) DEFAULT NULL,
  `drag_item_id` bigint(20) DEFAULT NULL,
  `drop_location_id` bigint(20) DEFAULT NULL,
  `question_id` bigint(20) DEFAULT NULL,
  `submitted_answer_id` bigint(20) DEFAULT NULL,
  `correct_mappings_order` int(11) DEFAULT NULL,
  `invalid` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5qmi3mh5y1mt9ysht4nx34nvf` (`drag_item_id`),
  KEY `FK7p8h4scvkiilws9d5dxsdxkwy` (`question_id`),
  KEY `FKa9cfgroc0t8ujct9ux7ub1nyo` (`submitted_answer_id`),
  KEY `FKawq05c6hvxl400ouw5sg89xp9` (`drop_location_id`),
  CONSTRAINT `FK5qmi3mh5y1mt9ysht4nx34nvf` FOREIGN KEY (`drag_item_id`) REFERENCES `drag_item` (`id`),
  CONSTRAINT `FK7p8h4scvkiilws9d5dxsdxkwy` FOREIGN KEY (`question_id`) REFERENCES `quiz_question` (`id`),
  CONSTRAINT `FKa9cfgroc0t8ujct9ux7ub1nyo` FOREIGN KEY (`submitted_answer_id`) REFERENCES `submitted_answer` (`id`),
  CONSTRAINT `FKawq05c6hvxl400ouw5sg89xp9` FOREIGN KEY (`drop_location_id`) REFERENCES `drop_location` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table drag_item
# ------------------------------------------------------------

DROP TABLE IF EXISTS `drag_item`;

CREATE TABLE `drag_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `picture_file_path` varchar(255) DEFAULT NULL,
  `text` varchar(255) DEFAULT NULL,
  `question_id` bigint(20) DEFAULT NULL,
  `drag_items_order` int(11) DEFAULT NULL,
  `invalid` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6qk07o5uhre61g1egc064wmmc` (`question_id`),
  CONSTRAINT `FK6qk07o5uhre61g1egc064wmmc` FOREIGN KEY (`question_id`) REFERENCES `quiz_question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table drop_location
# ------------------------------------------------------------

DROP TABLE IF EXISTS `drop_location`;

CREATE TABLE `drop_location` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `height` int(11) DEFAULT NULL,
  `pos_x` int(11) DEFAULT NULL,
  `pos_y` int(11) DEFAULT NULL,
  `width` int(11) DEFAULT NULL,
  `question_id` bigint(20) DEFAULT NULL,
  `drop_locations_order` int(11) DEFAULT NULL,
  `invalid` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKlbu6qm8ykqhtb5xm911bg5ct7` (`question_id`),
  CONSTRAINT `FKlbu6qm8ykqhtb5xm911bg5ct7` FOREIGN KEY (`question_id`) REFERENCES `quiz_question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table example_submission
# ------------------------------------------------------------

DROP TABLE IF EXISTS `example_submission`;

CREATE TABLE `example_submission` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `used_for_tutorial` bit(1) DEFAULT NULL,
  `exercise_id` bigint(20) DEFAULT NULL,
  `submission_id` bigint(20) DEFAULT NULL,
  `tutor_participation_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_EXAMPLE_SUBMISSIONSUBMISSION_ID_COL` (`submission_id`),
  KEY `FKannvxhl5djc6qrwntuip91egx` (`exercise_id`),
  KEY `FKf7noy2n9bkw3i49drb6ic4yut` (`tutor_participation_id`),
  CONSTRAINT `FKannvxhl5djc6qrwntuip91egx` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `FKbkom4atu3covr3trrbldf6bvn` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`),
  CONSTRAINT `FKf7noy2n9bkw3i49drb6ic4yut` FOREIGN KEY (`tutor_participation_id`) REFERENCES `tutor_participation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table exercise
# ------------------------------------------------------------

DROP TABLE IF EXISTS `exercise`;

CREATE TABLE `exercise` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) DEFAULT NULL,
  `release_date` timestamp NULL DEFAULT NULL,
  `due_date` timestamp NULL DEFAULT NULL,
  `course_id` bigint(20) DEFAULT NULL,
  `publish_build_plan_url` bit(1) DEFAULT NULL,
  `allow_online_editor` bit(1) DEFAULT NULL,
  `discriminator` varchar(31) NOT NULL,
  `duration` int(11) DEFAULT NULL,
  `is_open_for_practice` bit(1) DEFAULT NULL,
  `is_planned_to_start` bit(1) DEFAULT NULL,
  `is_visible_before_start` bit(1) DEFAULT NULL,
  `allowed_number_of_attempts` int(11) DEFAULT NULL,
  `randomize_question_order` bit(1) DEFAULT NULL,
  `quiz_point_statistic_id` bigint(20) DEFAULT NULL,
  `max_score` double DEFAULT NULL,
  `diagram_type` varchar(255) DEFAULT NULL,
  `difficulty` varchar(255) DEFAULT NULL,
  `file_pattern` varchar(255) DEFAULT NULL,
  `grading_instructions` longtext,
  `problem_statement` longtext,
  `sample_solution` longtext,
  `sample_solution_explanation` longtext,
  `sample_solution_model` longtext,
  `short_name` varchar(255) DEFAULT NULL,
  `test_repository_url` varchar(255) DEFAULT NULL,
  `package_name` varchar(255) DEFAULT NULL,
  `programming_language` varchar(255) DEFAULT NULL,
  `assessment_due_date` datetime DEFAULT NULL,
  `solution_participation_id` bigint(20) DEFAULT NULL,
  `template_participation_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_EXERCISEQUIZ_POINT_STATISTIC_ID_COL` (`quiz_point_statistic_id`),
  UNIQUE KEY `UC_EXERCISESOLUTION_PARTICIPATION_ID_COL` (`solution_participation_id`),
  UNIQUE KEY `UC_EXERCISETEMPLATE_PARTICIPATION_ID_COL` (`template_participation_id`),
  KEY `fk_exercise_course_id` (`course_id`),
  CONSTRAINT `FK1kyxexi1uqeu8pw3vcbulfi42` FOREIGN KEY (`quiz_point_statistic_id`) REFERENCES `quiz_statistic` (`id`),
  CONSTRAINT `FK4ldf9949g6c5n7a79sr6rkt5n` FOREIGN KEY (`solution_participation_id`) REFERENCES `participation` (`id`),
  CONSTRAINT `FK91br4p8i0d3owf75pqxpnc3o0` FOREIGN KEY (`template_participation_id`) REFERENCES `participation` (`id`),
  CONSTRAINT `fk_exercise_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table exercise_categories
# ------------------------------------------------------------

DROP TABLE IF EXISTS `exercise_categories`;

CREATE TABLE `exercise_categories` (
  `exercise_id` bigint(20) NOT NULL,
  `categories` varchar(255) DEFAULT NULL,
  KEY `FKqdemq87pe28lexkg6kh73vueg` (`exercise_id`),
  CONSTRAINT `FKqdemq87pe28lexkg6kh73vueg` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table feedback
# ------------------------------------------------------------

DROP TABLE IF EXISTS `feedback`;

CREATE TABLE `feedback` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `detail_text` varchar(5000) DEFAULT NULL,
  `text` varchar(500) DEFAULT NULL,
  `result_id` bigint(20) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `positive` bit(1) DEFAULT NULL,
  `feedbacks_order` int(11) DEFAULT NULL,
  `credits` double DEFAULT NULL,
  `reference` varchar(2000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmaiyumo49qyke2ijh8h575yy2` (`result_id`),
  CONSTRAINT `feedback_ibfk_1` FOREIGN KEY (`result_id`) REFERENCES `result` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table jhi_authority
# ------------------------------------------------------------

DROP TABLE IF EXISTS `jhi_authority`;

CREATE TABLE `jhi_authority` (
  `name` varchar(50) NOT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

LOCK TABLES `jhi_authority` WRITE;
/*!40000 ALTER TABLE `jhi_authority` DISABLE KEYS */;

INSERT INTO `jhi_authority` (`name`)
VALUES
	('ROLE_ADMIN'),
	('ROLE_INSTRUCTOR'),
	('ROLE_TA'),
	('ROLE_USER');

/*!40000 ALTER TABLE `jhi_authority` ENABLE KEYS */;
UNLOCK TABLES;


# Dump of table jhi_date_time_wrapper
# ------------------------------------------------------------

DROP TABLE IF EXISTS `jhi_date_time_wrapper`;

CREATE TABLE `jhi_date_time_wrapper` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `instant` timestamp NULL DEFAULT NULL,
  `local_date_time` timestamp NULL DEFAULT NULL,
  `offset_date_time` timestamp NULL DEFAULT NULL,
  `zoned_date_time` timestamp NULL DEFAULT NULL,
  `local_time` time DEFAULT NULL,
  `offset_time` time DEFAULT NULL,
  `local_date` date DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table jhi_persistent_audit_event
# ------------------------------------------------------------

DROP TABLE IF EXISTS `jhi_persistent_audit_event`;

CREATE TABLE `jhi_persistent_audit_event` (
  `event_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `principal` varchar(255) NOT NULL,
  `event_date` timestamp NULL DEFAULT NULL,
  `event_type` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  KEY `idx_persistent_audit_event` (`principal`,`event_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table jhi_persistent_audit_evt_data
# ------------------------------------------------------------

DROP TABLE IF EXISTS `jhi_persistent_audit_evt_data`;

CREATE TABLE `jhi_persistent_audit_evt_data` (
  `event_id` bigint(20) NOT NULL,
  `name` varchar(255) NOT NULL,
  `value` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`event_id`,`name`),
  KEY `idx_persistent_audit_evt_data` (`event_id`),
  CONSTRAINT `fk_evt_pers_audit_evt_data` FOREIGN KEY (`event_id`) REFERENCES `jhi_persistent_audit_event` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table jhi_persistent_token
# ------------------------------------------------------------

DROP TABLE IF EXISTS `jhi_persistent_token`;

CREATE TABLE `jhi_persistent_token` (
  `series` varchar(255) NOT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  `token_value` varchar(255) NOT NULL,
  `token_date` date DEFAULT NULL,
  `ip_address` varchar(39) DEFAULT NULL,
  `user_agent` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`series`),
  KEY `fk_user_persistent_token` (`user_id`),
  CONSTRAINT `fk_user_persistent_token` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table jhi_user
# ------------------------------------------------------------

DROP TABLE IF EXISTS `jhi_user`;

CREATE TABLE `jhi_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `login` varchar(50) NOT NULL,
  `password_hash` varchar(60) DEFAULT NULL,
  `first_name` varchar(50) DEFAULT NULL,
  `last_name` varchar(50) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `activated` bit(1) NOT NULL,
  `lang_key` varchar(5) DEFAULT NULL,
  `activation_key` varchar(20) DEFAULT NULL,
  `reset_key` varchar(20) DEFAULT NULL,
  `created_by` varchar(50) DEFAULT NULL,
  `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `reset_date` timestamp NULL DEFAULT NULL,
  `last_modified_by` varchar(50) DEFAULT NULL,
  `last_modified_date` timestamp NULL DEFAULT NULL,
  `image_url` varchar(256) DEFAULT NULL,
  `last_notification_read` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_user_login` (`login`),
  UNIQUE KEY `login` (`login`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table jhi_user_authority
# ------------------------------------------------------------

DROP TABLE IF EXISTS `jhi_user_authority`;

CREATE TABLE `jhi_user_authority` (
  `user_id` bigint(20) NOT NULL,
  `authority_name` varchar(50) NOT NULL,
  PRIMARY KEY (`user_id`,`authority_name`),
  KEY `fk_authority_name` (`authority_name`),
  CONSTRAINT `fk_authority_name` FOREIGN KEY (`authority_name`) REFERENCES `jhi_authority` (`name`),
  CONSTRAINT `fk_user_id` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table lecture
# ------------------------------------------------------------

DROP TABLE IF EXISTS `lecture`;

CREATE TABLE `lecture` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `description` longtext,
  `end_date` datetime DEFAULT NULL,
  `start_date` datetime DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `course_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKjoc9yetfohpygdvx5wv385vwb` (`course_id`),
  CONSTRAINT `FKjoc9yetfohpygdvx5wv385vwb` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table lti_outcome_url
# ------------------------------------------------------------

DROP TABLE IF EXISTS `lti_outcome_url`;

CREATE TABLE `lti_outcome_url` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `url` varchar(255) DEFAULT NULL,
  `sourced_id` varchar(255) DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  `exercise_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_ltioutcomeurl_exercise_id` (`exercise_id`),
  KEY `fk_ltioutcomeurl_user_id` (`user_id`),
  CONSTRAINT `fk_ltioutcomeurl_exercise_id` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`) ON DELETE CASCADE ON UPDATE NO ACTION,
  CONSTRAINT `fk_ltioutcomeurl_user_id` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`) ON DELETE CASCADE ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table lti_user_id
# ------------------------------------------------------------

DROP TABLE IF EXISTS `lti_user_id`;

CREATE TABLE `lti_user_id` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `lti_user_id` varchar(255) DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`),
  CONSTRAINT `fk_ltiuserid_user_id` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`) ON DELETE CASCADE ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table multiple_choice_submitted_answer_selected_options
# ------------------------------------------------------------

DROP TABLE IF EXISTS `multiple_choice_submitted_answer_selected_options`;

CREATE TABLE `multiple_choice_submitted_answer_selected_options` (
  `multiple_choice_submitted_answers_id` bigint(20) NOT NULL,
  `selected_options_id` bigint(20) NOT NULL,
  PRIMARY KEY (`multiple_choice_submitted_answers_id`,`selected_options_id`),
  KEY `FK87gmes7g3ad3qf3wmx3lu0iq0` (`selected_options_id`),
  CONSTRAINT `FK87gmes7g3ad3qf3wmx3lu0iq0` FOREIGN KEY (`selected_options_id`) REFERENCES `answer_option` (`id`),
  CONSTRAINT `FKd6kco24shh09opxvn2kmi0dw5` FOREIGN KEY (`multiple_choice_submitted_answers_id`) REFERENCES `submitted_answer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table notification
# ------------------------------------------------------------

DROP TABLE IF EXISTS `notification`;

CREATE TABLE `notification` (
  `discriminator` varchar(31) NOT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `notification_date` datetime DEFAULT NULL,
  `target` varchar(255) DEFAULT NULL,
  `text` longtext,
  `title` varchar(255) DEFAULT NULL,
  `expire_date` datetime DEFAULT NULL,
  `jhi_type` varchar(255) DEFAULT NULL,
  `author_id` bigint(20) DEFAULT NULL,
  `recipient_id` bigint(20) DEFAULT NULL,
  `course_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK2qvynpew0iu557b4qk9go0u0c` (`course_id`),
  KEY `FKf3ah8uajs8wfda872mvl56hq4` (`recipient_id`),
  KEY `FKg8sxrh7ha8nl46lbljs12mtxy` (`author_id`),
  CONSTRAINT `FK2qvynpew0iu557b4qk9go0u0c` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
  CONSTRAINT `FKf3ah8uajs8wfda872mvl56hq4` FOREIGN KEY (`recipient_id`) REFERENCES `jhi_user` (`id`),
  CONSTRAINT `FKg8sxrh7ha8nl46lbljs12mtxy` FOREIGN KEY (`author_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table participation
# ------------------------------------------------------------

DROP TABLE IF EXISTS `participation`;

CREATE TABLE `participation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `repository_url` varchar(255) DEFAULT NULL,
  `build_plan_id` varchar(255) DEFAULT NULL,
  `student_id` bigint(20) DEFAULT NULL,
  `exercise_id` bigint(20) DEFAULT NULL,
  `initialization_state` varchar(255) DEFAULT NULL,
  `initialization_date` datetime(6) DEFAULT NULL,
  `presentation_score` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6skuc03gvp2gw5lyji5lu0v0v` (`student_id`,`exercise_id`,`initialization_state`),
  KEY `fk_participation_exercise_id` (`exercise_id`),
  KEY `fk_participation_student_id` (`student_id`),
  CONSTRAINT `fk_participation_exercise_id` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `fk_participation_student_id` FOREIGN KEY (`student_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table quiz_question
# ------------------------------------------------------------

DROP TABLE IF EXISTS `quiz_question`;

CREATE TABLE `quiz_question` (
  `discriminator` varchar(31) NOT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `text` varchar(1000) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `background_file_path` varchar(255) DEFAULT NULL,
  `explanation` varchar(255) DEFAULT NULL,
  `hint` varchar(255) DEFAULT NULL,
  `randomize_order` bit(1) DEFAULT NULL,
  `score` int(11) DEFAULT NULL,
  `scoring_type` varchar(255) DEFAULT NULL,
  `exercise_id` bigint(20) DEFAULT NULL,
  `quiz_questions_order` bigint(20) DEFAULT NULL,
  `quiz_question_statistic_id` bigint(20) DEFAULT NULL,
  `invalid` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_QUESTIONQUESTION_STATISTIC_ID_COL` (`quiz_question_statistic_id`),
  KEY `FKcw89k10abejup8p3hif0kjpw` (`exercise_id`),
  CONSTRAINT `FKcw89k10abejup8p3hif0kjpw` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `FKoi0sqphft8m7f92qa7ui8i3ac` FOREIGN KEY (`quiz_question_statistic_id`) REFERENCES `quiz_statistic` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table quiz_statistic
# ------------------------------------------------------------

DROP TABLE IF EXISTS `quiz_statistic`;

CREATE TABLE `quiz_statistic` (
  `discriminator` varchar(31) NOT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `participants_rated` int(11) DEFAULT NULL,
  `participants_unrated` int(11) DEFAULT NULL,
  `rated_correct_counter` int(11) DEFAULT NULL,
  `un_rated_correct_counter` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table quiz_statistic_counter
# ------------------------------------------------------------

DROP TABLE IF EXISTS `quiz_statistic_counter`;

CREATE TABLE `quiz_statistic_counter` (
  `discriminator` varchar(31) NOT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `rated_counter` int(11) DEFAULT NULL,
  `un_rated_counter` int(11) DEFAULT NULL,
  `points` double DEFAULT NULL,
  `drag_and_drop_question_statistic_id` bigint(20) DEFAULT NULL,
  `drop_location_id` bigint(20) DEFAULT NULL,
  `quiz_point_statistic_id` bigint(20) DEFAULT NULL,
  `answer_id` bigint(20) DEFAULT NULL,
  `multiple_choice_question_statistic_id` bigint(20) DEFAULT NULL,
  `short_answer_question_statistic_id` bigint(20) DEFAULT NULL,
  `spot_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_STATISTIC_COUNTERANSWER_ID_COL` (`answer_id`),
  UNIQUE KEY `UC_STATISTIC_COUNTERDROP_LOCATION_ID_COL` (`drop_location_id`),
  UNIQUE KEY `UC_STATISTIC_COUNTERSPOT_ID_COL` (`spot_id`),
  KEY `FK11jgyics37wyibhhpp866exa8` (`drag_and_drop_question_statistic_id`),
  KEY `FK3bqk45run356e20sqmf3sak1o` (`quiz_point_statistic_id`),
  KEY `FKoh69hhnfcimvw7i1cwhepvt5g` (`multiple_choice_question_statistic_id`),
  KEY `FKhascwjyvee25c0k3tay04ghog` (`short_answer_question_statistic_id`),
  CONSTRAINT `FK11jgyics37wyibhhpp866exa8` FOREIGN KEY (`drag_and_drop_question_statistic_id`) REFERENCES `quiz_statistic` (`id`),
  CONSTRAINT `FK2bses6ev8komaj0jw4gcyl8te` FOREIGN KEY (`drop_location_id`) REFERENCES `drop_location` (`id`),
  CONSTRAINT `FK3bqk45run356e20sqmf3sak1o` FOREIGN KEY (`quiz_point_statistic_id`) REFERENCES `quiz_statistic` (`id`),
  CONSTRAINT `FKg7hjug3wu6icklf6gbiqs4n18` FOREIGN KEY (`answer_id`) REFERENCES `answer_option` (`id`),
  CONSTRAINT `FKhascwjyvee25c0k3tay04ghog` FOREIGN KEY (`short_answer_question_statistic_id`) REFERENCES `quiz_statistic` (`id`),
  CONSTRAINT `FKoh69hhnfcimvw7i1cwhepvt5g` FOREIGN KEY (`multiple_choice_question_statistic_id`) REFERENCES `quiz_statistic` (`id`),
  CONSTRAINT `FKoqgu1clyd02qbo86silw1uhmk` FOREIGN KEY (`spot_id`) REFERENCES `short_answer_spot` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table result
# ------------------------------------------------------------

DROP TABLE IF EXISTS `result`;

CREATE TABLE `result` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `result_string` varchar(255) DEFAULT NULL,
  `completion_date` datetime(6) DEFAULT NULL,
  `jhi_successful` bit(1) DEFAULT NULL,
  `participation_id` bigint(20) DEFAULT NULL,
  `score` bigint(20) DEFAULT NULL,
  `build_artifact` bit(1) DEFAULT NULL,
  `submission_id` bigint(20) DEFAULT NULL,
  `rated` bit(1) DEFAULT NULL,
  `has_feedback` bit(1) DEFAULT NULL,
  `assessment_type` varchar(255) DEFAULT NULL,
  `assessor_id` bigint(20) DEFAULT NULL,
  `example_result` bit(1) DEFAULT NULL,
  `has_complaint` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_RESULTSUBMISSION_ID_COL` (`submission_id`),
  KEY `FKkkdvousfa95ueh46xg741fadg` (`assessor_id`),
  KEY `fk_result_participation_id` (`participation_id`),
  CONSTRAINT `FK3vct9sad5oubthdmq63n58mnp` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`),
  CONSTRAINT `FKkkdvousfa95ueh46xg741fadg` FOREIGN KEY (`assessor_id`) REFERENCES `jhi_user` (`id`),
  CONSTRAINT `fk_result_participation_id` FOREIGN KEY (`participation_id`) REFERENCES `participation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table short_answer_mapping
# ------------------------------------------------------------

DROP TABLE IF EXISTS `short_answer_mapping`;

CREATE TABLE `short_answer_mapping` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `invalid` bit(1) DEFAULT NULL,
  `short_answer_solution_index` int(11) DEFAULT NULL,
  `short_answer_spot_index` int(11) DEFAULT NULL,
  `question_id` bigint(20) DEFAULT NULL,
  `solution_id` bigint(20) DEFAULT NULL,
  `spot_id` bigint(20) DEFAULT NULL,
  `correct_mappings_order` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK4ehrp6xueea0pyiiidtuouc0s` (`question_id`),
  KEY `FK5opt512154y0g5llppcaqe19e` (`spot_id`),
  KEY `FKbiq7gp760m9y8xpycnguw78dp` (`solution_id`),
  CONSTRAINT `FK4ehrp6xueea0pyiiidtuouc0s` FOREIGN KEY (`question_id`) REFERENCES `quiz_question` (`id`),
  CONSTRAINT `FK5opt512154y0g5llppcaqe19e` FOREIGN KEY (`spot_id`) REFERENCES `short_answer_spot` (`id`),
  CONSTRAINT `FKbiq7gp760m9y8xpycnguw78dp` FOREIGN KEY (`solution_id`) REFERENCES `short_answer_solution` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table short_answer_solution
# ------------------------------------------------------------

DROP TABLE IF EXISTS `short_answer_solution`;

CREATE TABLE `short_answer_solution` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `invalid` bit(1) DEFAULT NULL,
  `text` varchar(255) DEFAULT NULL,
  `question_id` bigint(20) DEFAULT NULL,
  `solutions_order` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKejag8le5jrd03enkmhxircugx` (`question_id`),
  CONSTRAINT `FKejag8le5jrd03enkmhxircugx` FOREIGN KEY (`question_id`) REFERENCES `quiz_question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table short_answer_spot
# ------------------------------------------------------------

DROP TABLE IF EXISTS `short_answer_spot`;

CREATE TABLE `short_answer_spot` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `invalid` bit(1) DEFAULT NULL,
  `width` int(11) DEFAULT NULL,
  `question_id` bigint(20) DEFAULT NULL,
  `spots_order` int(11) DEFAULT NULL,
  `spot_nr` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK2kwul05ckvpi145gc0bmf7obh` (`question_id`),
  CONSTRAINT `FK2kwul05ckvpi145gc0bmf7obh` FOREIGN KEY (`question_id`) REFERENCES `quiz_question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table short_answer_submitted_text
# ------------------------------------------------------------

DROP TABLE IF EXISTS `short_answer_submitted_text`;

CREATE TABLE `short_answer_submitted_text` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `text` varchar(255) DEFAULT NULL,
  `spot_id` bigint(20) DEFAULT NULL,
  `submitted_answer_id` bigint(20) DEFAULT NULL,
  `is_correct` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKkp1tfjoaawuiskayomp85e0lj` (`submitted_answer_id`),
  KEY `FKpkb6e1yjqhma5tgvabb9smyv4` (`spot_id`),
  CONSTRAINT `FKkp1tfjoaawuiskayomp85e0lj` FOREIGN KEY (`submitted_answer_id`) REFERENCES `submitted_answer` (`id`),
  CONSTRAINT `FKpkb6e1yjqhma5tgvabb9smyv4` FOREIGN KEY (`spot_id`) REFERENCES `short_answer_spot` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table student_question
# ------------------------------------------------------------

DROP TABLE IF EXISTS `student_question`;

CREATE TABLE `student_question` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `creation_date` datetime DEFAULT NULL,
  `question_text` varchar(1000) DEFAULT NULL,
  `visible_for_students` bit(1) DEFAULT NULL,
  `author_id` bigint(20) DEFAULT NULL,
  `exercise_id` bigint(20) DEFAULT NULL,
  `lecture_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6kgo0sse7sait8q665s16wynf` (`exercise_id`),
  KEY `FKcu2dpdk9qef0yaithkxcjebwg` (`lecture_id`),
  KEY `FKjpgnpph3i2h0n8xekqiytaers` (`author_id`),
  CONSTRAINT `FK6kgo0sse7sait8q665s16wynf` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `FKcu2dpdk9qef0yaithkxcjebwg` FOREIGN KEY (`lecture_id`) REFERENCES `lecture` (`id`),
  CONSTRAINT `FKjpgnpph3i2h0n8xekqiytaers` FOREIGN KEY (`author_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table student_question_answer
# ------------------------------------------------------------

DROP TABLE IF EXISTS `student_question_answer`;

CREATE TABLE `student_question_answer` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `answer_date` datetime DEFAULT NULL,
  `answer_text` longtext,
  `verified` bit(1) DEFAULT NULL,
  `author_id` bigint(20) DEFAULT NULL,
  `question_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKfe9vnvra5xx2kulv4h27seksa` (`question_id`),
  KEY `FKgdd6qiyjtlrjgf2dsse32r8y5` (`author_id`),
  CONSTRAINT `FKfe9vnvra5xx2kulv4h27seksa` FOREIGN KEY (`question_id`) REFERENCES `student_question` (`id`),
  CONSTRAINT `FKgdd6qiyjtlrjgf2dsse32r8y5` FOREIGN KEY (`author_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table submission
# ------------------------------------------------------------

DROP TABLE IF EXISTS `submission`;

CREATE TABLE `submission` (
  `discriminator` varchar(31) NOT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `jhi_type` varchar(255) DEFAULT NULL,
  `submitted` bit(1) DEFAULT NULL,
  `score_in_points` double DEFAULT NULL,
  `commit_hash` varchar(255) DEFAULT NULL,
  `participation_id` bigint(20) DEFAULT NULL,
  `submission_date` datetime(6) DEFAULT NULL,
  `file_path` varchar(255) DEFAULT NULL,
  `text` longtext,
  `explanation_text` longtext,
  `example_submission` bit(1) DEFAULT NULL,
  `model` longtext,
  PRIMARY KEY (`id`),
  KEY `FKeap67y7pxd6ffvwbs6d6sfhmq` (`participation_id`),
  CONSTRAINT `FKeap67y7pxd6ffvwbs6d6sfhmq` FOREIGN KEY (`participation_id`) REFERENCES `participation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table submitted_answer
# ------------------------------------------------------------

DROP TABLE IF EXISTS `submitted_answer`;

CREATE TABLE `submitted_answer` (
  `discriminator` varchar(31) NOT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `submission_id` bigint(20) DEFAULT NULL,
  `quiz_question_id` bigint(20) DEFAULT NULL,
  `score_in_points` double DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6drbcvqf26cahd0j1u9ff0rtr` (`quiz_question_id`),
  KEY `FKfwgc3a5vd0cxt5jsvvxm2tstf` (`submission_id`),
  CONSTRAINT `FK6drbcvqf26cahd0j1u9ff0rtr` FOREIGN KEY (`quiz_question_id`) REFERENCES `quiz_question` (`id`),
  CONSTRAINT `FKfwgc3a5vd0cxt5jsvvxm2tstf` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table tutor_group
# ------------------------------------------------------------

DROP TABLE IF EXISTS `tutor_group`;

CREATE TABLE `tutor_group` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `capacity` int(11) DEFAULT NULL,
  `language` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `room` varchar(255) DEFAULT NULL,
  `time_slot` varchar(255) DEFAULT NULL,
  `weekday` varchar(255) DEFAULT NULL,
  `course_id` bigint(20) DEFAULT NULL,
  `tutor_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKcf8nhxegaw4eemea12s6q6p5j` (`tutor_id`),
  KEY `FKhlflc2uq6jxpt1ajaru95ud2c` (`course_id`),
  CONSTRAINT `FKcf8nhxegaw4eemea12s6q6p5j` FOREIGN KEY (`tutor_id`) REFERENCES `jhi_user` (`id`),
  CONSTRAINT `FKhlflc2uq6jxpt1ajaru95ud2c` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table tutor_group_students
# ------------------------------------------------------------

DROP TABLE IF EXISTS `tutor_group_students`;

CREATE TABLE `tutor_group_students` (
  `tutor_group_id` bigint(20) NOT NULL,
  `students_id` bigint(20) NOT NULL,
  PRIMARY KEY (`tutor_group_id`,`students_id`),
  KEY `FK3kx69s41qyv7k9s10ia5im91n` (`students_id`),
  CONSTRAINT `FK2cvxih0qjqys4ld9gdol39ngj` FOREIGN KEY (`tutor_group_id`) REFERENCES `tutor_group` (`id`),
  CONSTRAINT `FK3kx69s41qyv7k9s10ia5im91n` FOREIGN KEY (`students_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table tutor_participation
# ------------------------------------------------------------

DROP TABLE IF EXISTS `tutor_participation`;

CREATE TABLE `tutor_participation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `points` int(11) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `assessed_exercise_id` bigint(20) DEFAULT NULL,
  `tutor_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_tutor_participation_exercise_id` (`assessed_exercise_id`),
  KEY `fk_tutor_participation_student_id` (`tutor_id`),
  CONSTRAINT `fk_tutor_participation_exercise_id` FOREIGN KEY (`assessed_exercise_id`) REFERENCES `exercise` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_tutor_participation_student_id` FOREIGN KEY (`tutor_id`) REFERENCES `jhi_user` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table user_groups
# ------------------------------------------------------------

DROP TABLE IF EXISTS `user_groups`;

CREATE TABLE `user_groups` (
  `user_id` bigint(20) NOT NULL,
  `groups` varchar(255) DEFAULT NULL,
  KEY `FK_qhsd8ovn89o4usyr3fgaqkdjt` (`user_id`),
  CONSTRAINT `FK_qhsd8ovn89o4usyr3fgaqkdjt` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;




/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
