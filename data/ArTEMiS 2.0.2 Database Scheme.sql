# ************************************************************
# Sequel Pro SQL dump
# Version 4541
#
# http://www.sequelpro.com/
# https://github.com/sequelpro/sequelpro
#
# Host: 127.0.0.1 (MySQL 5.7.21)
# Database: ArTEMiS
# Generation Time: 2018-07-02 06:08:24 +0000
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
  CONSTRAINT `FKfqeqisl0e28xp3yn9bmlgkhej` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`)
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
	('00000000000001','jhipster','classpath:config/liquibase/changelog/00000000000000_initial_schema.xml','2018-07-02 09:07:07',1,'EXECUTED','7:8ac6da1602c7ac4479634c67959fc402','createTable tableName=jhi_user; createIndex indexName=idx_user_login, tableName=jhi_user; createIndex indexName=idx_user_email, tableName=jhi_user; createTable tableName=jhi_authority; createTable tableName=jhi_user_authority; addPrimaryKey tableN...','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('20160609093907-1','jhipster','classpath:config/liquibase/changelog/20160609093907_added_entity_Course.xml','2018-07-02 09:07:07',2,'EXECUTED','7:77835a8f9babae07551873268992b6d2','createTable tableName=course','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('20160609094621-1','jhipster','classpath:config/liquibase/changelog/20160609094621_added_entity_Exercise.xml','2018-07-02 09:07:07',3,'EXECUTED','7:3775648692a2b2fd358b8f11e823eba8','createTable tableName=exercise; dropDefaultValue columnName=release_date, tableName=exercise; dropDefaultValue columnName=due_date, tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('20160609100233-1','jhipster','classpath:config/liquibase/changelog/20160609100233_added_entity_Participation.xml','2018-07-02 09:07:07',4,'EXECUTED','7:84f96ea14e7bb1e7cb30efcc7cd35f0b','createTable tableName=participation','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('20160609101026-1','jhipster','classpath:config/liquibase/changelog/20160609101026_added_entity_Result.xml','2018-07-02 09:07:07',5,'EXECUTED','7:d607a3ad79fc7367ba3b67fd66a1ccaf','createTable tableName=result; dropDefaultValue columnName=build_completion_date, tableName=result','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('20161002111935-1','josiasmontag (jhipster)','classpath:config/liquibase/changelog/20161002111935_added_entity_LtiOutcomeUrl.xml','2018-07-02 09:07:07',6,'EXECUTED','7:f9fedde3b8074685960f345ae6b815b3','createTable tableName=lti_outcome_url','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('20161123125152-1','josiasmontag (jhipster)','classpath:config/liquibase/changelog/20161123125152_added_entity_LtiUserId.xml','2018-07-02 09:07:07',7,'EXECUTED','7:2d85cfb5b4241d73f44aa2ba061d7b2f','createTable tableName=lti_user_id','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('20160609094621-2','jhipster','classpath:config/liquibase/changelog/20160609094621_added_entity_constraints_Exercise.xml','2018-07-02 09:07:07',8,'EXECUTED','7:3f738faf8a43344869ce8bb6001d2275','addForeignKeyConstraint baseTableName=exercise, constraintName=fk_exercise_course_id, referencedTableName=course','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('20160609100233-2','jhipster','classpath:config/liquibase/changelog/20160609100233_added_entity_constraints_Participation.xml','2018-07-02 09:07:07',9,'EXECUTED','7:25b42527300e836c5d318f6555536f1b','addForeignKeyConstraint baseTableName=participation, constraintName=fk_participation_student_id, referencedTableName=jhi_user; addForeignKeyConstraint baseTableName=participation, constraintName=fk_participation_exercise_id, referencedTableName=ex...','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('20160609101026-2','jhipster','classpath:config/liquibase/changelog/20160609101026_added_entity_constraints_Result.xml','2018-07-02 09:07:07',10,'EXECUTED','7:3a761188d6b451fc7e05f34d78eb2ff9','addForeignKeyConstraint baseTableName=result, constraintName=fk_result_participation_id, referencedTableName=participation','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('20161002111935-2','josiasmontag (jhipster)','classpath:config/liquibase/changelog/20161002111935_added_entity_constraints_LtiOutcomeUrl.xml','2018-07-02 09:07:07',11,'EXECUTED','7:ea355ae572dab30578915114baebda03','addForeignKeyConstraint baseTableName=lti_outcome_url, constraintName=fk_ltioutcomeurl_user_id, referencedTableName=jhi_user; addForeignKeyConstraint baseTableName=lti_outcome_url, constraintName=fk_ltioutcomeurl_exercise_id, referencedTableName=e...','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('20161123125152-2','josiasmontag (jhipster)','classpath:config/liquibase/changelog/20161123125152_added_entity_constraints_LtiUserId.xml','2018-07-02 09:07:07',12,'EXECUTED','7:5eafff2b67f3bb193d8bdfe029358c3c','addForeignKeyConstraint baseTableName=lti_user_id, constraintName=fk_ltiuserid_user_id, referencedTableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1466504319728-1','muenchdo (generated)','classpath:config/liquibase/changelog/20160621121620_changelog.xml','2018-07-02 09:07:07',13,'EXECUTED','7:2d58817a41ba1c894d2b063d813c772d','dropColumn columnName=slug, tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1466588153067-1','muenchdo (generated)','classpath:config/liquibase/changelog/20160622113437_changelog.xml','2018-07-02 09:07:07',14,'EXECUTED','7:96bbf9befb9a10401d61d8356c11f911','addColumn tableName=participation','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1466840103721-1','muenchdo (generated)','classpath:config/liquibase/changelog/20160625093352_changelog.xml','2018-07-02 09:07:07',15,'EXECUTED','7:68bc49b138a1ad1df0cdda10b87b771a','createTable tableName=user_groups','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1466840103721-2','muenchdo (generated)','classpath:config/liquibase/changelog/20160625093352_changelog.xml','2018-07-02 09:07:07',16,'EXECUTED','7:77611bdf15450c960760592caa0d5cf6','addForeignKeyConstraint baseTableName=user_groups, constraintName=FK_qhsd8ovn89o4usyr3fgaqkdjt, referencedTableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1466850697366-1','muenchdo (generated)','classpath:config/liquibase/changelog/20160625123038_changelog.xml','2018-07-02 09:07:08',17,'EXECUTED','7:5218ea65a81d93e8a59b42d22a16a7c2','addColumn tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1466948677623-1','muenchdo (generated)','classpath:config/liquibase/changelog/20160626154416_changelog.xml','2018-07-02 09:07:08',18,'EXECUTED','7:6097b9de2da9395d9edcbda15934bc6b','addColumn tableName=participation','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1474917132322-1','muenchdo (generated)','classpath:config/liquibase/changelog/20160926211124_changelog.xml','2018-07-02 09:07:08',19,'EXECUTED','7:0571a1d8c7c735406473e5fc3caeca2e','addColumn tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1474917132322-2','muenchdo (generated)','classpath:config/liquibase/changelog/20160926211124_changelog.xml','2018-07-02 09:07:08',20,'EXECUTED','7:d8cc22d152a5640da3a11e06e0310882','addColumn tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1474917132322-3','muenchdo (generated)','classpath:config/liquibase/changelog/20160926211124_changelog.xml','2018-07-02 09:07:08',21,'EXECUTED','7:04d5e980bee8c94d1f83efc14a7896ca','addColumn tableName=participation','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1474917132322-4','muenchdo (generated)','classpath:config/liquibase/changelog/20160926211124_changelog.xml','2018-07-02 09:07:08',22,'EXECUTED','7:e8d3669cadc9a2ea48ddb478cf0960d1','dropColumn columnName=base_build_plan_slug, tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1474917132322-5','muenchdo (generated)','classpath:config/liquibase/changelog/20160926211124_changelog.xml','2018-07-02 09:07:08',23,'EXECUTED','7:8c99d58bc0651006713191f3cdb8c3d9','dropColumn columnName=base_project_key, tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1474917132322-6','muenchdo (generated)','classpath:config/liquibase/changelog/20160926211124_changelog.xml','2018-07-02 09:07:08',24,'EXECUTED','7:7390434e8e57acdfa122a438ccd5fb82','dropColumn columnName=base_repository_slug, tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1475007248412-1','muenchdo (generated)','classpath:config/liquibase/changelog/20160927221337_changelog.xml','2018-07-02 09:07:08',25,'EXECUTED','7:310d849a771a02309a7eedffec736b03','addColumn tableName=participation','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1475007248412-2','muenchdo (generated)','classpath:config/liquibase/changelog/20160927221337_changelog.xml','2018-07-02 09:07:08',26,'EXECUTED','7:0ca7414fcf0f8e3f0f4f821916b57846','dropColumn columnName=clone_url, tableName=participation','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1475007248412-3','muenchdo (generated)','classpath:config/liquibase/changelog/20160927221337_changelog.xml','2018-07-02 09:07:08',27,'EXECUTED','7:30b621f43a472954a45298e04f495139','dropColumn columnName=repository_slug, tableName=participation','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1475757066885-2','josiasmontag (generated)','classpath:config/liquibase/changelog/20161006142956_changelog.xml','2018-07-02 09:07:08',28,'EXECUTED','7:6e53e0b30b8510256eb16a3df1a88637','dropUniqueConstraint constraintName=idx_user_email, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1476886675461-1','muenchdo (generated)','classpath:config/liquibase/changelog/20161019161722_changelog.xml','2018-07-02 09:07:08',29,'EXECUTED','7:a692eace7cdb18599e7399666bcdd1b3','addColumn tableName=course','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1477563464535-1','monday (generated)','classpath:config/liquibase/changelog/20161027121730_changelog.xml','2018-07-02 09:07:08',30,'EXECUTED','7:5daff93680b648887f4aed9a4b018710','addColumn tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('20161227132122-1','josiasmontag (generated)','classpath:config/liquibase/changelog/20161227132122_changelog.xml','2018-07-02 09:07:08',31,'EXECUTED','7:f857a5eb68da756820a53c26af2eedd3','addColumn tableName=result','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('20170111171600-1','josiasmontag (generated)','classpath:config/liquibase/changelog/20170111171600_changelog.xml','2018-07-02 09:07:08',32,'EXECUTED','7:57cc6ba68ee0ae10494b7b62520cb228','addColumn tableName=result','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-1','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',33,'EXECUTED','7:9ad6dbc4fc19e218001c432920bcc840','createTable tableName=answer_option','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-2','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',34,'EXECUTED','7:0db1daa5cd22635bdaa16921e4b67802','createTable tableName=drag_and_drop_assignment','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-3','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',35,'EXECUTED','7:c380c0dbe3647b1770aaafed9afeb572','createTable tableName=drag_item','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-4','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',36,'EXECUTED','7:1fb6e2927255d791e9fc6e6793ae9425','createTable tableName=drop_location','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-5','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',37,'EXECUTED','7:96e536a438f7dd018553212e34c8652a','createTable tableName=multiple_choice_submitted_answer_selected_options','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-6','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',38,'EXECUTED','7:ad375c4f28b4e9472a8d026d7435a986','createTable tableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-7','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',39,'EXECUTED','7:611c17ee5c1f6265a2018c8165d5da67','createTable tableName=submission','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-8','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',40,'EXECUTED','7:851bb899043079a2e41a8c36909fcf81','createTable tableName=submitted_answer','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-9','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',41,'EXECUTED','7:6c5e2db3a6c030a799fce8d92ffcc9ad','addColumn tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-10','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',42,'EXECUTED','7:b8dd6acbd8c2eb1353e53c17c6f2e848','renameColumn newColumnName=completion_date, oldColumnName=build_completion_date, tableName=result','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-11','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',43,'EXECUTED','7:00d80cde7de76fea38f444fc6c5834d3','addColumn tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-12','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',44,'EXECUTED','7:16bc294226d061592fafcb933de508d4','renameColumn newColumnName=jhi_successful, oldColumnName=build_successful, tableName=result','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-13','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',45,'EXECUTED','7:d94fd51d880620963b7f965d29ba7510','addColumn tableName=result','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-14','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',46,'EXECUTED','7:5ffd3648411c6f88725daabf23c1f669','addPrimaryKey tableName=multiple_choice_submitted_answer_selected_options','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-15','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',47,'EXECUTED','7:1b479110cd84ab8ea40f399d431d59d9','addUniqueConstraint constraintName=UC_DRAG_ITEMCORRECT_LOCATION_ID_COL, tableName=drag_item','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-16','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',48,'EXECUTED','7:4cb25d170e517f14339b6a636e3499b1','addUniqueConstraint constraintName=UC_RESULTSUBMISSION_ID_COL, tableName=result','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-17','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',49,'EXECUTED','7:ca40ce4e6252900ad867401720f4bed3','addForeignKeyConstraint baseTableName=drag_and_drop_assignment, constraintName=FK3i875k6hd2xt8r30aljeuxyra, referencedTableName=submitted_answer','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-18','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',50,'EXECUTED','7:32e8476b34ea39c196d1d6960936fa7f','addForeignKeyConstraint baseTableName=result, constraintName=FK3vct9sad5oubthdmq63n58mnp, referencedTableName=submission','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-19','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',51,'EXECUTED','7:a2a1d216a2dd0b0afa16c0c0093efbf9','addForeignKeyConstraint baseTableName=drag_and_drop_assignment, constraintName=FK5hleb1iju5w10rt1pxkvdkj6j, referencedTableName=drag_item','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-20','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',52,'EXECUTED','7:a41a01fcfe4b0813a466040e5a0c0aed','addForeignKeyConstraint baseTableName=drag_item, constraintName=FK6qk07o5uhre61g1egc064wmmc, referencedTableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-21','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',53,'EXECUTED','7:b85065852cf77812b09a9775e51f9bbd','addForeignKeyConstraint baseTableName=multiple_choice_submitted_answer_selected_options, constraintName=FK87gmes7g3ad3qf3wmx3lu0iq0, referencedTableName=answer_option','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-22','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',54,'EXECUTED','7:5e0e1ff028303f136748600e43a27140','addForeignKeyConstraint baseTableName=drag_item, constraintName=FK8e1sjgkk2t883i9whn8m5w9qg, referencedTableName=drop_location','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-23','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',55,'EXECUTED','7:4f97ded660e86fc220d579e2264cbb9f','addForeignKeyConstraint baseTableName=question, constraintName=FKcw89k10abejup8p3hif0kjpw, referencedTableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-24','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',56,'EXECUTED','7:6986d77e4fe4344f335f4f958f66be64','addForeignKeyConstraint baseTableName=multiple_choice_submitted_answer_selected_options, constraintName=FKd6kco24shh09opxvn2kmi0dw5, referencedTableName=submitted_answer','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-25','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',57,'EXECUTED','7:0955f19c88b64648f5bd6fc782ea93fd','addForeignKeyConstraint baseTableName=answer_option, constraintName=FKfqeqisl0e28xp3yn9bmlgkhej, referencedTableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-26','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',58,'EXECUTED','7:afa17cf1db0dc9bb2e6214832c9a6764','addForeignKeyConstraint baseTableName=submitted_answer, constraintName=FKfwgc3a5vd0cxt5jsvvxm2tstf, referencedTableName=submission','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-27','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',59,'EXECUTED','7:81c758ebdd9c1b9416b44bbe419b99f5','addForeignKeyConstraint baseTableName=drag_and_drop_assignment, constraintName=FKjoafuadgwc0jipk0adfno7w2l, referencedTableName=drop_location','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1504180931627-28','krusche (generated)','classpath:config/liquibase/changelog/20170831140156_changelog.xml','2018-07-02 09:07:08',60,'EXECUTED','7:af17e940c353e6d0e208edabd1587755','addForeignKeyConstraint baseTableName=drop_location, constraintName=FKlbu6qm8ykqhtb5xm911bg5ct7, referencedTableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507148399605-1','Vale (generated)','classpath:config/liquibase/changelog/20171004221944_changelog.xml','2018-07-02 09:07:09',61,'EXECUTED','7:ec33de378baa6061c55955de7264294a','addColumn tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507148399605-2','Vale (generated)','classpath:config/liquibase/changelog/20171004221944_changelog.xml','2018-07-02 09:07:09',62,'EXECUTED','7:65418e5d7971232157f1ce1a03dc9c86','addColumn tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507148399605-3','Vale (generated)','classpath:config/liquibase/changelog/20171004221944_changelog.xml','2018-07-02 09:07:09',63,'EXECUTED','7:63dc937e91813e232561ef37dcf26207','addColumn tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507148399605-4','Vale (generated)','classpath:config/liquibase/changelog/20171004221944_changelog.xml','2018-07-02 09:07:09',64,'EXECUTED','7:65d69aefbdaa204a310a8d100001814e','addColumn tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1506774689028-1','Flo-VM (generated)','classpath:config/liquibase/changelog/20170930143105_changelog.xml','2018-07-02 09:07:09',65,'EXECUTED','7:258bbc25d849b15abf5d4c18eca754d3','createTable tableName=feedback','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1506774689028-2','Flo-VM (generated)','classpath:config/liquibase/changelog/20170930143105_changelog.xml','2018-07-02 09:07:09',66,'EXECUTED','7:d0d2ef8893590626a12245a3622a268a','addForeignKeyConstraint baseTableName=feedback, constraintName=FKmaiyumo49qyke2ijh8h575yy2, referencedTableName=result','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507746331973-1','Vale (generated)','classpath:config/liquibase/changelog/20171011202513_changelog.xml','2018-07-02 09:07:09',67,'EXECUTED','7:8f53ad2de219c7abdbef6a26b629e5ed','addColumn tableName=answer_option','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507746331973-2','Vale (generated)','classpath:config/liquibase/changelog/20171011202513_changelog.xml','2018-07-02 09:07:09',68,'EXECUTED','7:2f69e993573fbbbbe9c36d99b28564ad','addColumn tableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507746331973-3','Vale (generated)','classpath:config/liquibase/changelog/20171011202513_changelog.xml','2018-07-02 09:07:09',69,'EXECUTED','7:7ef87ae3dcf153f9b540c3af0746ea35','addColumn tableName=answer_option','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507746331973-4','Vale (generated)','classpath:config/liquibase/changelog/20171011202513_changelog.xml','2018-07-02 09:07:09',70,'EXECUTED','7:e200fb93fa57b4f70daeb004954c7e6d','addColumn tableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507746331973-5','Vale (generated)','classpath:config/liquibase/changelog/20171011202513_changelog.xml','2018-07-02 09:07:09',71,'EXECUTED','7:a27ba1f683c39a3ec15f025483cd9a6f','addColumn tableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507746331973-6','Vale (generated)','classpath:config/liquibase/changelog/20171011202513_changelog.xml','2018-07-02 09:07:09',72,'EXECUTED','7:2814e03bfe4cb63ac05a617e47c06d45','addColumn tableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507746331973-7','Vale (generated)','classpath:config/liquibase/changelog/20171011202513_changelog.xml','2018-07-02 09:07:09',73,'EXECUTED','7:c473d07a5418373001757687c526133a','addColumn tableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507746331973-8','Vale (generated)','classpath:config/liquibase/changelog/20171011202513_changelog.xml','2018-07-02 09:07:09',74,'EXECUTED','7:b5a9455e49adfdb13e7ef8896d209b2b','dropColumn columnName=correct_score, tableName=answer_option','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507746331973-9','Vale (generated)','classpath:config/liquibase/changelog/20171011202513_changelog.xml','2018-07-02 09:07:09',75,'EXECUTED','7:d6c08b308c6124cb341d24dbb7ab56eb','dropColumn columnName=incorrect_score, tableName=answer_option','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507746331973-10','Vale (generated)','classpath:config/liquibase/changelog/20171011202513_changelog.xml','2018-07-02 09:07:09',76,'EXECUTED','7:ea4753897700b0635142156483354789','dropColumn columnName=max_score, tableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507746331973-11','Vale (generated)','classpath:config/liquibase/changelog/20171011202513_changelog.xml','2018-07-02 09:07:09',77,'EXECUTED','7:b1fb981942a9dc3cc45f3d9a1765b2ee','dropColumn columnName=min_score, tableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507746331973-12','Vale (generated)','classpath:config/liquibase/changelog/20171011202513_changelog.xml','2018-07-02 09:07:09',78,'EXECUTED','7:f37ca9bc9ddfabf22421afa1476479af','dropNotNullConstraint columnName=completion_date, tableName=result','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507749398268-1','Vale (generated)','classpath:config/liquibase/changelog/20171011211615_changelog.xml','2018-07-02 09:07:09',79,'EXECUTED','7:cc418c06d1d1d70717b4beee9dc8a7df','createTable tableName=quiz_exercise_questions','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507749398268-2','Vale (generated)','classpath:config/liquibase/changelog/20171011211615_changelog.xml','2018-07-02 09:07:09',80,'EXECUTED','7:1ec7f894a5f2976a29e965c5b7a59c42','addPrimaryKey tableName=quiz_exercise_questions','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507749398268-3','Vale (generated)','classpath:config/liquibase/changelog/20171011211615_changelog.xml','2018-07-02 09:07:09',81,'EXECUTED','7:b71571d145e35bed2e68cbd69017d46f','addForeignKeyConstraint baseTableName=quiz_exercise_questions, constraintName=FKewh5i6ujecvx9j1q8mhj7fuw4, referencedTableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507749398268-4','Vale (generated)','classpath:config/liquibase/changelog/20171011211615_changelog.xml','2018-07-02 09:07:09',82,'EXECUTED','7:95e63e66a4731ddf4f3bf7f14b89b28d','addForeignKeyConstraint baseTableName=quiz_exercise_questions, constraintName=FKrf15kpj6sgj6li2ir1l4y0ddw, referencedTableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507749398268-5','Vale (generated)','classpath:config/liquibase/changelog/20171011211615_changelog.xml','2018-07-02 09:07:09',83,'EXECUTED','7:d9362d834361c1da27908a37a214b12e','dropForeignKeyConstraint baseTableName=question, constraintName=FKcw89k10abejup8p3hif0kjpw','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507749398268-6','Vale (generated)','classpath:config/liquibase/changelog/20171011211615_changelog.xml','2018-07-02 09:07:09',84,'EXECUTED','7:f0a0c3b5efd9830c58fe83b8e1397f60','dropColumn columnName=exercise_id, tableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507832287934-1','Vale (generated)','classpath:config/liquibase/changelog/20171012201744_changelog.xml','2018-07-02 09:07:09',85,'EXECUTED','7:ba2f9cb42035f7087e096a6a0faf48b4','addColumn tableName=answer_option','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507894928662-2','Vale (generated)','classpath:config/liquibase/changelog/20171013134151_changelog.xml','2018-07-02 09:07:09',86,'EXECUTED','7:e2bcb11e309ada31ac6c5a6b1732d165','addColumn tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507894928662-3','Vale (generated)','classpath:config/liquibase/changelog/20171013134151_changelog.xml','2018-07-02 09:07:09',87,'EXECUTED','7:e6da77f67f0e92db1527860be46f61a5','addColumn tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507894928662-4','Vale (generated)','classpath:config/liquibase/changelog/20171013134151_changelog.xml','2018-07-02 09:07:09',88,'EXECUTED','7:6a204ea2ab2b969a664135fe92451e81','addColumn tableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507894928662-5','Vale (generated)','classpath:config/liquibase/changelog/20171013134151_changelog.xml','2018-07-02 09:07:09',89,'EXECUTED','7:6f5c447408f2dca86951c8a2b318ea54','addColumn tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507894928662-6','Vale (generated)','classpath:config/liquibase/changelog/20171013134151_changelog.xml','2018-07-02 09:07:09',90,'EXECUTED','7:f08d6b181a602c198671b73de849ca8f','addColumn tableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507894928662-7','Vale (generated)','classpath:config/liquibase/changelog/20171013134151_changelog.xml','2018-07-02 09:07:09',91,'EXECUTED','7:e7898e742602fa29c1fb49395e5e6746','addColumn tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507894928662-8','Vale (generated)','classpath:config/liquibase/changelog/20171013134151_changelog.xml','2018-07-02 09:07:09',92,'EXECUTED','7:4f97ded660e86fc220d579e2264cbb9f','addForeignKeyConstraint baseTableName=question, constraintName=FKcw89k10abejup8p3hif0kjpw, referencedTableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507894928662-10','Vale (generated)','classpath:config/liquibase/changelog/20171013134151_changelog.xml','2018-07-02 09:07:09',93,'EXECUTED','7:aef8dcebf041315fcb43ade56753a09c','dropForeignKeyConstraint baseTableName=quiz_exercise_questions, constraintName=FKewh5i6ujecvx9j1q8mhj7fuw4','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507894928662-11','Vale (generated)','classpath:config/liquibase/changelog/20171013134151_changelog.xml','2018-07-02 09:07:09',94,'EXECUTED','7:59cd2c5bf4a438e47e8d460ded41ee49','dropForeignKeyConstraint baseTableName=quiz_exercise_questions, constraintName=FKrf15kpj6sgj6li2ir1l4y0ddw','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1507894928662-12','Vale (generated)','classpath:config/liquibase/changelog/20171013134151_changelog.xml','2018-07-02 09:07:09',95,'EXECUTED','7:8758bf96e4c602b4585403a77c7d2409','dropTable tableName=quiz_exercise_questions','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1509376465660-1','viki (generated)','classpath:config/liquibase/changelog/20171030161408_changelog.xml','2018-07-02 09:07:09',96,'EXECUTED','7:3ac98732fc76ccd1e8229eb942d7b1c9','addColumn tableName=feedback','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1509376465660-2','viki (generated)','classpath:config/liquibase/changelog/20171030161408_changelog.xml','2018-07-02 09:07:09',97,'EXECUTED','7:bf4af2700364e9e86006d4055f19f701','addNotNullConstraint columnName=login, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1509232974864-1','krusche (generated)','classpath:config/liquibase/changelog/20171029012243_changelog.xml','2018-07-02 09:07:09',98,'EXECUTED','7:8e4105625838c658403e6b461a94dada','addUniqueConstraint constraintName=UK6skuc03gvp2gw5lyji5lu0v0v, tableName=participation','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1509232974864-2','krusche (generated)','classpath:config/liquibase/changelog/20171029012243_changelog.xml','2018-07-02 09:07:09',99,'EXECUTED','7:bf4af2700364e9e86006d4055f19f701','addNotNullConstraint columnName=login, tableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1509811243331-1','Vale (generated)','classpath:config/liquibase/changelog/20171104170034_changelog.xml','2018-07-02 09:07:09',100,'EXECUTED','7:562f7b3209df9741cd9781bc10a14706','addColumn tableName=submitted_answer','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1509811243331-2','Vale (generated)','classpath:config/liquibase/changelog/20171104170034_changelog.xml','2018-07-02 09:07:09',101,'EXECUTED','7:df7cae9b421f2be80f8d068fb54bd50f','addForeignKeyConstraint baseTableName=submitted_answer, constraintName=FK6drbcvqf26cahd0j1u9ff0rtr, referencedTableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1511272964392-1','Vale (generated)','classpath:config/liquibase/changelog/20171121150234_changelog.xml','2018-07-02 09:07:09',102,'EXECUTED','7:7f3dbc00a756d94056f315340eda1dae','addColumn tableName=submission','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1511272964392-2','Vale (generated)','classpath:config/liquibase/changelog/20171121150234_changelog.xml','2018-07-02 09:07:09',103,'EXECUTED','7:54fca997f541239c3aa75d67e7c6170b','addColumn tableName=submission','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1511530763968-4','viki (generated)','classpath:config/liquibase/changelog/20171124143909_changelog.xml','2018-07-02 09:07:10',104,'EXECUTED','7:dcefc6ed1bdde98a4dd44a60576fe77c','addColumn tableName=course','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1511530763968-10','viki (generated)','classpath:config/liquibase/changelog/20171124143909_changelog.xml','2018-07-02 09:07:10',105,'EXECUTED','7:87cbf846a1c52872ea51d9d863ed2a5f','addColumn tableName=course','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1511530763968-11','viki (generated)','classpath:config/liquibase/changelog/20171124143909_changelog.xml','2018-07-02 09:07:10',106,'EXECUTED','7:127e49ba8e7098fccaff3ef0310f0759','addColumn tableName=course','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1511530763968-18','viki (generated)','classpath:config/liquibase/changelog/20171124143909_changelog.xml','2018-07-02 09:07:10',107,'EXECUTED','7:6c8292a46279df342afe571204e7c605','addColumn tableName=course','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1512050637264-1','Vale (generated)','classpath:config/liquibase/changelog/20171130150346_changelog.xml','2018-07-02 09:07:10',108,'EXECUTED','7:3d7e4c4db328855eb37c276113e4e884','addColumn tableName=submission','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1512050637264-2','Vale (generated)','classpath:config/liquibase/changelog/20171130150346_changelog.xml','2018-07-02 09:07:10',109,'EXECUTED','7:75069c30660bd2dfbdfaf2bda6302d5e','addColumn tableName=submitted_answer','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1512389509343-1','Mo (generated)','classpath:config/liquibase/changelog/20171204131140_changelog.xml','2018-07-02 09:07:10',110,'EXECUTED','7:b478022d7071aa7dd591c0182bf9d481','createTable tableName=statistic','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1512389509343-2','Mo (generated)','classpath:config/liquibase/changelog/20171204131140_changelog.xml','2018-07-02 09:07:10',111,'EXECUTED','7:8429a50a2097dea7ca76181703da108c','createTable tableName=statistic_counter','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1512389509343-3','Mo (generated)','classpath:config/liquibase/changelog/20171204131140_changelog.xml','2018-07-02 09:07:10',112,'EXECUTED','7:ce04fb8d13d0fb5be3f4b09bad14afac','addColumn tableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1512389509343-4','Mo (generated)','classpath:config/liquibase/changelog/20171204131140_changelog.xml','2018-07-02 09:07:10',113,'EXECUTED','7:5e8e1c00a6641739bf13cb28bed7c25c','addColumn tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1512389509343-5','Mo (generated)','classpath:config/liquibase/changelog/20171204131140_changelog.xml','2018-07-02 09:07:10',114,'EXECUTED','7:83184175d4f7ef456ad68b6ded14825a','addUniqueConstraint constraintName=UC_EXERCISEQUIZ_POINT_STATISTIC_ID_COL, tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1512389509343-6','Mo (generated)','classpath:config/liquibase/changelog/20171204131140_changelog.xml','2018-07-02 09:07:10',115,'EXECUTED','7:0cc1d5e7d1857da1338a08324f1ce86c','addUniqueConstraint constraintName=UC_QUESTIONQUESTION_STATISTIC_ID_COL, tableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1512389509343-7','Mo (generated)','classpath:config/liquibase/changelog/20171204131140_changelog.xml','2018-07-02 09:07:10',116,'EXECUTED','7:d4f02a015b46e989168d03a709e56b43','addUniqueConstraint constraintName=UC_STATISTIC_COUNTERANSWER_ID_COL, tableName=statistic_counter','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1512389509343-8','Mo (generated)','classpath:config/liquibase/changelog/20171204131140_changelog.xml','2018-07-02 09:07:10',117,'EXECUTED','7:98cdf3f64e49e4b37c7643607bb53830','addUniqueConstraint constraintName=UC_STATISTIC_COUNTERDROP_LOCATION_ID_COL, tableName=statistic_counter','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1512389509343-9','Mo (generated)','classpath:config/liquibase/changelog/20171204131140_changelog.xml','2018-07-02 09:07:10',118,'EXECUTED','7:5adb5fa5b1c00e504d611e6de72883e7','addForeignKeyConstraint baseTableName=statistic_counter, constraintName=FK11jgyics37wyibhhpp866exa8, referencedTableName=statistic','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1512389509343-10','Mo (generated)','classpath:config/liquibase/changelog/20171204131140_changelog.xml','2018-07-02 09:07:10',119,'EXECUTED','7:2834071e745526d20926b18f333bd1fd','addForeignKeyConstraint baseTableName=exercise, constraintName=FK1kyxexi1uqeu8pw3vcbulfi42, referencedTableName=statistic','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1512389509343-11','Mo (generated)','classpath:config/liquibase/changelog/20171204131140_changelog.xml','2018-07-02 09:07:10',120,'EXECUTED','7:4098d0d3726e0d75705c4df460953097','addForeignKeyConstraint baseTableName=statistic_counter, constraintName=FK2bses6ev8komaj0jw4gcyl8te, referencedTableName=drop_location','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1512389509343-12','Mo (generated)','classpath:config/liquibase/changelog/20171204131140_changelog.xml','2018-07-02 09:07:10',121,'EXECUTED','7:b39e598af0bc50bc267ce56535f68e2e','addForeignKeyConstraint baseTableName=statistic_counter, constraintName=FK3bqk45run356e20sqmf3sak1o, referencedTableName=statistic','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1512389509343-13','Mo (generated)','classpath:config/liquibase/changelog/20171204131140_changelog.xml','2018-07-02 09:07:10',122,'EXECUTED','7:43a77463d30d511c7413b5bf28bd773a','addForeignKeyConstraint baseTableName=statistic_counter, constraintName=FKg7hjug3wu6icklf6gbiqs4n18, referencedTableName=answer_option','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1512389509343-14','Mo (generated)','classpath:config/liquibase/changelog/20171204131140_changelog.xml','2018-07-02 09:07:10',123,'EXECUTED','7:88bca561badd6e4a5be5cecdecfe4890','addForeignKeyConstraint baseTableName=statistic_counter, constraintName=FKoh69hhnfcimvw7i1cwhepvt5g, referencedTableName=statistic','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1512389509343-15','Mo (generated)','classpath:config/liquibase/changelog/20171204131140_changelog.xml','2018-07-02 09:07:10',124,'EXECUTED','7:b342256258236def6777a079f33d3563','addForeignKeyConstraint baseTableName=question, constraintName=FKoi0sqphft8m7f92qa7ui8i3ac, referencedTableName=statistic','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1513607298124-1','Mo (generated)','classpath:config/liquibase/changelog/20171218152805_changelog.xml','2018-07-02 09:07:10',125,'EXECUTED','7:ea716ae110e489c2e890e9f1847e75cd','addColumn tableName=answer_option','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1513607298124-2','Mo (generated)','classpath:config/liquibase/changelog/20171218152805_changelog.xml','2018-07-02 09:07:10',126,'EXECUTED','7:133b4cf600bcfdede828370ea4ca9426','addColumn tableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1515421961516-1','Vale (generated)','classpath:config/liquibase/changelog/20180108153226_changelog.xml','2018-07-02 09:07:10',127,'EXECUTED','7:d7dd0a132984c700b601b43f055f79ff','addColumn tableName=drag_item','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1515421961516-2','Vale (generated)','classpath:config/liquibase/changelog/20180108153226_changelog.xml','2018-07-02 09:07:10',128,'EXECUTED','7:aa088decfe1323d1767c0c9e2a149d69','addColumn tableName=drop_location','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1515800759919-1','Vale (generated)','classpath:config/liquibase/changelog/20180113004549_changelog.xml','2018-07-02 09:07:10',129,'EXECUTED','7:48e0fe3d5f0607f46157c7e59590d55c','dropColumn columnName=correct_score, tableName=drag_item','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1515800759919-2','Vale (generated)','classpath:config/liquibase/changelog/20180113004549_changelog.xml','2018-07-02 09:07:10',130,'EXECUTED','7:7a26fc64eea2778d47bbea686bbd8ebb','dropColumn columnName=incorrect_score, tableName=drag_item','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1515963532604-1','Vale (generated)','classpath:config/liquibase/changelog/20180114215842_changelog.xml','2018-07-02 09:07:10',131,'EXECUTED','7:ff9023cfff6f8a7f07259fa480f53904','addColumn tableName=drag_and_drop_assignment','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1515963532604-2','Vale (generated)','classpath:config/liquibase/changelog/20180114215842_changelog.xml','2018-07-02 09:07:10',132,'EXECUTED','7:e1e69edc625b72fb8a3577a8688cc42d','addColumn tableName=drag_and_drop_assignment','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1515963532604-3','Vale (generated)','classpath:config/liquibase/changelog/20180114215842_changelog.xml','2018-07-02 09:07:10',133,'EXECUTED','7:938f1a0264e71fc0f2374c69a99d82c4','addForeignKeyConstraint baseTableName=drag_and_drop_assignment, constraintName=FK826ppkt4idlhts2bpo5ipg881, referencedTableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1515963532604-4','Vale (generated)','classpath:config/liquibase/changelog/20180114215842_changelog.xml','2018-07-02 09:07:10',134,'EXECUTED','7:51230f7e09681ac162827c22b2299183','dropForeignKeyConstraint baseTableName=drag_item, constraintName=FK8e1sjgkk2t883i9whn8m5w9qg','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1515963532604-5','Vale (generated)','classpath:config/liquibase/changelog/20180114215842_changelog.xml','2018-07-02 09:07:10',135,'EXECUTED','7:9781626a68ff653b8575ac55b1e37b36','dropUniqueConstraint constraintName=UC_DRAG_ITEMCORRECT_LOCATION_ID_COL, tableName=drag_item','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1515963532604-6','Vale (generated)','classpath:config/liquibase/changelog/20180114215842_changelog.xml','2018-07-02 09:07:10',136,'EXECUTED','7:7ebef689be315d5dfd26eadff7799a0e','dropColumn columnName=correct_location_id, tableName=drag_item','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1516058663403-1','Vale (generated)','classpath:config/liquibase/changelog/20180116002410_changelog.xml','2018-07-02 09:07:10',137,'EXECUTED','7:c39aaeba3332706e0974653404e86798','addColumn tableName=drag_and_drop_assignment','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1516058663403-2','Vale (generated)','classpath:config/liquibase/changelog/20180116002410_changelog.xml','2018-07-02 09:07:10',138,'EXECUTED','7:8436d210f3cbbcf6cf1b4743e4c38f30','addColumn tableName=drag_and_drop_assignment','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1516112166705-1','viki (generated)','classpath:config/liquibase/changelog/20180116151555_changelog.xml','2018-07-02 09:07:10',139,'EXECUTED','7:a1730e1cd533e3b0673c04050e4727ce','addColumn tableName=feedback','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1516115559434-1','Vale (generated)','classpath:config/liquibase/changelog/20180116161225_changelog.xml','2018-07-02 09:07:10',140,'EXECUTED','7:aa0c0e0cef5136baa9c3ad8127a4b132','createTable tableName=drag_and_drop_mapping','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1516115559434-2','Vale (generated)','classpath:config/liquibase/changelog/20180116161225_changelog.xml','2018-07-02 09:07:10',141,'EXECUTED','7:cf090c6b56c613b7fb924465f285a62b','addForeignKeyConstraint baseTableName=drag_and_drop_mapping, constraintName=FK5qmi3mh5y1mt9ysht4nx34nvf, referencedTableName=drag_item','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1516115559434-3','Vale (generated)','classpath:config/liquibase/changelog/20180116161225_changelog.xml','2018-07-02 09:07:10',142,'EXECUTED','7:1024d5dbcef10408bfd64e7040d1d90e','addForeignKeyConstraint baseTableName=drag_and_drop_mapping, constraintName=FK7p8h4scvkiilws9d5dxsdxkwy, referencedTableName=question','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1516115559434-4','Vale (generated)','classpath:config/liquibase/changelog/20180116161225_changelog.xml','2018-07-02 09:07:10',143,'EXECUTED','7:bc6a3d63cff938d047b21d6f39966a1b','addForeignKeyConstraint baseTableName=drag_and_drop_mapping, constraintName=FKa9cfgroc0t8ujct9ux7ub1nyo, referencedTableName=submitted_answer','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1516115559434-5','Vale (generated)','classpath:config/liquibase/changelog/20180116161225_changelog.xml','2018-07-02 09:07:10',144,'EXECUTED','7:eff355971b962c643638f3126db3b8a4','addForeignKeyConstraint baseTableName=drag_and_drop_mapping, constraintName=FKawq05c6hvxl400ouw5sg89xp9, referencedTableName=drop_location','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1516115559434-6','Vale (generated)','classpath:config/liquibase/changelog/20180116161225_changelog.xml','2018-07-02 09:07:10',145,'EXECUTED','7:7937bbb644c4138ca4a79d90222dfd50','dropForeignKeyConstraint baseTableName=drag_and_drop_assignment, constraintName=FK3i875k6hd2xt8r30aljeuxyra','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1516115559434-7','Vale (generated)','classpath:config/liquibase/changelog/20180116161225_changelog.xml','2018-07-02 09:07:10',146,'EXECUTED','7:a1de2b0f737a1f4e778a7db0cc54f5c4','dropForeignKeyConstraint baseTableName=drag_and_drop_assignment, constraintName=FK5hleb1iju5w10rt1pxkvdkj6j','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1516115559434-8','Vale (generated)','classpath:config/liquibase/changelog/20180116161225_changelog.xml','2018-07-02 09:07:10',147,'EXECUTED','7:af7e307c85fb0a58594e06d081a405b4','dropForeignKeyConstraint baseTableName=drag_and_drop_assignment, constraintName=FK826ppkt4idlhts2bpo5ipg881','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1516115559434-9','Vale (generated)','classpath:config/liquibase/changelog/20180116161225_changelog.xml','2018-07-02 09:07:10',148,'EXECUTED','7:31ba9c634bfb382cb46047c1af43ee4f','dropForeignKeyConstraint baseTableName=drag_and_drop_assignment, constraintName=FKjoafuadgwc0jipk0adfno7w2l','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1516115559434-10','Vale (generated)','classpath:config/liquibase/changelog/20180116161225_changelog.xml','2018-07-02 09:07:10',149,'EXECUTED','7:9b1d4b43b81f801597ed86cc0ff18907','dropTable tableName=drag_and_drop_assignment','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1517235026895-1','Mo (generated)','classpath:config/liquibase/changelog/20180129151014_changelog.xml','2018-07-02 09:07:10',150,'EXECUTED','7:63e27cb7f655f36d478df0d8dd3eae6e','addColumn tableName=drag_and_drop_mapping','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1517235026895-2','Mo (generated)','classpath:config/liquibase/changelog/20180129151014_changelog.xml','2018-07-02 09:07:10',151,'EXECUTED','7:dea6c613621335a8ecd56f7daffaaeef','addColumn tableName=drag_item','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1517235026895-3','Mo (generated)','classpath:config/liquibase/changelog/20180129151014_changelog.xml','2018-07-02 09:07:11',152,'EXECUTED','7:f054ad9188b032223b14312fd4ae1ef7','addColumn tableName=drop_location','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1517312107423-1','Vale (generated)','classpath:config/liquibase/changelog/20180130123457_changelog.xml','2018-07-02 09:07:11',153,'EXECUTED','7:c0c89f536f77b2d933686381b06e1ee7','addColumn tableName=result','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1518692765903-1','viki (generated)','classpath:config/liquibase/changelog/20180215120552_changelog.xml','2018-07-02 09:07:11',154,'EXECUTED','7:1b0c798399fb7bf0e881085035630f8c','addColumn tableName=result','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1520960566024-1','krusche (generated)','classpath:config/liquibase/changelog/20180313180222_changelog.xml','2018-07-02 09:07:11',155,'EXECUTED','7:dd7554ef938cc5a3f7ff64b7d8d346a7','createTable tableName=apollon_diagram','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1521047650173-4','marius (generated)','classpath:config/liquibase/changelog/20180314181406_changelog.xml','2018-07-02 09:07:11',156,'EXECUTED','7:e13888b5bfd3685c4bfe0a1712ba5f77','addColumn tableName=apollon_diagram','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1524408867295-1','mai (generated)','classpath:config/liquibase/changelog/20180422145420_changelog.xml','2018-07-02 09:07:11',157,'EXECUTED','7:0bf5e5ffb8c80749d8636b43cca7d5cf','dropColumn columnName=submission_path, tableName=submission','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1524408867295-2','mai (generated)','classpath:config/liquibase/changelog/20180422145420_changelog.xml','2018-07-02 09:07:11',158,'EXECUTED','7:c9c96b7cb0533cfbf3af7a17343fd280','addColumn tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1524408867295-3','mai (generated)','classpath:config/liquibase/changelog/20180422145420_changelog.xml','2018-07-02 09:07:11',159,'EXECUTED','7:acef492479b92c257bcc644fa56c146f','dropColumn columnName=base_file_path, tableName=exercise','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1527501454351-1','mai (generated)','classpath:config/liquibase/changelog/20180528095728_changelog.xml','2018-07-02 09:07:11',160,'EXECUTED','7:23889d2ac89814ea1d032035ab0dcf0f','addColumn tableName=result','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1527501454351-2','mai (generated)','classpath:config/liquibase/changelog/20180528095728_changelog.xml','2018-07-02 09:07:11',161,'EXECUTED','7:16838f994b04814fd51274970d80ea1d','addColumn tableName=result','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1527501454351-3','mai (generated)','classpath:config/liquibase/changelog/20180528095728_changelog.xml','2018-07-02 09:07:11',162,'EXECUTED','7:406d465fa14511f4cac4442ba391ac45','addUniqueConstraint constraintName=UC_RESULTASSESSOR_ID_COL, tableName=result','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1527501454351-4','mai (generated)','classpath:config/liquibase/changelog/20180528095728_changelog.xml','2018-07-02 09:07:11',163,'EXECUTED','7:7fe393d0ed5a94d78fe9a3056560b474','addForeignKeyConstraint baseTableName=result, constraintName=FKkkdvousfa95ueh46xg741fadg, referencedTableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1527507063126-1','mai (generated)','classpath:config/liquibase/changelog/20180528113059_changelog.xml','2018-07-02 09:07:11',164,'EXECUTED','7:fc085b6d2cee4d01a2850425dadc544e','dropForeignKeyConstraint baseTableName=result, constraintName=FKkkdvousfa95ueh46xg741fadg','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1527507063126-2','mai (generated)','classpath:config/liquibase/changelog/20180528113059_changelog.xml','2018-07-02 09:07:11',165,'EXECUTED','7:8e7c3109dc8ab1c36b57b37c177ac7b2','dropUniqueConstraint constraintName=UC_RESULTASSESSOR_ID_COL, tableName=result','',NULL,'3.5.4',NULL,NULL,'0511627212'),
	('1528143609026-1','krusche (generated)','classpath:config/liquibase/changelog/20180604221953_changelog.xml','2018-07-02 09:07:11',166,'EXECUTED','7:7fe393d0ed5a94d78fe9a3056560b474','addForeignKeyConstraint baseTableName=result, constraintName=FKkkdvousfa95ueh46xg741fadg, referencedTableName=jhi_user','',NULL,'3.5.4',NULL,NULL,'0511627212');

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
  CONSTRAINT `FK7p8h4scvkiilws9d5dxsdxkwy` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`),
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
  CONSTRAINT `FK6qk07o5uhre61g1egc064wmmc` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`)
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
  CONSTRAINT `FKlbu6qm8ykqhtb5xm911bg5ct7` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table exercise
# ------------------------------------------------------------

DROP TABLE IF EXISTS `exercise`;

CREATE TABLE `exercise` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) DEFAULT NULL,
  `release_date` timestamp NULL,
  `due_date` timestamp NULL,
  `course_id` bigint(20) DEFAULT NULL,
  `publish_build_plan_url` bit(1) DEFAULT NULL,
  `base_build_plan_id` varchar(255) DEFAULT NULL,
  `base_repository_url` varchar(255) DEFAULT NULL,
  `allow_online_editor` bit(1) DEFAULT NULL,
  `discriminator` varchar(31) NOT NULL,
  `duration` int(11) DEFAULT NULL,
  `is_open_for_practice` bit(1) DEFAULT NULL,
  `is_planned_to_start` bit(1) DEFAULT NULL,
  `is_visible_before_start` bit(1) DEFAULT NULL,
  `allowed_number_of_attempts` int(11) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `explanation` varchar(255) DEFAULT NULL,
  `randomize_question_order` bit(1) DEFAULT NULL,
  `quiz_point_statistic_id` bigint(20) DEFAULT NULL,
  `max_score` double DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_EXERCISEQUIZ_POINT_STATISTIC_ID_COL` (`quiz_point_statistic_id`),
  KEY `fk_exercise_course_id` (`course_id`),
  CONSTRAINT `FK1kyxexi1uqeu8pw3vcbulfi42` FOREIGN KEY (`quiz_point_statistic_id`) REFERENCES `statistic` (`id`),
  CONSTRAINT `fk_exercise_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table feedback
# ------------------------------------------------------------

DROP TABLE IF EXISTS `feedback`;

CREATE TABLE `feedback` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `detail_text` varchar(255) DEFAULT NULL,
  `text` varchar(255) DEFAULT NULL,
  `result_id` bigint(20) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `positive` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmaiyumo49qyke2ijh8h575yy2` (`result_id`),
  CONSTRAINT `FKmaiyumo49qyke2ijh8h575yy2` FOREIGN KEY (`result_id`) REFERENCES `result` (`id`)
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
  `principal` varchar(50) NOT NULL,
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
  `name` varchar(150) NOT NULL,
  `value` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`event_id`,`name`),
  KEY `idx_persistent_audit_evt_data` (`event_id`),
  CONSTRAINT `fk_evt_pers_audit_evt_data` FOREIGN KEY (`event_id`) REFERENCES `jhi_persistent_audit_event` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table jhi_persistent_token
# ------------------------------------------------------------

DROP TABLE IF EXISTS `jhi_persistent_token`;

CREATE TABLE `jhi_persistent_token` (
  `series` varchar(20) NOT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  `token_value` varchar(20) NOT NULL,
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
  `image_url` varchar(256) DEFAULT NULL,
  `activated` bit(1) NOT NULL,
  `lang_key` varchar(6) DEFAULT NULL,
  `activation_key` varchar(20) DEFAULT NULL,
  `reset_key` varchar(20) DEFAULT NULL,
  `created_by` varchar(50) NOT NULL,
  `created_date` timestamp NOT NULL,
  `reset_date` timestamp NULL DEFAULT NULL,
  `last_modified_by` varchar(50) DEFAULT NULL,
  `last_modified_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_user_login` (`login`),
  UNIQUE KEY `idx_user_login` (`login`),
  UNIQUE KEY `ux_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

LOCK TABLES `jhi_user` WRITE;
/*!40000 ALTER TABLE `jhi_user` DISABLE KEYS */;

INSERT INTO `jhi_user` (`id`, `login`, `password_hash`, `first_name`, `last_name`, `email`, `image_url`, `activated`, `lang_key`, `activation_key`, `reset_key`, `created_by`, `created_date`, `reset_date`, `last_modified_by`, `last_modified_date`)
VALUES
	(1,'system','$2a$10$mE.qmcV0mFU5NcKh73TZx.z4ueI/.bDWbj0T1BYyqP481kGGarKLG','System','System','system@localhost','',b'1','en',NULL,NULL,'system','2018-07-02 09:07:07',NULL,'system',NULL),
	(2,'anonymoususer','$2a$10$j8S5d7Sr7.8VTOYNviDPOeWX8KcYILUVJBsYV83Y5NtECayypx9lO','Anonymous','User','anonymous@localhost','',b'1','en',NULL,NULL,'system','2018-07-02 09:07:07',NULL,'system',NULL),
	(3,'admin','$2a$10$gSAhZrxMllrbgj/kkK9UceBPpChGWJA7SYIb1Mqo.n5aNLq1/oRrC','Administrator','Administrator','admin@localhost','',b'1','en',NULL,NULL,'system','2018-07-02 09:07:07',NULL,'system',NULL),
	(4,'user','$2a$10$VEjxo0jq2YG9Rbk2HmX9S.k1uZBGYUHdUcid3g/vfiEl7lwWgOH/K','User','User','user@localhost','',b'1','en',NULL,NULL,'system','2018-07-02 09:07:07',NULL,'system',NULL);

/*!40000 ALTER TABLE `jhi_user` ENABLE KEYS */;
UNLOCK TABLES;


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

LOCK TABLES `jhi_user_authority` WRITE;
/*!40000 ALTER TABLE `jhi_user_authority` DISABLE KEYS */;

INSERT INTO `jhi_user_authority` (`user_id`, `authority_name`)
VALUES
	(1,'ROLE_ADMIN'),
	(3,'ROLE_ADMIN'),
	(1,'ROLE_USER'),
	(3,'ROLE_USER'),
	(4,'ROLE_USER');

/*!40000 ALTER TABLE `jhi_user_authority` ENABLE KEYS */;
UNLOCK TABLES;


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
  KEY `fk_ltioutcomeurl_user_id` (`user_id`),
  KEY `fk_ltioutcomeurl_exercise_id` (`exercise_id`),
  CONSTRAINT `fk_ltioutcomeurl_exercise_id` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ltioutcomeurl_user_id` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`) ON DELETE CASCADE
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
  CONSTRAINT `fk_ltiuserid_user_id` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`) ON DELETE CASCADE
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



# Dump of table participation
# ------------------------------------------------------------

DROP TABLE IF EXISTS `participation`;

CREATE TABLE `participation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `student_id` bigint(20) DEFAULT NULL,
  `exercise_id` bigint(20) DEFAULT NULL,
  `initialization_state` varchar(255) DEFAULT NULL,
  `initialization_date` datetime DEFAULT NULL,
  `build_plan_id` varchar(255) DEFAULT NULL,
  `repository_url` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6skuc03gvp2gw5lyji5lu0v0v` (`student_id`,`exercise_id`,`initialization_state`),
  KEY `fk_participation_exercise_id` (`exercise_id`),
  CONSTRAINT `fk_participation_exercise_id` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `fk_participation_student_id` FOREIGN KEY (`student_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;



# Dump of table question
# ------------------------------------------------------------

DROP TABLE IF EXISTS `question`;

CREATE TABLE `question` (
  `discriminator` varchar(31) NOT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `text` varchar(255) DEFAULT NULL,
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
  CONSTRAINT `FKcw89k10abejup8p3hif0kjpw` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `FKoi0sqphft8m7f92qa7ui8i3ac` FOREIGN KEY (`question_statistic_id`) REFERENCES `statistic` (`id`)
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
  KEY `fk_result_participation_id` (`participation_id`),
  KEY `FKkkdvousfa95ueh46xg741fadg` (`assessor_id`),
  CONSTRAINT `FK3vct9sad5oubthdmq63n58mnp` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`),
  CONSTRAINT `FKkkdvousfa95ueh46xg741fadg` FOREIGN KEY (`assessor_id`) REFERENCES `jhi_user` (`id`),
  CONSTRAINT `fk_result_participation_id` FOREIGN KEY (`participation_id`) REFERENCES `participation` (`id`)
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
  CONSTRAINT `FK11jgyics37wyibhhpp866exa8` FOREIGN KEY (`drag_and_drop_question_statistic_id`) REFERENCES `statistic` (`id`),
  CONSTRAINT `FK2bses6ev8komaj0jw4gcyl8te` FOREIGN KEY (`drop_location_id`) REFERENCES `drop_location` (`id`),
  CONSTRAINT `FK3bqk45run356e20sqmf3sak1o` FOREIGN KEY (`quiz_point_statistic_id`) REFERENCES `statistic` (`id`),
  CONSTRAINT `FKg7hjug3wu6icklf6gbiqs4n18` FOREIGN KEY (`answer_id`) REFERENCES `answer_option` (`id`),
  CONSTRAINT `FKoh69hhnfcimvw7i1cwhepvt5g` FOREIGN KEY (`multiple_choice_question_statistic_id`) REFERENCES `statistic` (`id`)
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
  PRIMARY KEY (`id`)
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
  KEY `FKfwgc3a5vd0cxt5jsvvxm2tstf` (`submission_id`),
  KEY `FK6drbcvqf26cahd0j1u9ff0rtr` (`question_id`),
  CONSTRAINT `FK6drbcvqf26cahd0j1u9ff0rtr` FOREIGN KEY (`question_id`) REFERENCES `question` (`id`),
  CONSTRAINT `FKfwgc3a5vd0cxt5jsvvxm2tstf` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`)
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
