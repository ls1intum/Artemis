# ************************************************************
# Sequel Pro SQL dump
# Version 4541
#
# http://www.sequelpro.com/
# https://github.com/sequelpro/sequelpro
#
# Host: 127.0.0.1 (MySQL 5.7.23)
# Database: ArTEMiS
# Generation Time: 2018-10-19 08:29:10 +0000
# ************************************************************


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
  CONSTRAINT `FKfqeqisl0e28xp3yn9bmlgkhej` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
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



# Dump of table course
# ------------------------------------------------------------

DROP TABLE IF EXISTS `course`;

CREATE TABLE `course` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) DEFAULT NULL,
  `student_group_name` varchar(255) DEFAULT NULL,
  `teaching_assistant_group_name` varchar(255) DEFAULT NULL,
  `end_date` datetime DEFAULT NULL,
  `instructor_group_name` varchar(255) DEFAULT NULL,
  `online_course` bit(1) DEFAULT NULL,
  `start_date` datetime DEFAULT NULL,
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
	('00000000000001','krusche','classpath:config/liquibase/changelog/00000000000000_initial_schema.xml','2018-10-19 10:28:09',1,'EXECUTED','7:0ed67ee32ef8b3bd04a1bcfbe7107762','createTable tableName=answer_option; createTable tableName=apollon_diagram; createTable tableName=course; createTable tableName=drag_and_drop_mapping; createTable tableName=drag_item; createTable tableName=drop_location; createTable tableName=exer...','',NULL,'3.5.4',NULL,NULL,'9937688449');

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
  CONSTRAINT `FK5qmi3mh5y1mt9ysht4nx34nvf` FOREIGN KEY (`drag_item_id`) REFERENCES `drag_item` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK7p8h4scvkiilws9d5dxsdxkwy` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKa9cfgroc0t8ujct9ux7ub1nyo` FOREIGN KEY (`submitted_answer_id`) REFERENCES `submitted_answer` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKawq05c6hvxl400ouw5sg89xp9` FOREIGN KEY (`drop_location_id`) REFERENCES `drop_location` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  CONSTRAINT `FK6qk07o5uhre61g1egc064wmmc` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  CONSTRAINT `FKlbu6qm8ykqhtb5xm911bg5ct7` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table exercise
# ------------------------------------------------------------

DROP TABLE IF EXISTS `exercise`;

CREATE TABLE `exercise` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) DEFAULT NULL,
  `base_repository_url` varchar(255) DEFAULT NULL,
  `base_build_plan_id` varchar(255) DEFAULT NULL,
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
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_EXERCISEQUIZ_POINT_STATISTIC_ID_COL` (`quiz_point_statistic_id`),
  KEY `fk_exercise_course_id` (`course_id`),
  CONSTRAINT `FK1kyxexi1uqeu8pw3vcbulfi42` FOREIGN KEY (`quiz_point_statistic_id`) REFERENCES `statistic` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_exercise_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table exercise_categories
# ------------------------------------------------------------

DROP TABLE IF EXISTS `exercise_categories`;

CREATE TABLE `exercise_categories` (
  `exercise_id` bigint(20) NOT NULL,
  `categories` varchar(255) DEFAULT NULL,
  KEY `FKqdemq87pe28lexkg6kh73vueg` (`exercise_id`),
  CONSTRAINT `FKqdemq87pe28lexkg6kh73vueg` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table feedback
# ------------------------------------------------------------

DROP TABLE IF EXISTS `feedback`;

CREATE TABLE `feedback` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `detail_text` varchar(5000) DEFAULT NULL,
  `text` varchar(255) DEFAULT NULL,
  `result_id` bigint(20) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `positive` bit(1) DEFAULT NULL,
  `feedbacks_order` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmaiyumo49qyke2ijh8h575yy2` (`result_id`),
  CONSTRAINT `feedback_ibfk_1` FOREIGN KEY (`result_id`) REFERENCES `result` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  CONSTRAINT `fk_evt_pers_audit_evt_data` FOREIGN KEY (`event_id`) REFERENCES `jhi_persistent_audit_event` (`event_id`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  CONSTRAINT `fk_user_persistent_token` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  `created_by` varchar(50) NOT NULL,
  `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `reset_date` timestamp NULL DEFAULT NULL,
  `last_modified_by` varchar(50) DEFAULT NULL,
  `last_modified_date` timestamp NULL DEFAULT NULL,
  `image_url` varchar(256) DEFAULT NULL,
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
  CONSTRAINT `fk_authority_name` FOREIGN KEY (`authority_name`) REFERENCES `jhi_authority` (`name`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_user_id` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  CONSTRAINT `FK87gmes7g3ad3qf3wmx3lu0iq0` FOREIGN KEY (`selected_options_id`) REFERENCES `answer_option` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKd6kco24shh09opxvn2kmi0dw5` FOREIGN KEY (`multiple_choice_submitted_answers_id`) REFERENCES `submitted_answer` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  `initialization_date` datetime DEFAULT NULL,
  `presentation_score` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6skuc03gvp2gw5lyji5lu0v0v` (`student_id`,`exercise_id`,`initialization_state`),
  KEY `fk_participation_exercise_id` (`exercise_id`),
  KEY `fk_participation_student_id` (`student_id`),
  CONSTRAINT `fk_participation_exercise_id` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_participation_student_id` FOREIGN KEY (`student_id`) REFERENCES `jhi_user` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table question
# ------------------------------------------------------------

DROP TABLE IF EXISTS `question`;

CREATE TABLE `question` (
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
  `questions_order` int(11) DEFAULT NULL,
  `question_statistic_id` bigint(20) DEFAULT NULL,
  `invalid` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_QUESTIONQUESTION_STATISTIC_ID_COL` (`question_statistic_id`),
  KEY `FKcw89k10abejup8p3hif0kjpw` (`exercise_id`),
  CONSTRAINT `FKcw89k10abejup8p3hif0kjpw` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKoi0sqphft8m7f92qa7ui8i3ac` FOREIGN KEY (`question_statistic_id`) REFERENCES `statistic` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table result
# ------------------------------------------------------------

DROP TABLE IF EXISTS `result`;

CREATE TABLE `result` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `result_string` varchar(255) DEFAULT NULL,
  `completion_date` datetime DEFAULT NULL,
  `jhi_successful` bit(1) DEFAULT NULL,
  `participation_id` bigint(20) DEFAULT NULL,
  `score` bigint(20) DEFAULT NULL,
  `build_artifact` bit(1) DEFAULT NULL,
  `submission_id` bigint(20) DEFAULT NULL,
  `rated` bit(1) DEFAULT NULL,
  `has_feedback` bit(1) DEFAULT NULL,
  `assessment_type` varchar(255) DEFAULT NULL,
  `assessor_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_RESULTSUBMISSION_ID_COL` (`submission_id`),
  KEY `FKkkdvousfa95ueh46xg741fadg` (`assessor_id`),
  KEY `fk_result_participation_id` (`participation_id`),
  CONSTRAINT `FK3vct9sad5oubthdmq63n58mnp` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKkkdvousfa95ueh46xg741fadg` FOREIGN KEY (`assessor_id`) REFERENCES `jhi_user` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_result_participation_id` FOREIGN KEY (`participation_id`) REFERENCES `participation` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table statistic
# ------------------------------------------------------------

DROP TABLE IF EXISTS `statistic`;

CREATE TABLE `statistic` (
  `discriminator` varchar(31) NOT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `participants_rated` int(11) DEFAULT NULL,
  `participants_unrated` int(11) DEFAULT NULL,
  `released` bit(1) DEFAULT NULL,
  `rated_correct_counter` int(11) DEFAULT NULL,
  `un_rated_correct_counter` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table statistic_counter
# ------------------------------------------------------------

DROP TABLE IF EXISTS `statistic_counter`;

CREATE TABLE `statistic_counter` (
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
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_STATISTIC_COUNTERANSWER_ID_COL` (`answer_id`),
  UNIQUE KEY `UC_STATISTIC_COUNTERDROP_LOCATION_ID_COL` (`drop_location_id`),
  KEY `FK11jgyics37wyibhhpp866exa8` (`drag_and_drop_question_statistic_id`),
  KEY `FK3bqk45run356e20sqmf3sak1o` (`quiz_point_statistic_id`),
  KEY `FKoh69hhnfcimvw7i1cwhepvt5g` (`multiple_choice_question_statistic_id`),
  CONSTRAINT `FK11jgyics37wyibhhpp866exa8` FOREIGN KEY (`drag_and_drop_question_statistic_id`) REFERENCES `statistic` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK2bses6ev8komaj0jw4gcyl8te` FOREIGN KEY (`drop_location_id`) REFERENCES `drop_location` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK3bqk45run356e20sqmf3sak1o` FOREIGN KEY (`quiz_point_statistic_id`) REFERENCES `statistic` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKg7hjug3wu6icklf6gbiqs4n18` FOREIGN KEY (`answer_id`) REFERENCES `answer_option` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKoh69hhnfcimvw7i1cwhepvt5g` FOREIGN KEY (`multiple_choice_question_statistic_id`) REFERENCES `statistic` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  `submission_date` datetime DEFAULT NULL,
  `file_path` varchar(255) DEFAULT NULL,
  `text` longtext,
  `explanation_text` longtext,
  PRIMARY KEY (`id`),
  KEY `FKeap67y7pxd6ffvwbs6d6sfhmq` (`participation_id`),
  CONSTRAINT `FKeap67y7pxd6ffvwbs6d6sfhmq` FOREIGN KEY (`participation_id`) REFERENCES `participation` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table submitted_answer
# ------------------------------------------------------------

DROP TABLE IF EXISTS `submitted_answer`;

CREATE TABLE `submitted_answer` (
  `discriminator` varchar(31) NOT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `submission_id` bigint(20) DEFAULT NULL,
  `question_id` bigint(20) DEFAULT NULL,
  `score_in_points` double DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6drbcvqf26cahd0j1u9ff0rtr` (`question_id`),
  KEY `FKfwgc3a5vd0cxt5jsvvxm2tstf` (`submission_id`),
  CONSTRAINT `FK6drbcvqf26cahd0j1u9ff0rtr` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKfwgc3a5vd0cxt5jsvvxm2tstf` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table user_groups
# ------------------------------------------------------------

DROP TABLE IF EXISTS `user_groups`;

CREATE TABLE `user_groups` (
  `user_id` bigint(20) NOT NULL,
  `groups` varchar(255) DEFAULT NULL,
  KEY `FK_qhsd8ovn89o4usyr3fgaqkdjt` (`user_id`),
  CONSTRAINT `FK_qhsd8ovn89o4usyr3fgaqkdjt` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8;




/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
