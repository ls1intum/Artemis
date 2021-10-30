-- MySQL dump 10.13  Distrib 5.7.36, for Linux (x86_64)
--
-- Host: 127.0.0.1    Database: Artemis
-- ------------------------------------------------------
-- Server version	8.0.25

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `Artemis`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `Artemis` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `Artemis`;

--
-- Table structure for table `DATABASECHANGELOG`
--

DROP TABLE IF EXISTS `DATABASECHANGELOG`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DATABASECHANGELOG` (
  `ID` varchar(255) NOT NULL,
  `AUTHOR` varchar(255) NOT NULL,
  `FILENAME` varchar(255) NOT NULL,
  `DATEEXECUTED` datetime NOT NULL,
  `ORDEREXECUTED` int NOT NULL,
  `EXECTYPE` varchar(10) NOT NULL,
  `MD5SUM` varchar(35) DEFAULT NULL,
  `DESCRIPTION` varchar(255) DEFAULT NULL,
  `COMMENTS` varchar(255) DEFAULT NULL,
  `TAG` varchar(255) DEFAULT NULL,
  `LIQUIBASE` varchar(20) DEFAULT NULL,
  `CONTEXTS` varchar(255) DEFAULT NULL,
  `LABELS` varchar(255) DEFAULT NULL,
  `DEPLOYMENT_ID` varchar(10) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `DATABASECHANGELOG`
--

LOCK TABLES `DATABASECHANGELOG` WRITE;
/*!40000 ALTER TABLE `DATABASECHANGELOG` DISABLE KEYS */;
INSERT INTO `DATABASECHANGELOG` VALUES ('00000000000001','krusche','config/liquibase/changelog/00000000000000_initial_schema.xml','2021-10-30 19:06:20',1,'EXECUTED','8:2c9c80db5b45bbf1d85e321f6bd408fd','createTable tableName=answer_option; createTable tableName=apollon_diagram; createTable tableName=course; createTable tableName=drag_and_drop_mapping; createTable tableName=drag_item; createTable tableName=drop_location; createTable tableName=exer...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1540544592272-1','krusche','config/liquibase/changelog/20181026110308_changelog.xml','2021-10-30 19:06:20',2,'EXECUTED','8:9f66989b166b3bcb8b055e9d57813df3','addColumn tableName=exercise; addColumn tableName=course','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1541592773036-1','krusche','config/liquibase/changelog/20181107131246_changelog.xml','2021-10-30 19:06:24',3,'EXECUTED','8:62bb45cc8c21407215e161f473ea1c96','createTable tableName=short_answer_submitted_text; createTable tableName=short_answer_mapping; createTable tableName=short_answer_solution; createTable tableName=short_answer_spot; addColumn tableName=statistic_counter; addColumn tableName=statist...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1543924264951-1','jpbernius','config/liquibase/changelog/20181204125049_changelog.xml','2021-10-30 19:06:25',4,'EXECUTED','8:8facf406d1563e91512c2d943de12686','addColumn tableName=feedback; modifyDataType columnName=text, tableName=feedback','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1544786174713','krusche','config/liquibase/changelog/20181214121608_changelog.xml','2021-10-30 19:06:29',5,'EXECUTED','8:cbdbe28b5a65796c2d6b728686e5607f','createTable tableName=complaint; createTable tableName=complaint_response; createTable tableName=example_submission; createTable tableName=tutor_participation; addColumn tableName=result; addColumn tableName=submission; addColumn tableName=course;...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1548099226633','krusche','config/liquibase/changelog/20190121203339_changelog.xml','2021-10-30 19:06:29',6,'EXECUTED','8:9224c273d46dbfa4e72637455e4bca61','addColumn tableName=exercise; dropColumn columnName=released, tableName=statistic','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1548585910503-01','krusche','config/liquibase/changelog/20190127114504_changelog.xml','2021-10-30 19:06:30',7,'EXECUTED','8:9bf353f7fd41c94fcdc411a726318eda','dropForeignKeyConstraint baseTableName=tutor_participation, constraintName=FK3hpwu78yd7lmteft5itac6t1k; dropForeignKeyConstraint baseTableName=tutor_participation, constraintName=FKqxdo67bt084eag6onrwkww8vq; dropUniqueConstraint constraintName=UC_...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1548585910503-02','krusche','config/liquibase/changelog/20190127114504_changelog.xml','2021-10-30 19:06:30',8,'EXECUTED','8:5a122d98044c9ed8058f0883f00d3b12','addColumn tableName=course; addColumn tableName=short_answer_submitted_text; addColumn tableName=short_answer_spot','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1548777349810','krusche','config/liquibase/changelog/20190129165404_changelog.xml','2021-10-30 19:06:31',9,'EXECUTED','8:d7db81eee45f0be2745e7f1f1eb95dc0','dropForeignKeyConstraint baseTableName=short_answer_submitted_text, constraintName=FKpkb6e1yjqhma5tgvabb9smyv3; dropUniqueConstraint constraintName=UC_SHORT_ANSWER_SUBMITTED_TEXTSPOT_ID_COL, tableName=short_answer_submitted_text; addForeignKeyCons...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1551295289847-1','krusche','config/liquibase/changelog/20190227202121_changelog.xml','2021-10-30 19:06:32',10,'EXECUTED','8:aae005f58368fbe908ce8b915d1418c7','addColumn tableName=exercise; addUniqueConstraint constraintName=UC_EXERCISESOLUTION_PARTICIPATION_ID_COL, tableName=exercise; addUniqueConstraint constraintName=UC_EXERCISETEMPLATE_PARTICIPATION_ID_COL, tableName=exercise; addForeignKeyConstraint...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1551295875207','krusche','config/liquibase/changelog/20190227203021_changelog.xml','2021-10-30 19:06:33',11,'EXECUTED','8:586450db24170b81eabbba49220a8f34','dropColumn columnName=base_build_plan_id, tableName=exercise; dropColumn columnName=base_repository_url, tableName=exercise; dropColumn columnName=solution_build_plan_id, tableName=exercise; dropColumn columnName=solution_repository_url, tableName...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1552664538429','krusche','config/liquibase/changelog/20190315164212_changelog.xml','2021-10-30 19:06:44',12,'EXECUTED','8:f8574756567b93bd4496099308cb7d4a','renameTable newTableName=quiz_question, oldTableName=question; renameTable newTableName=quiz_statistic, oldTableName=statistic; renameTable newTableName=quiz_statistic_counter, oldTableName=statistic_counter; renameColumn newColumnName=quiz_questi...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1553514252258','krusche','config/liquibase/changelog/20190325123821_changelog.xml','2021-10-30 19:06:44',13,'EXECUTED','8:ca5f281ed33c9469ec3868ba08064a94','addColumn tableName=apollon_diagram','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1554711275151','krusche','config/liquibase/changelog/20190408101427_changelog.xml','2021-10-30 19:06:45',14,'EXECUTED','8:946104077b96c67b4d4ebd326370df65','dropForeignKeyConstraint baseTableName=complaint, constraintName=FKjodokcxrnd8igpwe9g36a26p3; dropForeignKeyConstraint baseTableName=complaint_response, constraintName=FKb864d65horyth8i17crcdco2j; dropUniqueConstraint constraintName=UC_COMPLAINTST...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1554884367154-1','krusche','config/liquibase/changelog/20190410101919_changelog.xml','2021-10-30 19:06:45',15,'EXECUTED','8:de6f30f6c139760636ffe5678ab15f34','addColumn tableName=attachment','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1554884367154-2','krusche','config/liquibase/changelog/20190410101919_changelog.xml','2021-10-30 19:06:46',16,'EXECUTED','8:dbd23ffe1595dc98a5221e16e4b8663f','createTable tableName=conflicting_result; createTable tableName=model_assessment_conflict; addForeignKeyConstraint baseTableName=conflicting_result, constraintName=FK6v77dp8g5ge9y1squlelo9k0n, referencedTableName=feedback; addForeignKeyConstraint ...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1555426860230-1','krusche','config/liquibase/changelog/20190416170046_changelog.xml','2021-10-30 19:06:47',17,'EXECUTED','8:1750d8c8428983a522b256bc0f7a4ce3','createTable tableName=tutor_participation_trained_example_submissions; addPrimaryKey tableName=tutor_participation_trained_example_submissions; addForeignKeyConstraint baseTableName=tutor_participation_trained_example_submissions, constraintName=F...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1555426860230-2','krusche','config/liquibase/changelog/20190416170046_changelog.xml','2021-10-30 19:06:47',18,'EXECUTED','8:b7445fb7658c2c19966833aeacf5eef0','dropColumn columnName=tutor_participation_id, tableName=example_submission','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1555426860232','jpbernius','config/liquibase/changelog/20190514170000_changelog.xml','2021-10-30 19:06:48',19,'EXECUTED','8:9e28525e0d71ff142c8886f805f87aef','addColumn tableName=submission','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1558315741785-1','ziegner','config/liquibase/changelog/20190520032531_changelog.xml','2021-10-30 19:06:48',20,'EXECUTED','8:97d2332d5ff9ae1e648444b6769a0276','addColumn tableName=example_submission','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1559227133834-1','radchuk','config/liquibase/changelog/20190530163845_changelog.xml','2021-10-30 19:06:48',21,'EXECUTED','8:38f1d5e1c223f97fac3ae0755a429b56','addColumn tableName=complaint; addNotNullConstraint columnName=complaint_type, tableName=complaint','',NULL,'4.5.0',NULL,NULL,'5620761397'),('201905026142600','behnke','config/liquibase/changelog/20190603142500_changelog.xml','2021-10-30 19:06:49',22,'EXECUTED','8:5073e09fc25a2ffff72653b62905c2f1','createTable tableName=programming_exercise_test_case; addUniqueConstraint constraintName=exercise_test_case, tableName=programming_exercise_test_case; addForeignKeyConstraint baseTableName=programming_exercise_test_case, constraintName=fk_programm...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1560945129025','krusche','config/liquibase/changelog/20190619134531_changelog.xml','2021-10-30 19:06:49',23,'EXECUTED','8:a697acf13ef82f5495fb5fa2b0caa7d1','createView viewName=view_tutor_leaderboard_assessments; createView viewName=view_tutor_leaderboard_accepted_complaints; createView viewName=view_tutor_leaderboard_complaint_responses','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1561222333834-1','radchuk','config/liquibase/changelog/20190711168543_changelog.xml','2021-10-30 19:06:49',24,'EXECUTED','8:0d45f710b5413cf6a95ba8f4a45c35f1','createView viewName=view_tutor_leaderboard_accepted_complaints; createView viewName=view_tutor_leaderboard_not_answered_more_feedback_requests; createView viewName=view_tutor_leaderboard_answered_more_feedback_requests; createView viewName=view_tu...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1562320394217-1','krusche','config/liquibase/changelog/20190705114935_changelog.xml','2021-10-30 19:06:49',25,'EXECUTED','8:3ea8dc8df028b05a5f24e67f0a93c5a5','addColumn tableName=participation','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1562320394217-2','krusche','config/liquibase/changelog/20190705114935_changelog.xml','2021-10-30 19:06:49',26,'EXECUTED','8:6e07f15d0a7f89c7647ddbab8e798a01','sql; sql; sql; sql','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20190715204744-1','behnke','config/liquibase/changelog/20190715204745_changelog.xml','2021-10-30 19:06:50',27,'EXECUTED','8:382b5b4747739765f37185fde0a039ae','createTable tableName=exercise_hint; addForeignKeyConstraint baseTableName=exercise_hint, constraintName=fk_exercise_hint_exercise_id, referencedTableName=exercise','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20190707203000','jpbernius','config/liquibase/changelog/20190707203000_changelog.xml','2021-10-30 19:06:51',28,'EXECUTED','8:fe6f31f7144004ae825db89d32c6200e','createTable tableName=text_block; addForeignKeyConstraint baseTableName=text_block, constraintName=fk_text_block_submission_id, referencedTableName=submission; createTable tableName=text_cluster; addForeignKeyConstraint baseTableName=text_block, c...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20190719162231','behnke','config/liquibase/changelog/20190719162231_changelog.xml','2021-10-30 19:06:51',29,'EXECUTED','8:dca2436ee3756baa332790832053373b','addColumn tableName=programming_exercise_test_case','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20190812135345','krusche','config/liquibase/changelog/20190812135345_changelog.xml','2021-10-30 19:06:51',30,'EXECUTED','8:78b0c300c7a19cfaa1ed0c9c11461a9f','addColumn tableName=jhi_user','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1566406520708','phuong.anh.montag','config/liquibase/changelog/20190821185514_changelog.xml','2021-10-30 19:06:52',31,'EXECUTED','8:65785e537100a73c487bf56a61a03fe3','createTable tableName=guided_tour_setting; addForeignKeyConstraint baseTableName=guided_tour_setting, constraintName=FKdbjfaktpewig8lac4jimhf34l, referencedTableName=jhi_user','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20190813140300','elkbreeder','config/liquibase/changelog/20190813140300_changelog.xml','2021-10-30 19:06:52',32,'EXECUTED','8:60b55a13b77660e59a62a5db7cd707c3','addColumn tableName=text_block','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20190812135345','krusche','config/liquibase/changelog/20190828113914_changelog.xml','2021-10-30 19:06:52',33,'EXECUTED','8:cd3caa832e0b43de258957fafcb7461f','dropTable tableName=jhi_persistent_token','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20190906100940','behnke','config/liquibase/changelog/20190906100940_changelog.xml','2021-10-30 19:06:52',34,'EXECUTED','8:ed2e60c9a6209cd58d16869bf94e52f3','createTable tableName=programming_exercise_details','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20190924183733','behnke','config/liquibase/changelog/20190924183733_changelog.xml','2021-10-30 19:06:52',35,'EXECUTED','8:57054ff1d28cdcd9a3891623927de3ae','addColumn tableName=programming_exercise_details','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20190928155324','ungar','config/liquibase/changelog/20190928155324_changelog.xml','2021-10-30 19:06:52',36,'EXECUTED','8:35c7118e12429191f543a942f8c6d9f0','addColumn tableName=programming_exercise_details; sql; sql','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20190929121500','phuong.anh.montag','config/liquibase/changelog/20190929121500_changelog.xml','2021-10-30 19:06:53',37,'EXECUTED','8:bf0bd6af5c2b77b97edbdf13c992b565','addColumn tableName=course; addColumn tableName=exercise','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20191008103112','phuong.anh.montag','config/liquibase/changelog/20191008103112_changelog.xml','2021-10-30 19:06:53',38,'EXECUTED','8:c5789769309de5559967ec8c122c2bc3','dropNotNullConstraint columnName=presentation_score, tableName=course; dropNotNullConstraint columnName=presentation_score_enabled, tableName=exercise','',NULL,'4.5.0',NULL,NULL,'5620761397'),('2360945134025','radchuk','config/liquibase/changelog/20191013103042_changelog.xml','2021-10-30 19:06:53',39,'EXECUTED','8:e56f605e9ab8cfabca21573d0135fc02','createView viewName=view_tutor_leaderboard_assessments; createView viewName=view_tutor_leaderboard_accepted_complaints; createView viewName=view_tutor_leaderboard_not_answered_more_feedback_requests; createView viewName=view_tutor_leaderboard_answ...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1571145486572','krusche','config/liquibase/changelog/20191015151742_changelog.xml','2021-10-30 19:06:54',40,'EXECUTED','8:758de550535ccec3c1df65c3b2ad8ed3','dropNotNullConstraint columnName=created_date, tableName=jhi_user; dropDefaultValue columnName=created_date, tableName=jhi_user','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20191028100235','behnke','config/liquibase/changelog/20191028100235_changelog.xml','2021-10-30 19:06:54',41,'EXECUTED','8:a98fbb01eef60f56fce11babb18a7af1','createView viewName=view_tutor_leaderboard_assessments; createView viewName=view_tutor_leaderboard_accepted_complaints; createView viewName=view_tutor_leaderboard_not_answered_more_feedback_requests; createView viewName=view_tutor_leaderboard_answ...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20191210131150','hashemy','config/liquibase/changelog/20191210131150_changelog.xml','2021-10-30 19:06:55',42,'EXECUTED','8:c4891d8462dd1469fc3a779c42f959ef','createTable tableName=grading_criterion; createTable tableName=grading_instruction; addForeignKeyConstraint baseTableName=grading_criterion, constraintName=fk_grading_criterion_exercise_id, referencedTableName=exercise; addForeignKeyConstraint bas...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20191215212935','krusche','config/liquibase/changelog/20191215211735_changelog.xml','2021-10-30 19:06:55',43,'EXECUTED','8:c42f63b83d180d0cbb6433de6d0fc576','addUniqueConstraint constraintName=UK6sioc0zhvp2bxcl4fi5labvpa, tableName=tutor_participation','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200117211342-1','ungar','config/liquibase/changelog/20200117211342_changelog.xml','2021-10-30 19:06:55',44,'EXECUTED','8:4b2cbedb59f7444f79117c2079e03a86','addColumn tableName=submission','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200117211342-2','ungar','config/liquibase/changelog/20200117211342_changelog.xml','2021-10-30 19:06:55',45,'EXECUTED','8:ff7f7c7aa27d82aed9fbeaa56eb35a99','sql','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200117213324-1','ungar','config/liquibase/changelog/20200117213324_changelog.xml','2021-10-30 19:06:55',46,'EXECUTED','8:bd2687fce61baf43a48c57e0a70089ac','addColumn tableName=submission','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200117213324-2','ungar','config/liquibase/changelog/20200117213324_changelog.xml','2021-10-30 19:06:55',47,'EXECUTED','8:ddd036c74e14fd984a45f5992a437105','sql','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200128141242','ungar','config/liquibase/changelog/20200128141242_changelog.xml','2021-10-30 19:06:56',48,'EXECUTED','8:7362842853c1737253266a32ff31efd9','dropColumn columnName=build_artifact, tableName=result','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200202173920','madwau','config/liquibase/changelog/20200202173920_changelog.xml','2021-10-30 19:06:59',49,'EXECUTED','8:4b7574bba0e1e042328851366c5de48a','addColumn tableName=participation; createTable tableName=team; createTable tableName=team_student; createTable tableName=team_assignment_config; addColumn tableName=exercise; createIndex indexName=fk_participation_team_id, tableName=participation;...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200307164322','jonathantumboimbela','config/liquibase/changelog/20200307164322_changelog.xml','2021-10-30 19:07:00',50,'EXECUTED','8:a4da228b98a44ef6cd3eab4ce2d94cce','addColumn tableName=course; addColumn tableName=course; addDefaultValue columnName=max_complaints, tableName=course; addNotNullConstraint columnName=max_complaints, tableName=course','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200318144757','jpbernius','config/liquibase/changelog/20200318144757_changelog.xml','2021-10-30 19:07:00',51,'EXECUTED','8:43f6bdcb3ca1b7ef3b7e304f1e99facd','addColumn tableName=text_block','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200327214122','krusche','config/liquibase/changelog/20200327214122_changelog.xml','2021-10-30 19:07:00',52,'EXECUTED','8:b23e54597eb952f60a550d3d22b7902f','modifyDataType columnName=password_hash, tableName=jhi_user','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1585575971059-25','filipgregurevic (generated)','config/liquibase/changelog/20200330154601_changelog.xml','2021-10-30 19:07:00',53,'EXECUTED','8:d1bb6a1580f4435754cbcbb39c8f71d0','addColumn tableName=student_question_answer','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200330155834','madwau','config/liquibase/changelog/20200330155834_changelog.xml','2021-10-30 19:07:01',54,'EXECUTED','8:0175e9ebd68b7cd88c31974ffe63ec7f','addColumn tableName=team; addForeignKeyConstraint baseTableName=team, constraintName=fk_team_owner_id, referencedTableName=jhi_user','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200407150053','madwau','config/liquibase/changelog/20200407150053_changelog.xml','2021-10-30 19:07:01',55,'EXECUTED','8:20ee4481bc2fc1ef687129a609b4f343','dropUniqueConstraint constraintName=short_name, tableName=team; addUniqueConstraint constraintName=uc_team_exercise_id_and_short_name, tableName=team','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200408200283','madwau','config/liquibase/changelog/20200408200283_changelog.xml','2021-10-30 19:07:01',56,'EXECUTED','8:97d0e28232d66c1558a127c975f7b147','sql','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200413162034','krusche','config/liquibase/changelog/20200413162034_changelog.xml','2021-10-30 19:07:01',57,'EXECUTED','8:033c2bcd65ada40b80110431b107020b','addUniqueConstraint constraintName=UC_COURSE_SHORT_NAME, tableName=course','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1586705478626-21','f4ll3n (generated)','config/liquibase/changelog/20200412173108_changelog.xml','2021-10-30 19:07:02',58,'EXECUTED','8:c6568118d21aeecbec365fdd834e1c1a','modifyDataType columnName=pos_x, tableName=drop_location; modifyDataType columnName=pos_y, tableName=drop_location; modifyDataType columnName=width, tableName=drop_location; modifyDataType columnName=height, tableName=drop_location','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200416184036','krusche','config/liquibase/changelog/20200416184036_changelog.xml','2021-10-30 19:07:02',59,'EXECUTED','8:64c8ddb00662f9615add613e21c9af9f','dropView viewName=view_tutor_leaderboard_not_answered_more_feedback_requests; dropView viewName=view_tutor_leaderboard_accepted_complaints; createView viewName=view_tutor_leaderboard_complaints; createView viewName=view_tutor_leaderboard_more_feed...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200422140920','sebastianjagla','config/liquibase/changelog/20200422140920_changelog.xml','2021-10-30 19:07:03',60,'EXECUTED','8:45af7b14a5c0e907043c546f132eb7f9','addColumn tableName=apollon_diagram','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200426110000','saschabeele','config/liquibase/changelog/20200426110000_changelog.xml','2021-10-30 19:07:03',61,'EXECUTED','8:a453aef51a4cd154ca1d1f0450003e87','addColumn tableName=notification','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200504120935','madwau','config/liquibase/changelog/20200504120935_changelog.xml','2021-10-30 19:07:03',62,'EXECUTED','8:c4fc3f7041c3f545194adc1a55ca9108','sql; sql','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1589738911776-168','filipgregurevic (generated)','config/liquibase/changelog/20200517200811_changelog.xml','2021-10-30 19:07:03',63,'EXECUTED','8:88475a877cd44fb4add8878e47d16b0e','addColumn tableName=student_question','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200518205212','madwau','config/liquibase/changelog/20200518205212_changelog.xml','2021-10-30 19:07:04',64,'EXECUTED','8:4428c151fd099e8b907c7ce7a5d1f89c','createTable tableName=submission_version; addForeignKeyConstraint baseTableName=submission_version, constraintName=fk_submission_version_author_id, referencedTableName=jhi_user; addForeignKeyConstraint baseTableName=submission_version, constraintN...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200526172043','madwau','config/liquibase/changelog/20200526172043_changelog.xml','2021-10-30 19:07:04',65,'EXECUTED','8:5062948a5c862c54ed7a3503172b204d','addColumn tableName=course; addColumn tableName=complaint; addForeignKeyConstraint baseTableName=complaint, constraintName=fk_complaint_team_id, referencedTableName=team','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200601234000','petry','config/liquibase/changelog/20200601234000_changelog.xml','2021-10-30 19:07:05',66,'EXECUTED','8:2a8c61a77bc5e4ee6a6a06a2c9c161dd','createTable tableName=result_rating; addForeignKeyConstraint baseTableName=result_rating, constraintName=fk_result_rating_result_id, referencedTableName=result; createIndex indexName=fk_result_rating_result_id, tableName=result_rating','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200608203410','saschabeele','config/liquibase/changelog/20200608203410_changelog.xml','2021-10-30 19:07:08',67,'EXECUTED','8:dadd9d51d7b0229684d73b9e659c1107','createTable tableName=exam; createTable tableName=exam_user; createTable tableName=exercise_group; createTable tableName=student_exam; createTable tableName=student_exam_exercise; addColumn tableName=exercise; addForeignKeyConstraint baseTableName...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200618141500','krusche','config/liquibase/changelog/20200618141500_changelog.xml','2021-10-30 19:07:09',68,'EXECUTED','8:e42677c8f25770ed7468239f3d85f548','addColumn tableName=programming_exercise_details; sql','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200621123510','balazsczopf','config/liquibase/changelog/20200621123510_changelog.xml','2021-10-30 19:07:09',69,'EXECUTED','8:e27e70463ea2a13da6da8e0f8db03d3e','createTable tableName=exam_session','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20191210131150','hashemy','config/liquibase/changelog/20200526120801_changelog.xml','2021-10-30 19:07:09',70,'EXECUTED','8:13488fa6d42bdaeaecb81bd45a030f8a','addColumn tableName=feedback; addForeignKeyConstraint baseTableName=feedback, constraintName=fk_feedback_grading_instruction_id, referencedTableName=grading_instruction','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200626022200','saschabeele','config/liquibase/changelog/20200626022200_changelog.xml','2021-10-30 19:07:09',71,'EXECUTED','8:319ce451c94c3243337d859cf039ad0c','addColumn tableName=student_exam','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200629152810','krusche','config/liquibase/changelog/20200629152810_changelog.xml','2021-10-30 19:07:10',72,'EXECUTED','8:5a416ae0d3ae5b07afebfe01277eb5d9','modifyDataType columnName=browser_fingerprint_hash, tableName=exam_session; modifyDataType columnName=user_agent, tableName=exam_session; addColumn tableName=exam_session','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200621141500','krusche','config/liquibase/changelog/20200629162710_changelog.xml','2021-10-30 19:07:10',73,'EXECUTED','8:2ff2716c1a2d096073f1bf8fcb89acc2','dropColumn columnName=allow_online_editor, tableName=exercise','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1593787340249-30','jpbernius','config/liquibase/changelog/20200703164147_changelog.xml','2021-10-30 19:07:10',74,'EXECUTED','8:c3603984813c43e4dd570dca2fbb5802','addColumn tableName=exam_session','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200706225100','krusche','config/liquibase/changelog/20200706225110_changelog.xml','2021-10-30 19:07:10',75,'EXECUTED','8:0869659262852b8009badd997cec30cb','createIndex indexName=build_plan_id, tableName=participation; createIndex indexName=repository_url, tableName=participation','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1594126134662-35','stefanwaldhauser','config/liquibase/changelog/20200707144843_changelog.xml','2021-10-30 19:07:11',76,'EXECUTED','8:8fe74197e80357fd286a48b2ef68a8b4','addColumn tableName=exam','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1594540509233-37','tobias priesching (generated)','config/liquibase/changelog/20200712095451_changelog.xml','2021-10-30 19:07:11',77,'EXECUTED','8:ee84c5dee2aff64c5f39aa4788488b83','addColumn tableName=exam','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1594540509233-39','tobias priesching (generated)','config/liquibase/changelog/20200712095451_changelog.xml','2021-10-30 19:07:11',78,'EXECUTED','8:3bc99967d7d7bd34849b1ee78bcaf604','addColumn tableName=student_exam','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200715141400','saschabeele','config/liquibase/changelog/20200715141400_changelog.xml','2021-10-30 19:07:11',79,'EXECUTED','8:64662e499559086deb4a08d476e6ec3c','addColumn tableName=student_exam; addColumn tableName=student_exam','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200715213700','saschabeele','config/liquibase/changelog/20200715213700_changelog.xml','2021-10-30 19:07:11',80,'EXECUTED','8:21ecc5b186f9fe6c928001078b137c2f','addColumn tableName=exam; addColumn tableName=exam; addColumn tableName=exam','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200716231710.xml','krusche','config/liquibase/changelog/20200716231710_changelog.xml','2021-10-30 19:07:12',81,'EXECUTED','8:9d5fdea98581b3a723d40753b0eed657','sql; addForeignKeyConstraint baseTableName=exam_session, constraintName=FK6pok2pdckbv609q1kjcxnguha, referencedTableName=student_exam','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200726015816','birtan','config/liquibase/changelog/20200726015816_changelog.xml','2021-10-30 19:07:12',82,'EXECUTED','8:4aa490c5cd9c01774893df39803e7307','createTable tableName=feedback_conflict; addForeignKeyConstraint baseTableName=feedback_conflict, constraintName=fk_first_feedback_id, referencedTableName=feedback; addForeignKeyConstraint baseTableName=feedback_conflict, constraintName=fk_second_...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200728142700','kloessst','config/liquibase/changelog/20200728142700_changelog.xml','2021-10-30 19:07:13',83,'EXECUTED','8:1df65ed00b78e4c657dc5a876f69b8e8','addColumn tableName=programming_exercise_details','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200805101210','krusche','config/liquibase/changelog/20200805101210_changelog.xml','2021-10-30 19:07:31',84,'EXECUTED','8:653f588a636d57dd46139843e7548967','dropAllForeignKeyConstraints baseTableName=conflicting_result; dropAllForeignKeyConstraints baseTableName=model_assessment_conflict; dropTable tableName=conflicting_result; dropTable tableName=model_assessment_conflict','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200716231710','krusche','config/liquibase/changelog/20200806141810_changelog.xml','2021-10-30 19:07:32',85,'EXECUTED','8:4b8aa24a7351c1c7b500ccb0a95326cd','addColumn tableName=programming_exercise_test_case; modifyDataType columnName=weight, tableName=programming_exercise_test_case; addDefaultValue columnName=weight, tableName=programming_exercise_test_case; addColumn tableName=exercise','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200827182434','sleiss','config/liquibase/changelog/20200827182434_changelog.xml','2021-10-30 19:07:32',86,'EXECUTED','8:0acde333aecc473d8268fb0ab25bcc79','createTable tableName=build_log_entry; addForeignKeyConstraint baseTableName=build_log_entry, constraintName=FKarrnc5l01jjyixrw6jbu18k6a, referencedTableName=submission','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200901185005','kloessst','config/liquibase/changelog/20200901185005_changelog.xml','2021-10-30 19:07:33',87,'EXECUTED','8:7a05fc73e2dc93636b2da2b86e883e78','addColumn tableName=programming_exercise_details; createTable tableName=static_code_analysis_category; addForeignKeyConstraint baseTableName=static_code_analysis_category, constraintName=fk_static_code_analysis_category_exercise_id, referencedTabl...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200908112330','turdiu','config/liquibase/changelog/20200908112330_changelog.xml','2021-10-30 19:07:33',88,'EXECUTED','8:a875f859dea1e805ba8be5f371234923','addColumn tableName=student_exam','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200925174332','turdiu','config/liquibase/changelog/20200925174332_changelog.xml','2021-10-30 19:07:35',89,'EXECUTED','8:3764505a1b47411221dba0c99ad6f8fe','addColumn tableName=student_exam; sql; dropColumn columnName=last_modified_date, tableName=student_exam; dropColumn columnName=submission_date, tableName=student_exam; dropColumn columnName=created_date, tableName=student_exam; renameColumn newCol...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20201007105032','krusche','config/liquibase/changelog/20201007105032_changelog.xml','2021-10-30 19:07:35',90,'EXECUTED','8:6c79a046f8ce7e66d7634860aa9b99c8','addColumn tableName=course','',NULL,'4.5.0',NULL,NULL,'5620761397'),('1603125315878-42','stefanwaldhauser','config/liquibase/changelog/20201019183507_changelog.xml','2021-10-30 19:07:38',91,'EXECUTED','8:88f9d8ce48cbf941584656a411b83d8c','addColumn tableName=attachment; createTable tableName=learning_goal; createTable tableName=learning_goal_exercise; createTable tableName=learning_goal_lecture_unit; createTable tableName=lecture_unit; addForeignKeyConstraint baseTableName=learning...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20200908112330','birtan','config/liquibase/changelog/20200926154829_changelog.xml','2021-10-30 19:07:38',92,'EXECUTED','8:da98a5d84c875bc788e3d77476fe062d','addColumn tableName=feedback_conflict','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20201015224743','franke','config/liquibase/changelog/20201015224743_changelog.xml','2021-10-30 19:07:38',93,'EXECUTED','8:b72a34c0923e31242200e68a71ca5013','addColumn tableName=course','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20201007105032','sleiss','config/liquibase/changelog/20201026115657_changelog.xml','2021-10-30 19:07:38',94,'EXECUTED','8:45e8f74683ee185713d9990c971ec536','addColumn tableName=programming_exercise_details','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20201102143912','femers','config/liquibase/changelog/20201102143912_changelog.xml','2021-10-30 19:07:38',95,'EXECUTED','8:8601c1c174fe68c8b8fc4c1d4fe94ddd','modifyDataType columnName=registration_confirmation_message, tableName=course','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20201111141743','schultek','config/liquibase/changelog/20201111141743_changelog.xml','2021-10-30 19:07:39',96,'EXECUTED','8:09e744360facd9648dec777d81479d4c','addColumn tableName=programming_exercise_details','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20201117113912','krusche','config/liquibase/changelog/20201117113912_changelog.xml','2021-10-30 19:07:39',97,'EXECUTED','8:0bb5c1b03735451536651ed798b51473','addColumn tableName=result','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20201119174900','femers','config/liquibase/changelog/20201119174900_changelog.xml','2021-10-30 19:07:39',98,'EXECUTED','8:9255affc1d003b80252836b0867f7b99','addColumn tableName=course; sql','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20201222203018','ruscher','config/liquibase/changelog/20201222203018_changelog.xml','2021-10-30 19:07:39',99,'EXECUTED','8:5cab4ae5020d5701e0b1d2c9a67b9df9','addColumn tableName=exam','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20201214214200','krusche','config/liquibase/changelog/20201214214200_changelog.xml','2021-10-30 19:07:40',100,'EXECUTED','8:05a13e24bf903ffd0cddb874212aba7b','dropForeignKeyConstraint baseTableName=result, constraintName=FK3vct9sad5oubthdmq63n58mnp; dropUniqueConstraint constraintName=UC_RESULTSUBMISSION_ID_COL, tableName=result; addForeignKeyConstraint baseTableName=result, constraintName=fk_result_sub...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210104190000-1','stefanwaldhauser','config/liquibase/changelog/20210104190000_changelog.xml','2021-10-30 19:07:40',101,'EXECUTED','8:3b31ef9f4295f422d1dd47490ed81652','addColumn tableName=complaint_response','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210117093301','ivohashamov','config/liquibase/changelog/20210117093301_changelog.xml','2021-10-30 19:07:40',102,'EXECUTED','8:3890ad03cc0f4c07d05e010896afe54d','addColumn tableName=quiz_question','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210120205500','krusche','config/liquibase/changelog/20210120205500_changelog.xml','2021-10-30 19:07:40',103,'EXECUTED','8:47c28d27bfbeac9d5e7d0145f8aa2f5c','modifyDataType columnName=text, tableName=quiz_question','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210120205500','krusche','config/liquibase/changelog/20210124211500_changelog.xml','2021-10-30 19:07:40',104,'EXECUTED','8:66ec7fd17fa77b9242b49b67f25220d7','addColumn tableName=participation','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210125101100','b-fein','config/liquibase/changelog/20210125101100_changelog.xml','2021-10-30 19:07:40',105,'EXECUTED','8:fdc51dc7c3249a6ff7c64606dda5c3a2','addColumn tableName=programming_exercise_test_case; sql; addColumn tableName=feedback','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20211901120000-1','swaldhauser','config/liquibase/changelog/20211901120000_changelog.xml','2021-10-30 19:07:41',106,'EXECUTED','8:76b215ba42e7591c8d4276e07e2525a7','addColumn tableName=exercise','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20211901120000-2','swaldhauser','config/liquibase/changelog/20211901120000_changelog.xml','2021-10-30 19:07:41',107,'EXECUTED','8:ac824ec359ccf269787ad96265eb82bd','addNotNullConstraint columnName=included_in_overall_score, tableName=exercise','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20211901120000-3','swaldhauser','config/liquibase/changelog/20211901120000_changelog.xml','2021-10-30 19:07:41',108,'EXECUTED','8:64a8123fe97041384bdd420033252a11','addDefaultValue columnName=included_in_overall_score, tableName=exercise','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20211901120000-4','swaldhauser','config/liquibase/changelog/20211901120000_changelog.xml','2021-10-30 19:07:41',109,'EXECUTED','8:389d821677b6aa33cb87106e0babb821','sql','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20211901120000-5','swaldhauser','config/liquibase/changelog/20211901120000_changelog.xml','2021-10-30 19:07:42',110,'EXECUTED','8:1ea843acc3835d8cc720a6ffe01569f7','addNotNullConstraint columnName=max_score, tableName=exercise','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20211901120000-6','swaldhauser','config/liquibase/changelog/20211901120000_changelog.xml','2021-10-30 19:07:42',111,'EXECUTED','8:36b70f55994e0e87e72742a59ca52e89','sql','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20211901120000-7','swaldhauser','config/liquibase/changelog/20211901120000_changelog.xml','2021-10-30 19:07:42',112,'EXECUTED','8:b07e0fb726ed00e81387ae4e09c4ac04','addNotNullConstraint columnName=bonus_points, tableName=exercise','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20211901120000-8','swaldhauser','config/liquibase/changelog/20211901120000_changelog.xml','2021-10-30 19:07:42',113,'EXECUTED','8:0a8dd9d8776916c6c6edaf055be6f35b','sql','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20211901120000-9','swaldhauser','config/liquibase/changelog/20211901120000_changelog.xml','2021-10-30 19:07:42',114,'EXECUTED','8:9a944acc2d3a43765168c8a79dcf9eda','sql','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20211901120000-10','swaldhauser','config/liquibase/changelog/20211901120000_changelog.xml','2021-10-30 19:07:43',115,'EXECUTED','8:2ef4596b2314da579536b96e132dbc84','sql','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20211901120000-11','swaldhauser','config/liquibase/changelog/20211901120000_changelog.xml','2021-10-30 19:07:43',116,'EXECUTED','8:0dfa96bd0b4d08cec4dbe3ed5b300199','sql','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20212901101701','chimeno','config/liquibase/changelog/20212901101701_changelog.xml','2021-10-30 19:07:43',117,'EXECUTED','8:be2d12fa7b5e4eef2260be29881820ef','addColumn tableName=course','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20212601155025','simon.entholzer','config/liquibase/changelog/20212601155025_changelog.xml','2021-10-30 19:07:43',118,'EXECUTED','8:9c95264b6731ce756087222ac85aa8d5','addColumn tableName=exercise','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210102120000','stefan.waldhauser','config/liquibase/changelog/20210102120000_changelog.xml','2021-10-30 19:07:44',119,'EXECUTED','8:973c0a25448373eb8a2aab406bf8f327','renameColumn newColumnName=max_points, oldColumnName=max_score, tableName=exercise; renameColumn newColumnName=points, oldColumnName=score, tableName=quiz_question','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210202170000','francisco.delascasasyoung','config/liquibase/changelog/20210202170000_changelog.xml','2021-10-30 19:07:44',120,'EXECUTED','8:6d294c156a09d144c77360d83ddcbfd6','createView viewName=view_tutor_leaderboard_assessments; createView viewName=view_tutor_leaderboard_answered_more_feedback_requests; createView viewName=view_tutor_leaderboard_complaint_responses; createView viewName=view_tutor_leaderboard_complain...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210201173140','philippbauch','config/liquibase/changelog/20210201173102_changelog.xml','2021-10-30 19:07:47',121,'EXECUTED','8:e9dbf17180a6b8fb11f8abc413df52ee','createTable tableName=plagiarism_result; createTable tableName=plagiarism_result_similarity_distribution; createTable tableName=plagiarism_comparison; createTable tableName=plagiarism_comparison_matches; createTable tableName=plagiarism_submission...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210126000000-1','stefanwaldhauser','config/liquibase/changelog/20210126000000_changelog.xml','2021-10-30 19:07:50',122,'EXECUTED','8:12b775e33938394ce6b8245959fef5e2','createTable tableName=participant_score; addForeignKeyConstraint baseTableName=participant_score, constraintName=fk_participant_score_exercise_id, referencedTableName=exercise; addForeignKeyConstraint baseTableName=participant_score, constraintNam...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210126000000-2','stefanwaldhauser','config/liquibase/changelog/20210126000000_changelog.xml','2021-10-30 19:07:50',123,'EXECUTED','8:5528fa0c9d9bad7f15f863e7636309b0','sqlFile','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210218101120-0','daniels98it','config/liquibase/changelog/20210218101120_changelog.xml','2021-10-30 19:07:50',124,'EXECUTED','8:5d30a3a90765da8d8d1669a1f0ae3224','createTable tableName=organization','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210218101120-1','daniels98it','config/liquibase/changelog/20210218101120_changelog.xml','2021-10-30 19:07:50',125,'EXECUTED','8:68d1262ce870d4760e98884c8c278a25','createTable tableName=course_organization','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210218101120-2','daniels98it','config/liquibase/changelog/20210218101120_changelog.xml','2021-10-30 19:07:50',126,'EXECUTED','8:70c65c1b3673ee8bc08deb2e3c0e47de','createTable tableName=user_organization','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210218101120-3','daniels98it','config/liquibase/changelog/20210218101120_changelog.xml','2021-10-30 19:07:50',127,'EXECUTED','8:003043ab0aedd0f002e11f7a3ec04d1d','addPrimaryKey tableName=course_organization','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210218101120-4','daniels98it','config/liquibase/changelog/20210218101120_changelog.xml','2021-10-30 19:07:51',128,'EXECUTED','8:856f33ec9ec989fa47b527f5f55ea11f','addPrimaryKey tableName=user_organization','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210218101120-6','daniels98it','config/liquibase/changelog/20210218101120_changelog.xml','2021-10-30 19:07:51',129,'EXECUTED','8:fac739d8ce77051295f7050f2a5c91d1','addForeignKeyConstraint baseTableName=course_organization, constraintName=FK71cxcwaglhos8x0qxs61vpxkb, referencedTableName=course','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210218101120-7','daniels98it','config/liquibase/changelog/20210218101120_changelog.xml','2021-10-30 19:07:51',130,'EXECUTED','8:85e4d4062dd4ae6b7addd5e8eb56c883','addForeignKeyConstraint baseTableName=course_organization, constraintName=FKdythnvneadrsbvfa3hxd1tq4h, referencedTableName=organization','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210218101120-8','daniels98it','config/liquibase/changelog/20210218101120_changelog.xml','2021-10-30 19:07:52',131,'EXECUTED','8:c26b13f951cbcaf7ee4d87a2c98c5677','addForeignKeyConstraint baseTableName=user_organization, constraintName=FKfdnaj8emi62iffmg6w6ykjxf4, referencedTableName=organization','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210218101120-9','daniels98it','config/liquibase/changelog/20210218101120_changelog.xml','2021-10-30 19:07:52',132,'EXECUTED','8:bcd967d4d454692ad2649130f16e06db','addForeignKeyConstraint baseTableName=user_organization, constraintName=FKnfyuy63doxihtl3pu8rnm46n4, referencedTableName=jhi_user','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210223200000','francisco.delascasasyoung','config/liquibase/changelog/20210223200000_changelog.xml','2021-10-30 19:07:52',133,'EXECUTED','8:913336100f77e02e6889b6787d73d885','sql','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210306211300','krusche','config/liquibase/changelog/20210306211300_changelog.xml','2021-10-30 19:07:52',134,'EXECUTED','8:a057f52849fbb4d4e80b8d37511e7672','modifyDataType columnName=score, tableName=result','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210310090900','krusche','config/liquibase/changelog/20210310090900_changelog.xml','2021-10-30 19:07:53',135,'EXECUTED','8:7630c6a2efc3f3efd3d96e33647de7e5','dropView viewName=view_tutor_leaderboard_answered_more_feedback_requests; dropView viewName=view_tutor_leaderboard_assessments; dropView viewName=view_tutor_leaderboard_complaint_responses; dropView viewName=view_tutor_leaderboard_complaints; drop...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210319163148','ivohashamov','config/liquibase/changelog/20210319163148_changelog.xml','2021-10-30 19:07:54',136,'EXECUTED','8:e9520acf809b2bf94fe8c3a52d5c9fe9','createTable tableName=grade_step; createTable tableName=grading_scale; addForeignKeyConstraint baseTableName=grade_step, constraintName=FKiub3ue9adasdas24v9ns656n, referencedTableName=grading_scale; addForeignKeyConstraint baseTableName=grading_sc...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210317174601','chimeno','config/liquibase/changelog/20210317174601_changelog.xml','2021-10-30 19:07:54',137,'EXECUTED','8:7389326321d086a4eed6d912e103cd1f','addColumn tableName=exam','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210321130000','franke','config/liquibase/changelog/20210321130000_changelog.xml','2021-10-30 19:07:59',138,'EXECUTED','8:d84951588963242ef9fcff512172d318','createIndex indexName=submission_date, tableName=submission; createIndex indexName=submission_discriminator, tableName=submission; createIndex indexName=exercise_release_date, tableName=exercise; createIndex indexName=exercise_due_date, tableName=...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210413105400','chimeno','config/liquibase/changelog/20210413105400_changelog.xml','2021-10-30 19:07:59',139,'EXECUTED','8:b6f24c15e8025a3d59fddb390c3837de','createIndex indexName=jhi_type, tableName=submission','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210417123460','fglombik','config/liquibase/changelog/20210417123460_changelog.xml','2021-10-30 19:07:59',140,'EXECUTED','8:bfd671e008a5f7f7a17ca77d3b54f036','addColumn tableName=course; sql','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210507092500','joschahenningsen','config/liquibase/changelog/20210507092500_changelog.xml','2021-10-30 19:07:59',141,'EXECUTED','8:324696cf204bfe0478bc7b8c87541823','modifyDataType columnName=file, tableName=plagiarism_submission_element','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210515191046','niclasschuemann','config/liquibase/changelog/20210515191046_changelog.xml','2021-10-30 19:08:00',142,'EXECUTED','8:da44e834733d086f2b5d72216c4ca72c','createTable tableName=programming_exercise_auxiliary_repositories; createIndex indexName=fk_auxiliary_repository_exercise, tableName=programming_exercise_auxiliary_repositories; addForeignKeyConstraint baseTableName=programming_exercise_auxiliary_...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210519162242','niclasschuemann','config/liquibase/changelog/20210519162242_changelog.xml','2021-10-30 19:08:00',143,'EXECUTED','8:f298e57c8aa291403a3f1a1ce88f5fcc','addColumn tableName=programming_exercise_auxiliary_repositories','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210528104100','schlesinger','config/liquibase/changelog/20210528104100_changelog.xml','2021-10-30 19:08:01',144,'EXECUTED','8:78b0d66abedbd5564cad2dcca6b3854e','renameTable newTableName=post, oldTableName=student_question; renameTable newTableName=answer_post, oldTableName=student_question_answer; renameColumn newColumnName=content, oldColumnName=question_text, tableName=post; renameColumn newColumnName=c...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210528163200','krusche','config/liquibase/changelog/20210528163200_changelog.xml','2021-10-30 19:08:03',145,'EXECUTED','8:61f373e67ffffda622e05e832da4205d','createTable tableName=model_element; createTable tableName=model_cluster; addForeignKeyConstraint baseTableName=model_element, constraintName=fk_model_element_submission_id, referencedTableName=submission; addForeignKeyConstraint baseTableName=mod...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210605180002','hashamov','config/liquibase/changelog/20210605180002_changelog.xml','2021-10-30 19:08:03',146,'EXECUTED','8:80a46cbce6fcc4e3a160144bfa4529a1','addColumn tableName=course','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210603110700','schlesinger','config/liquibase/changelog/20210624110700_changelog.xml','2021-10-30 19:08:05',147,'EXECUTED','8:df11848055527610792314d98625e4cc','createTable tableName=post_tag; createIndex indexName=fk_post_tag_post_id, tableName=post_tag; addForeignKeyConstraint baseTableName=post_tag, constraintName=fk_post_tag_post_id, referencedTableName=post; addColumn tableName=post; addForeignKeyCon...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210711080808-1','argertboja','config/liquibase/changelog/20210711080808_changelog.xml','2021-10-30 19:08:06',148,'EXECUTED','8:bd389745ed56ce3d4e9b8d37c46d6327','createTable tableName=text_assessment_knowledge; createTable tableName=text_exercise_details; addForeignKeyConstraint baseTableName=text_exercise_details, constraintName=fk_text_exercise_knowledge_id, referencedTableName=text_assessment_knowledge;...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210711080808-2','argertboja','config/liquibase/changelog/20210711080808_changelog.xml','2021-10-30 19:08:08',149,'EXECUTED','8:852476c6974c3676925ac835e93e152f','createTable tableName=model_assessment_knowledge; createTable tableName=model_exercise_details; addForeignKeyConstraint baseTableName=model_exercise_details, constraintName=fk_model_exercise_knowledge_id, referencedTableName=model_assessment_knowl...','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210711080808-3','argertboja','config/liquibase/changelog/20210711080808_changelog.xml','2021-10-30 19:08:08',150,'EXECUTED','8:01b99bd3b0905bdb93987edcf059ef6f','sql; sql','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210707101700','ndricimrr','config/liquibase/changelog/20210707101700_changelog.xml','2021-10-30 19:08:08',151,'EXECUTED','8:8aa3ca1467716fec17877bf7fec4f9d1','createTable tableName=text_assessment_event','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210720132912','stoehrj','config/liquibase/changelog/20210720132912_changelog.xml','2021-10-30 19:08:08',152,'EXECUTED','8:303708edcd3596276ac5faf2f4f4742f','addColumn tableName=programming_exercise_details','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210722093000','b-fein','config/liquibase/changelog/20210722093000_changelog.xml','2021-10-30 19:08:08',153,'EXECUTED','8:c2c1b507c34065a949c5d5624d86c86e','dropColumn columnName=after_due_date, tableName=programming_exercise_test_case','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210802213000','schlesinger','config/liquibase/changelog/20210802213000_changelog.xml','2021-10-30 19:08:09',154,'EXECUTED','8:490f03088a03d28fd0207eae9d05eecb','modifyDataType columnName=emoji_id, tableName=reaction','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210812152000','schlesinger','config/liquibase/changelog/20210812152000_changelog.xml','2021-10-30 19:08:09',155,'EXECUTED','8:c0ceda9a4e9f3aa6f5819c6a5e922e4b','addColumn tableName=post','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210813152000','ndricimrr','config/liquibase/changelog/20210813152000_changelog.xml','2021-10-30 19:08:09',156,'EXECUTED','8:26b94e878bbc5f69a102fa99291bb281','addColumn tableName=text_cluster','',NULL,'4.5.0',NULL,NULL,'5620761397'),('20210818133200','stoehrj','config/liquibase/changelog/20210818133200_changelog.xml','2021-10-30 19:08:09',157,'EXECUTED','8:d37ecdad169d0ca126bf27c9406fe407','addColumn tableName=exercise; sql','',NULL,'4.5.0',NULL,NULL,'5620761397');
/*!40000 ALTER TABLE `DATABASECHANGELOG` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `DATABASECHANGELOGLOCK`
--

DROP TABLE IF EXISTS `DATABASECHANGELOGLOCK`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DATABASECHANGELOGLOCK` (
  `ID` int NOT NULL,
  `LOCKED` bit(1) NOT NULL,
  `LOCKGRANTED` datetime DEFAULT NULL,
  `LOCKEDBY` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `DATABASECHANGELOGLOCK`
--

LOCK TABLES `DATABASECHANGELOGLOCK` WRITE;
/*!40000 ALTER TABLE `DATABASECHANGELOGLOCK` DISABLE KEYS */;
INSERT INTO `DATABASECHANGELOGLOCK` VALUES (1,_binary '\0',NULL,NULL);
/*!40000 ALTER TABLE `DATABASECHANGELOGLOCK` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `answer_option`
--

DROP TABLE IF EXISTS `answer_option`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `answer_option` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `is_correct` bit(1) DEFAULT NULL,
  `text` varchar(255) DEFAULT NULL,
  `question_id` bigint DEFAULT NULL,
  `explanation` varchar(255) DEFAULT NULL,
  `hint` varchar(255) DEFAULT NULL,
  `answer_options_order` int DEFAULT NULL,
  `invalid` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKfqeqisl0e28xp3yn9bmlgkhej` (`question_id`),
  CONSTRAINT `FKfqeqisl0e28xp3yn9bmlgkhej` FOREIGN KEY (`question_id`) REFERENCES `quiz_question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `answer_option`
--

LOCK TABLES `answer_option` WRITE;
/*!40000 ALTER TABLE `answer_option` DISABLE KEYS */;
/*!40000 ALTER TABLE `answer_option` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `answer_post`
--

DROP TABLE IF EXISTS `answer_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `answer_post` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creation_date` datetime DEFAULT NULL,
  `content` longtext,
  `author_id` bigint DEFAULT NULL,
  `post_id` bigint DEFAULT NULL,
  `tutor_approved` bit(1) DEFAULT NULL,
  `tokenized_content` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgdd6qiyjtlrjgf2dsse32r8y5` (`author_id`),
  KEY `FKfe9vnvra5xx2kulv4h27seksa` (`post_id`),
  CONSTRAINT `FKfe9vnvra5xx2kulv4h27seksa` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`),
  CONSTRAINT `FKgdd6qiyjtlrjgf2dsse32r8y5` FOREIGN KEY (`author_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `answer_post`
--

LOCK TABLES `answer_post` WRITE;
/*!40000 ALTER TABLE `answer_post` DISABLE KEYS */;
/*!40000 ALTER TABLE `answer_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `apollon_diagram`
--

DROP TABLE IF EXISTS `apollon_diagram`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `apollon_diagram` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `json_representation` longtext,
  `title` varchar(255) DEFAULT NULL,
  `diagram_type` varchar(255) DEFAULT NULL,
  `course_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `apollon_diagram`
--

LOCK TABLES `apollon_diagram` WRITE;
/*!40000 ALTER TABLE `apollon_diagram` DISABLE KEYS */;
/*!40000 ALTER TABLE `apollon_diagram` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `attachment`
--

DROP TABLE IF EXISTS `attachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attachment_type` varchar(255) DEFAULT NULL,
  `jhi_link` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `release_date` datetime DEFAULT NULL,
  `exercise_id` bigint DEFAULT NULL,
  `lecture_id` bigint DEFAULT NULL,
  `upload_date` datetime DEFAULT NULL,
  `version` int DEFAULT NULL,
  `attachment_unit_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK840kdpn5r9l2wyhq83gl6sx2g` (`exercise_id`),
  KEY `FKa6apwe76xlljxtm3nimrlvppf` (`lecture_id`),
  KEY `FKc5ylgocmwyffladuv6454evje` (`attachment_unit_id`),
  CONSTRAINT `FK840kdpn5r9l2wyhq83gl6sx2g` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `FKa6apwe76xlljxtm3nimrlvppf` FOREIGN KEY (`lecture_id`) REFERENCES `lecture` (`id`),
  CONSTRAINT `FKc5ylgocmwyffladuv6454evje` FOREIGN KEY (`attachment_unit_id`) REFERENCES `lecture_unit` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `attachment`
--

LOCK TABLES `attachment` WRITE;
/*!40000 ALTER TABLE `attachment` DISABLE KEYS */;
/*!40000 ALTER TABLE `attachment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `build_log_entry`
--

DROP TABLE IF EXISTS `build_log_entry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `build_log_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `log` varchar(255) DEFAULT NULL,
  `time` datetime(6) DEFAULT NULL,
  `programming_submission_id` bigint DEFAULT NULL,
  `build_log_entries_order` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKarrnc5l01jjyixrw6jbu18k6a` (`programming_submission_id`),
  CONSTRAINT `FKarrnc5l01jjyixrw6jbu18k6a` FOREIGN KEY (`programming_submission_id`) REFERENCES `submission` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `build_log_entry`
--

LOCK TABLES `build_log_entry` WRITE;
/*!40000 ALTER TABLE `build_log_entry` DISABLE KEYS */;
/*!40000 ALTER TABLE `build_log_entry` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `complaint`
--

DROP TABLE IF EXISTS `complaint`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `complaint` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `accepted` bit(1) DEFAULT NULL,
  `complaint_text` varchar(2000) DEFAULT NULL,
  `result_before_complaint` longtext,
  `submitted_time` datetime DEFAULT NULL,
  `result_id` bigint DEFAULT NULL,
  `student_id` bigint DEFAULT NULL,
  `team_id` bigint DEFAULT NULL,
  `complaint_type` varchar(15) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_COMPLAINTRESULT_ID_COL` (`result_id`),
  KEY `fk_complaint_student_id` (`student_id`),
  KEY `fk_complaint_team_id` (`team_id`),
  KEY `complaint_submitted_time` (`submitted_time`),
  KEY `complaint_type` (`complaint_type`),
  CONSTRAINT `fk_complaint_student_id` FOREIGN KEY (`student_id`) REFERENCES `jhi_user` (`id`),
  CONSTRAINT `fk_complaint_team_id` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`),
  CONSTRAINT `FKgyh851bnmsti3b0tmvtdf1y97` FOREIGN KEY (`result_id`) REFERENCES `result` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `complaint`
--

LOCK TABLES `complaint` WRITE;
/*!40000 ALTER TABLE `complaint` DISABLE KEYS */;
/*!40000 ALTER TABLE `complaint` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `complaint_response`
--

DROP TABLE IF EXISTS `complaint_response`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `complaint_response` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `response_text` varchar(2000) DEFAULT NULL,
  `submitted_time` datetime DEFAULT NULL,
  `complaint_id` bigint DEFAULT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `created_by` varchar(45) DEFAULT NULL,
  `created_date` timestamp NULL DEFAULT NULL,
  `last_modified_by` varchar(45) DEFAULT NULL,
  `last_modified_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_COMPLAINT_RESPONSECOMPLAINT_ID_COL` (`complaint_id`),
  KEY `fk_complaint_response_reviwer_id` (`reviewer_id`),
  KEY `complaint_response_submitted_time` (`submitted_time`),
  CONSTRAINT `fk_complaint_response_reviwer_id` FOREIGN KEY (`reviewer_id`) REFERENCES `jhi_user` (`id`),
  CONSTRAINT `FKrwjscojfvjqea2hl24gvl5yvb` FOREIGN KEY (`complaint_id`) REFERENCES `complaint` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `complaint_response`
--

LOCK TABLES `complaint_response` WRITE;
/*!40000 ALTER TABLE `complaint_response` DISABLE KEYS */;
/*!40000 ALTER TABLE `complaint_response` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course`
--

DROP TABLE IF EXISTS `course`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `course` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) DEFAULT NULL,
  `student_group_name` varchar(255) DEFAULT NULL,
  `teaching_assistant_group_name` varchar(255) DEFAULT NULL,
  `end_date` datetime(6) DEFAULT NULL,
  `instructor_group_name` varchar(255) DEFAULT NULL,
  `online_course` bit(1) DEFAULT NULL,
  `start_date` datetime(6) DEFAULT NULL,
  `short_name` varchar(255) DEFAULT NULL,
  `max_complaints` int NOT NULL,
  `max_team_complaints` int DEFAULT '3',
  `description` longtext,
  `color` varchar(255) DEFAULT NULL,
  `course_icon` varchar(255) DEFAULT NULL,
  `registration_enabled` bit(1) DEFAULT NULL,
  `presentation_score` tinyint DEFAULT NULL,
  `max_complaint_time_days` int NOT NULL DEFAULT '7',
  `posts_enabled` bit(1) DEFAULT NULL,
  `registration_confirmation_message` varchar(2000) DEFAULT NULL,
  `semester` varchar(25) DEFAULT NULL,
  `test_course` bit(1) NOT NULL DEFAULT b'0',
  `language` varchar(25) DEFAULT NULL,
  `max_request_more_feedback_time_days` int NOT NULL DEFAULT '7',
  `course_archive_path` varchar(255) DEFAULT NULL,
  `editor_group_name` varchar(255) DEFAULT NULL,
  `max_points` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_COURSE_SHORT_NAME` (`short_name`),
  KEY `course_start_date` (`start_date`),
  KEY `course_end_date` (`end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course`
--

LOCK TABLES `course` WRITE;
/*!40000 ALTER TABLE `course` DISABLE KEYS */;
/*!40000 ALTER TABLE `course` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_organization`
--

DROP TABLE IF EXISTS `course_organization`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `course_organization` (
  `course_id` bigint NOT NULL,
  `organization_id` bigint NOT NULL,
  PRIMARY KEY (`organization_id`,`course_id`),
  KEY `FK71cxcwaglhos8x0qxs61vpxkb` (`course_id`),
  CONSTRAINT `FK71cxcwaglhos8x0qxs61vpxkb` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
  CONSTRAINT `FKdythnvneadrsbvfa3hxd1tq4h` FOREIGN KEY (`organization_id`) REFERENCES `organization` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_organization`
--

LOCK TABLES `course_organization` WRITE;
/*!40000 ALTER TABLE `course_organization` DISABLE KEYS */;
/*!40000 ALTER TABLE `course_organization` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `drag_and_drop_mapping`
--

DROP TABLE IF EXISTS `drag_and_drop_mapping`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `drag_and_drop_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `drag_item_index` int DEFAULT NULL,
  `drop_location_index` int DEFAULT NULL,
  `drag_item_id` bigint DEFAULT NULL,
  `drop_location_id` bigint DEFAULT NULL,
  `question_id` bigint DEFAULT NULL,
  `submitted_answer_id` bigint DEFAULT NULL,
  `correct_mappings_order` int DEFAULT NULL,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `drag_and_drop_mapping`
--

LOCK TABLES `drag_and_drop_mapping` WRITE;
/*!40000 ALTER TABLE `drag_and_drop_mapping` DISABLE KEYS */;
/*!40000 ALTER TABLE `drag_and_drop_mapping` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `drag_item`
--

DROP TABLE IF EXISTS `drag_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `drag_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `picture_file_path` varchar(255) DEFAULT NULL,
  `text` varchar(255) DEFAULT NULL,
  `question_id` bigint DEFAULT NULL,
  `drag_items_order` int DEFAULT NULL,
  `invalid` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6qk07o5uhre61g1egc064wmmc` (`question_id`),
  CONSTRAINT `FK6qk07o5uhre61g1egc064wmmc` FOREIGN KEY (`question_id`) REFERENCES `quiz_question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `drag_item`
--

LOCK TABLES `drag_item` WRITE;
/*!40000 ALTER TABLE `drag_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `drag_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `drop_location`
--

DROP TABLE IF EXISTS `drop_location`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `drop_location` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `height` double DEFAULT NULL,
  `pos_x` double DEFAULT NULL,
  `pos_y` double DEFAULT NULL,
  `width` double DEFAULT NULL,
  `question_id` bigint DEFAULT NULL,
  `drop_locations_order` int DEFAULT NULL,
  `invalid` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKlbu6qm8ykqhtb5xm911bg5ct7` (`question_id`),
  CONSTRAINT `FKlbu6qm8ykqhtb5xm911bg5ct7` FOREIGN KEY (`question_id`) REFERENCES `quiz_question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `drop_location`
--

LOCK TABLES `drop_location` WRITE;
/*!40000 ALTER TABLE `drop_location` DISABLE KEYS */;
/*!40000 ALTER TABLE `drop_location` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `exam`
--

DROP TABLE IF EXISTS `exam`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `exam` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `confirmation_end_text` longtext,
  `confirmation_start_text` longtext,
  `end_date` datetime DEFAULT NULL,
  `end_text` longtext,
  `max_points` int DEFAULT NULL,
  `number_of_exercises_in_exam` int DEFAULT NULL,
  `randomize_exercise_order` bit(1) DEFAULT NULL,
  `start_date` datetime DEFAULT NULL,
  `start_text` longtext,
  `title` varchar(255) NOT NULL,
  `visible_date` datetime DEFAULT NULL,
  `course_id` bigint DEFAULT NULL,
  `exam_student_review_end` datetime(6) DEFAULT NULL,
  `exam_student_review_start` datetime(6) DEFAULT NULL,
  `publish_results_date` datetime(6) DEFAULT NULL,
  `grace_period` int DEFAULT '180',
  `examiner` varchar(255) DEFAULT NULL,
  `module_number` varchar(255) DEFAULT NULL,
  `course_name` varchar(255) DEFAULT NULL,
  `number_of_correction_rounds` int DEFAULT '1',
  `exam_archive_path` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKiub3ue9cklcyyra24v9ns656n` (`course_id`),
  KEY `exam_visible_date` (`visible_date`),
  KEY `exam_start_date` (`start_date`),
  KEY `exam_end_date` (`end_date`),
  KEY `exam_exam_student_review_start` (`exam_student_review_start`),
  KEY `exam_exam_student_review_end` (`exam_student_review_end`),
  KEY `exam_publish_results_date` (`publish_results_date`),
  CONSTRAINT `FKiub3ue9cklcyyra24v9ns656n` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exam`
--

LOCK TABLES `exam` WRITE;
/*!40000 ALTER TABLE `exam` DISABLE KEYS */;
/*!40000 ALTER TABLE `exam` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `exam_session`
--

DROP TABLE IF EXISTS `exam_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `exam_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_exam_id` bigint DEFAULT NULL,
  `session_token` varchar(16) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `browser_fingerprint_hash` varchar(60) DEFAULT NULL,
  `created_by` varchar(45) DEFAULT NULL,
  `last_modified_by` varchar(45) DEFAULT NULL,
  `instance_id` varchar(60) DEFAULT NULL,
  `ip_address` varchar(48) DEFAULT NULL,
  `last_modified_date` timestamp NULL DEFAULT NULL,
  `created_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6pok2pdckbv609q1kjcxnguha` (`student_exam_id`),
  CONSTRAINT `FK6pok2pdckbv609q1kjcxnguha` FOREIGN KEY (`student_exam_id`) REFERENCES `student_exam` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exam_session`
--

LOCK TABLES `exam_session` WRITE;
/*!40000 ALTER TABLE `exam_session` DISABLE KEYS */;
/*!40000 ALTER TABLE `exam_session` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `exam_user`
--

DROP TABLE IF EXISTS `exam_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `exam_user` (
  `exam_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  PRIMARY KEY (`exam_id`,`student_id`),
  KEY `FK9kic2piykbv64dc8kjcxnlagu` (`student_id`),
  CONSTRAINT `FK9kic2piykbv64dc8kjcxnlagu` FOREIGN KEY (`student_id`) REFERENCES `jhi_user` (`id`),
  CONSTRAINT `FKpvet665enmcbtnoecju2epqmp` FOREIGN KEY (`exam_id`) REFERENCES `exam` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exam_user`
--

LOCK TABLES `exam_user` WRITE;
/*!40000 ALTER TABLE `exam_user` DISABLE KEYS */;
/*!40000 ALTER TABLE `exam_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `example_submission`
--

DROP TABLE IF EXISTS `example_submission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `example_submission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `used_for_tutorial` bit(1) DEFAULT NULL,
  `exercise_id` bigint DEFAULT NULL,
  `submission_id` bigint DEFAULT NULL,
  `assessment_explanation` varchar(2000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_EXAMPLE_SUBMISSIONSUBMISSION_ID_COL` (`submission_id`),
  KEY `FKannvxhl5djc6qrwntuip91egx` (`exercise_id`),
  CONSTRAINT `FKannvxhl5djc6qrwntuip91egx` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `FKbkom4atu3covr3trrbldf6bvn` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `example_submission`
--

LOCK TABLES `example_submission` WRITE;
/*!40000 ALTER TABLE `example_submission` DISABLE KEYS */;
/*!40000 ALTER TABLE `example_submission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `exercise`
--

DROP TABLE IF EXISTS `exercise`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `exercise` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) DEFAULT NULL,
  `release_date` timestamp NULL DEFAULT NULL,
  `due_date` timestamp NULL DEFAULT NULL,
  `course_id` bigint DEFAULT NULL,
  `publish_build_plan_url` bit(1) DEFAULT NULL,
  `discriminator` varchar(31) NOT NULL,
  `duration` int DEFAULT NULL,
  `is_open_for_practice` bit(1) DEFAULT NULL,
  `is_planned_to_start` bit(1) DEFAULT NULL,
  `is_visible_before_start` bit(1) DEFAULT NULL,
  `allowed_number_of_attempts` int DEFAULT NULL,
  `randomize_question_order` bit(1) DEFAULT NULL,
  `quiz_point_statistic_id` bigint DEFAULT NULL,
  `max_points` double DEFAULT NULL,
  `diagram_type` varchar(255) DEFAULT NULL,
  `difficulty` varchar(255) DEFAULT NULL,
  `mode` varchar(255) NOT NULL DEFAULT 'INDIVIDUAL',
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
  `solution_participation_id` bigint DEFAULT NULL,
  `template_participation_id` bigint DEFAULT NULL,
  `sequential_test_runs` bit(1) DEFAULT NULL,
  `assessment_type` varchar(25) DEFAULT NULL,
  `presentation_score_enabled` bit(1) DEFAULT NULL,
  `team_assignment_config_id` bigint DEFAULT NULL,
  `exercise_group_id` bigint DEFAULT NULL,
  `bonus_points` double NOT NULL,
  `included_in_overall_score` varchar(255) NOT NULL DEFAULT 'INCLUDED_COMPLETELY',
  `second_correction_enabled` bit(1) NOT NULL DEFAULT b'0',
  `allow_complaints_for_automatic_assessments` bit(1) DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_EXERCISEQUIZ_POINT_STATISTIC_ID_COL` (`quiz_point_statistic_id`),
  UNIQUE KEY `UC_EXERCISESOLUTION_PARTICIPATION_ID_COL` (`solution_participation_id`),
  UNIQUE KEY `UC_EXERCISETEMPLATE_PARTICIPATION_ID_COL` (`template_participation_id`),
  KEY `fk_exercise_course_id` (`course_id`),
  KEY `fk_exercise_team_assignment_config_id` (`team_assignment_config_id`),
  KEY `FK8cduvvvsh3ki4y8ssmm8r6hmb` (`exercise_group_id`),
  KEY `exercise_release_date` (`release_date`),
  KEY `exercise_due_date` (`due_date`),
  KEY `exercise_assessment_due_date` (`assessment_due_date`),
  KEY `exercise_discriminator` (`discriminator`),
  CONSTRAINT `FK1kyxexi1uqeu8pw3vcbulfi42` FOREIGN KEY (`quiz_point_statistic_id`) REFERENCES `quiz_statistic` (`id`),
  CONSTRAINT `FK4ldf9949g6c5n7a79sr6rkt5n` FOREIGN KEY (`solution_participation_id`) REFERENCES `participation` (`id`),
  CONSTRAINT `FK8cduvvvsh3ki4y8ssmm8r6hmb` FOREIGN KEY (`exercise_group_id`) REFERENCES `exercise_group` (`id`),
  CONSTRAINT `FK91br4p8i0d3owf75pqxpnc3o0` FOREIGN KEY (`template_participation_id`) REFERENCES `participation` (`id`),
  CONSTRAINT `fk_exercise_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
  CONSTRAINT `fk_exercise_team_assignment_config_id` FOREIGN KEY (`team_assignment_config_id`) REFERENCES `team_assignment_config` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exercise`
--

LOCK TABLES `exercise` WRITE;
/*!40000 ALTER TABLE `exercise` DISABLE KEYS */;
/*!40000 ALTER TABLE `exercise` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `exercise_categories`
--

DROP TABLE IF EXISTS `exercise_categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `exercise_categories` (
  `exercise_id` bigint NOT NULL,
  `categories` varchar(255) DEFAULT NULL,
  KEY `FKqdemq87pe28lexkg6kh73vueg` (`exercise_id`),
  CONSTRAINT `FKqdemq87pe28lexkg6kh73vueg` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exercise_categories`
--

LOCK TABLES `exercise_categories` WRITE;
/*!40000 ALTER TABLE `exercise_categories` DISABLE KEYS */;
/*!40000 ALTER TABLE `exercise_categories` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `exercise_group`
--

DROP TABLE IF EXISTS `exercise_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `exercise_group` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `is_mandatory` bit(1) NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `exam_id` bigint DEFAULT NULL,
  `exercise_group_order` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKfcsbwug74umq05n5g5lnbku85` (`exam_id`),
  CONSTRAINT `FKfcsbwug74umq05n5g5lnbku85` FOREIGN KEY (`exam_id`) REFERENCES `exam` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exercise_group`
--

LOCK TABLES `exercise_group` WRITE;
/*!40000 ALTER TABLE `exercise_group` DISABLE KEYS */;
/*!40000 ALTER TABLE `exercise_group` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `exercise_hint`
--

DROP TABLE IF EXISTS `exercise_hint`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `exercise_hint` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) DEFAULT NULL,
  `content` longtext,
  `exercise_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_exercise_hint_exercise_id` (`exercise_id`),
  CONSTRAINT `fk_exercise_hint_exercise_id` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `exercise_hint`
--

LOCK TABLES `exercise_hint` WRITE;
/*!40000 ALTER TABLE `exercise_hint` DISABLE KEYS */;
/*!40000 ALTER TABLE `exercise_hint` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `feedback`
--

DROP TABLE IF EXISTS `feedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `detail_text` varchar(5000) DEFAULT NULL,
  `text` varchar(500) DEFAULT NULL,
  `result_id` bigint DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `positive` bit(1) DEFAULT NULL,
  `feedbacks_order` int DEFAULT NULL,
  `credits` double DEFAULT NULL,
  `reference` varchar(2000) DEFAULT NULL,
  `grading_instruction_id` bigint DEFAULT NULL,
  `visibility` varchar(255) DEFAULT 'ALWAYS',
  PRIMARY KEY (`id`),
  KEY `FKmaiyumo49qyke2ijh8h575yy2` (`result_id`),
  KEY `fk_feedback_grading_instruction_id` (`grading_instruction_id`),
  KEY `feedback_type` (`type`),
  CONSTRAINT `feedback_ibfk_1` FOREIGN KEY (`result_id`) REFERENCES `result` (`id`),
  CONSTRAINT `fk_feedback_grading_instruction_id` FOREIGN KEY (`grading_instruction_id`) REFERENCES `grading_instruction` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `feedback`
--

LOCK TABLES `feedback` WRITE;
/*!40000 ALTER TABLE `feedback` DISABLE KEYS */;
/*!40000 ALTER TABLE `feedback` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `feedback_conflict`
--

DROP TABLE IF EXISTS `feedback_conflict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `feedback_conflict` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conflict` bit(1) NOT NULL,
  `created_at` timestamp NULL DEFAULT NULL,
  `solved_at` timestamp NULL DEFAULT NULL,
  `type` varchar(50) NOT NULL,
  `first_feedback_id` bigint NOT NULL,
  `second_feedback_id` bigint NOT NULL,
  `discard` bit(1) DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `fk_first_feedback_id` (`first_feedback_id`),
  KEY `fk_second_feedback_id` (`second_feedback_id`),
  CONSTRAINT `fk_first_feedback_id` FOREIGN KEY (`first_feedback_id`) REFERENCES `feedback` (`id`),
  CONSTRAINT `fk_second_feedback_id` FOREIGN KEY (`second_feedback_id`) REFERENCES `feedback` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `feedback_conflict`
--

LOCK TABLES `feedback_conflict` WRITE;
/*!40000 ALTER TABLE `feedback_conflict` DISABLE KEYS */;
/*!40000 ALTER TABLE `feedback_conflict` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `grade_step`
--

DROP TABLE IF EXISTS `grade_step`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `grade_step` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lower_bound_percentage` double NOT NULL,
  `upper_bound_percentage` double NOT NULL,
  `lower_bound_inclusive` bit(1) NOT NULL DEFAULT b'1',
  `upper_bound_inclusive` bit(1) NOT NULL DEFAULT b'0',
  `grade_name` varchar(100) NOT NULL,
  `is_passing_grade` bit(1) NOT NULL,
  `grading_scale_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKiub3ue9adasdas24v9ns656n` (`grading_scale_id`),
  CONSTRAINT `FKiub3ue9adasdas24v9ns656n` FOREIGN KEY (`grading_scale_id`) REFERENCES `grading_scale` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `grade_step`
--

LOCK TABLES `grade_step` WRITE;
/*!40000 ALTER TABLE `grade_step` DISABLE KEYS */;
/*!40000 ALTER TABLE `grade_step` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `grading_criterion`
--

DROP TABLE IF EXISTS `grading_criterion`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `grading_criterion` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) DEFAULT NULL,
  `exercise_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_grading_criterion_exercise_id` (`exercise_id`),
  CONSTRAINT `fk_grading_criterion_exercise_id` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `grading_criterion`
--

LOCK TABLES `grading_criterion` WRITE;
/*!40000 ALTER TABLE `grading_criterion` DISABLE KEYS */;
/*!40000 ALTER TABLE `grading_criterion` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `grading_instruction`
--

DROP TABLE IF EXISTS `grading_instruction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `grading_instruction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `credits` double DEFAULT NULL,
  `grading_scale` varchar(255) DEFAULT NULL,
  `instruction_description` longtext,
  `feedback` longtext,
  `usage_count` int DEFAULT NULL,
  `grading_criterion_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_grading_instruction_grading_criterion_id` (`grading_criterion_id`),
  CONSTRAINT `fk_grading_instruction_grading_criterion_id` FOREIGN KEY (`grading_criterion_id`) REFERENCES `grading_criterion` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `grading_instruction`
--

LOCK TABLES `grading_instruction` WRITE;
/*!40000 ALTER TABLE `grading_instruction` DISABLE KEYS */;
/*!40000 ALTER TABLE `grading_instruction` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `grading_scale`
--

DROP TABLE IF EXISTS `grading_scale`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `grading_scale` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `grade_type` varchar(10) NOT NULL DEFAULT 'NONE',
  `course_id` bigint DEFAULT NULL,
  `exam_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKiub3uexsbasdas24v9ns656n` (`course_id`),
  KEY `FKiub3ue9adasdas2789ns656n` (`exam_id`),
  CONSTRAINT `FKiub3ue9adasdas2789ns656n` FOREIGN KEY (`exam_id`) REFERENCES `exam` (`id`),
  CONSTRAINT `FKiub3uexsbasdas24v9ns656n` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `grading_scale`
--

LOCK TABLES `grading_scale` WRITE;
/*!40000 ALTER TABLE `grading_scale` DISABLE KEYS */;
/*!40000 ALTER TABLE `grading_scale` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `guided_tour_setting`
--

DROP TABLE IF EXISTS `guided_tour_setting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `guided_tour_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `guided_tour_key` varchar(255) DEFAULT NULL,
  `guided_tour_step` int DEFAULT NULL,
  `guided_tour_state` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKdbjfaktpewig8lac4jimhf34l` (`user_id`),
  CONSTRAINT `FKdbjfaktpewig8lac4jimhf34l` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `guided_tour_setting`
--

LOCK TABLES `guided_tour_setting` WRITE;
/*!40000 ALTER TABLE `guided_tour_setting` DISABLE KEYS */;
/*!40000 ALTER TABLE `guided_tour_setting` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jhi_authority`
--

DROP TABLE IF EXISTS `jhi_authority`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `jhi_authority` (
  `name` varchar(50) NOT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jhi_authority`
--

LOCK TABLES `jhi_authority` WRITE;
/*!40000 ALTER TABLE `jhi_authority` DISABLE KEYS */;
INSERT INTO `jhi_authority` VALUES ('ROLE_ADMIN'),('ROLE_EDITOR'),('ROLE_INSTRUCTOR'),('ROLE_TA'),('ROLE_USER');
/*!40000 ALTER TABLE `jhi_authority` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jhi_date_time_wrapper`
--

DROP TABLE IF EXISTS `jhi_date_time_wrapper`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `jhi_date_time_wrapper` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `instant` timestamp NULL DEFAULT NULL,
  `local_date_time` timestamp NULL DEFAULT NULL,
  `offset_date_time` timestamp NULL DEFAULT NULL,
  `zoned_date_time` timestamp NULL DEFAULT NULL,
  `local_time` time DEFAULT NULL,
  `offset_time` time DEFAULT NULL,
  `local_date` date DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jhi_date_time_wrapper`
--

LOCK TABLES `jhi_date_time_wrapper` WRITE;
/*!40000 ALTER TABLE `jhi_date_time_wrapper` DISABLE KEYS */;
/*!40000 ALTER TABLE `jhi_date_time_wrapper` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jhi_persistent_audit_event`
--

DROP TABLE IF EXISTS `jhi_persistent_audit_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `jhi_persistent_audit_event` (
  `event_id` bigint NOT NULL AUTO_INCREMENT,
  `principal` varchar(255) NOT NULL,
  `event_date` timestamp NULL DEFAULT NULL,
  `event_type` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  KEY `idx_persistent_audit_event` (`principal`,`event_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jhi_persistent_audit_event`
--

LOCK TABLES `jhi_persistent_audit_event` WRITE;
/*!40000 ALTER TABLE `jhi_persistent_audit_event` DISABLE KEYS */;
INSERT INTO `jhi_persistent_audit_event` VALUES (1,'artemis_admin','2021-10-27 15:37:28','AUTHENTICATION_SUCCESS'),(2,'artemis_admin','2021-10-30 07:50:15','AUTHENTICATION_SUCCESS'),(3,'user100','2021-10-30 08:01:42','AUTHENTICATION_FAILURE'),(4,'user_100','2021-10-30 08:01:51','AUTHENTICATION_SUCCESS'),(5,'artemis_admin','2021-10-30 08:02:04','AUTHENTICATION_SUCCESS'),(6,'user_100','2021-10-30 08:02:47','AUTHENTICATION_SUCCESS'),(7,'artemis_admin','2021-10-30 08:02:58','AUTHENTICATION_SUCCESS'),(8,'uuser_100','2021-10-30 08:06:49','AUTHENTICATION_FAILURE'),(9,'user_100','2021-10-30 08:06:52','AUTHENTICATION_SUCCESS'),(10,'user_102','2021-10-30 08:07:03','AUTHENTICATION_SUCCESS'),(11,'user_104','2021-10-30 08:07:17','AUTHENTICATION_SUCCESS'),(12,'user_101','2021-10-30 08:07:28','AUTHENTICATION_SUCCESS'),(13,'user_103','2021-10-30 08:07:46','AUTHENTICATION_SUCCESS');
/*!40000 ALTER TABLE `jhi_persistent_audit_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jhi_persistent_audit_evt_data`
--

DROP TABLE IF EXISTS `jhi_persistent_audit_evt_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `jhi_persistent_audit_evt_data` (
  `event_id` bigint NOT NULL,
  `name` varchar(255) NOT NULL,
  `value` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`event_id`,`name`),
  KEY `idx_persistent_audit_evt_data` (`event_id`),
  CONSTRAINT `fk_evt_pers_audit_evt_data` FOREIGN KEY (`event_id`) REFERENCES `jhi_persistent_audit_event` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jhi_persistent_audit_evt_data`
--

LOCK TABLES `jhi_persistent_audit_evt_data` WRITE;
/*!40000 ALTER TABLE `jhi_persistent_audit_evt_data` DISABLE KEYS */;
/*!40000 ALTER TABLE `jhi_persistent_audit_evt_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jhi_user`
--

DROP TABLE IF EXISTS `jhi_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `jhi_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `login` varchar(50) NOT NULL,
  `password_hash` varchar(255) DEFAULT NULL,
  `first_name` varchar(50) DEFAULT NULL,
  `last_name` varchar(50) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `activated` bit(1) NOT NULL,
  `lang_key` varchar(5) DEFAULT NULL,
  `activation_key` varchar(20) DEFAULT NULL,
  `reset_key` varchar(20) DEFAULT NULL,
  `created_by` varchar(50) DEFAULT NULL,
  `created_date` timestamp NULL,
  `reset_date` timestamp NULL DEFAULT NULL,
  `last_modified_by` varchar(50) DEFAULT NULL,
  `last_modified_date` timestamp NULL DEFAULT NULL,
  `image_url` varchar(256) DEFAULT NULL,
  `last_notification_read` datetime DEFAULT NULL,
  `registration_number` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_user_login` (`login`),
  UNIQUE KEY `login` (`login`),
  UNIQUE KEY `registration_number` (`registration_number`),
  KEY `jhi_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jhi_user`
--

LOCK TABLES `jhi_user` WRITE;
/*!40000 ALTER TABLE `jhi_user` DISABLE KEYS */;
INSERT INTO `jhi_user` VALUES (1,'artemis_admin','I+lqKn+WGhFu1gia5rAO36d9C2TXBNPo','Administrator','Administrator','admin@localhost',_binary '','en',NULL,'q3DJSIBoutc91pvyyHWe','system','2021-10-25 10:26:59','2021-10-25 10:26:59','system','2021-10-30 07:58:43',NULL,NULL,NULL),(2,'user_100','gpjZTZtKjX8WXNxcYp3/P6jH3GYoLM3S','user','100','user100@artemis-testing.com',_binary '','en',NULL,'39b2hgHqNOPtnT9aWsF6','artemis_admin','2021-10-30 07:53:44','2021-10-30 07:53:44','artemis_admin','2021-10-30 08:02:32',NULL,NULL,NULL),(3,'user_102','pXZxA5BV9JK7oCGRqLCHyjAHwFzRiitH','user','102','user102@artemis-testing.com',_binary '','en',NULL,'upkqkgI6V2uLop9gseP1','artemis_admin','2021-10-30 08:04:21','2021-10-30 08:04:21','artemis_admin','2021-10-30 08:04:21',NULL,NULL,NULL),(4,'user_104','QhfBX1Vdw4fnepXfxmuDHIQXGbcimsNa','user','104','user104@artemis-testing.com',_binary '','en',NULL,'8XmwuKCkkE54y3QIJebI','artemis_admin','2021-10-30 08:04:55','2021-10-30 08:04:55','artemis_admin','2021-10-30 08:04:55',NULL,NULL,NULL),(5,'user_103','U/4ivrg5TlSS431j3zHMm+/dSc90Amyv','user','103','user103@artemis-testing.com',_binary '','en',NULL,'rz2fzxMzzgXr3gqKNp2V','artemis_admin','2021-10-30 08:05:35','2021-10-30 08:05:35','artemis_admin','2021-10-30 08:05:35',NULL,NULL,NULL),(6,'user_101','dMtn+5bofzln4aApPBSftynBIJ0r2xyJ','user','101','user101@artemis-testing.com',_binary '','en',NULL,'sEk8D4vAxrqL0fXY045C','artemis_admin','2021-10-30 08:06:20','2021-10-30 08:06:20','artemis_admin','2021-10-30 08:06:20',NULL,NULL,NULL);
/*!40000 ALTER TABLE `jhi_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `jhi_user_authority`
--

DROP TABLE IF EXISTS `jhi_user_authority`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `jhi_user_authority` (
  `user_id` bigint NOT NULL,
  `authority_name` varchar(50) NOT NULL,
  PRIMARY KEY (`user_id`,`authority_name`),
  KEY `fk_authority_name` (`authority_name`),
  CONSTRAINT `fk_authority_name` FOREIGN KEY (`authority_name`) REFERENCES `jhi_authority` (`name`),
  CONSTRAINT `fk_user_id` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `jhi_user_authority`
--

LOCK TABLES `jhi_user_authority` WRITE;
/*!40000 ALTER TABLE `jhi_user_authority` DISABLE KEYS */;
INSERT INTO `jhi_user_authority` VALUES (1,'ROLE_ADMIN'),(5,'ROLE_INSTRUCTOR'),(6,'ROLE_TA'),(1,'ROLE_USER'),(2,'ROLE_USER'),(3,'ROLE_USER'),(4,'ROLE_USER');
/*!40000 ALTER TABLE `jhi_user_authority` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `learning_goal`
--

DROP TABLE IF EXISTS `learning_goal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `learning_goal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` longtext,
  `title` varchar(255) NOT NULL,
  `course_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKlndcce3s82xwv8rumk2cjm2q0` (`course_id`),
  CONSTRAINT `FKlndcce3s82xwv8rumk2cjm2q0` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `learning_goal`
--

LOCK TABLES `learning_goal` WRITE;
/*!40000 ALTER TABLE `learning_goal` DISABLE KEYS */;
/*!40000 ALTER TABLE `learning_goal` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `learning_goal_exercise`
--

DROP TABLE IF EXISTS `learning_goal_exercise`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `learning_goal_exercise` (
  `learning_goal_id` bigint NOT NULL,
  `exercise_id` bigint NOT NULL,
  PRIMARY KEY (`learning_goal_id`,`exercise_id`),
  KEY `FKdls9xttqemxxt0qljxop5v8rt` (`exercise_id`),
  CONSTRAINT `FKdls9xttqemxxt0qljxop5v8rt` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `FKsqsxg20yruu16ca9x383wnhci` FOREIGN KEY (`learning_goal_id`) REFERENCES `learning_goal` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `learning_goal_exercise`
--

LOCK TABLES `learning_goal_exercise` WRITE;
/*!40000 ALTER TABLE `learning_goal_exercise` DISABLE KEYS */;
/*!40000 ALTER TABLE `learning_goal_exercise` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `learning_goal_lecture_unit`
--

DROP TABLE IF EXISTS `learning_goal_lecture_unit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `learning_goal_lecture_unit` (
  `learning_goal_id` bigint NOT NULL,
  `lecture_unit_id` bigint NOT NULL,
  PRIMARY KEY (`learning_goal_id`,`lecture_unit_id`),
  KEY `FK4rflk0awb45txqagwpq2islj2` (`lecture_unit_id`),
  CONSTRAINT `FK34nd0arw9mr0ei8quno6u4sw3` FOREIGN KEY (`learning_goal_id`) REFERENCES `learning_goal` (`id`),
  CONSTRAINT `FK4rflk0awb45txqagwpq2islj2` FOREIGN KEY (`lecture_unit_id`) REFERENCES `lecture_unit` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `learning_goal_lecture_unit`
--

LOCK TABLES `learning_goal_lecture_unit` WRITE;
/*!40000 ALTER TABLE `learning_goal_lecture_unit` DISABLE KEYS */;
/*!40000 ALTER TABLE `learning_goal_lecture_unit` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `lecture`
--

DROP TABLE IF EXISTS `lecture`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `lecture` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` longtext,
  `end_date` datetime DEFAULT NULL,
  `start_date` datetime DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `course_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKjoc9yetfohpygdvx5wv385vwb` (`course_id`),
  KEY `lecture_start_date` (`start_date`),
  KEY `lecture_end_date` (`end_date`),
  CONSTRAINT `FKjoc9yetfohpygdvx5wv385vwb` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `lecture`
--

LOCK TABLES `lecture` WRITE;
/*!40000 ALTER TABLE `lecture` DISABLE KEYS */;
/*!40000 ALTER TABLE `lecture` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `lecture_unit`
--

DROP TABLE IF EXISTS `lecture_unit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `lecture_unit` (
  `discriminator` varchar(31) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  `release_date` datetime(6) DEFAULT NULL,
  `content` longtext,
  `description` longtext,
  `source` longtext,
  `lecture_id` bigint DEFAULT NULL,
  `exercise_id` bigint DEFAULT NULL,
  `lecture_unit_order` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKdr9d160441fo4llod8gq74jwp` (`lecture_id`),
  KEY `FKrx11lk6gxgpcg2vw96ay8id8p` (`exercise_id`),
  KEY `lecture_unit_release_date` (`release_date`),
  CONSTRAINT `FKdr9d160441fo4llod8gq74jwp` FOREIGN KEY (`lecture_id`) REFERENCES `lecture` (`id`),
  CONSTRAINT `FKrx11lk6gxgpcg2vw96ay8id8p` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `lecture_unit`
--

LOCK TABLES `lecture_unit` WRITE;
/*!40000 ALTER TABLE `lecture_unit` DISABLE KEYS */;
/*!40000 ALTER TABLE `lecture_unit` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `lti_outcome_url`
--

DROP TABLE IF EXISTS `lti_outcome_url`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `lti_outcome_url` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `url` varchar(255) DEFAULT NULL,
  `sourced_id` varchar(255) DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `exercise_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_ltioutcomeurl_exercise_id` (`exercise_id`),
  KEY `fk_ltioutcomeurl_user_id` (`user_id`),
  CONSTRAINT `fk_ltioutcomeurl_exercise_id` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ltioutcomeurl_user_id` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `lti_outcome_url`
--

LOCK TABLES `lti_outcome_url` WRITE;
/*!40000 ALTER TABLE `lti_outcome_url` DISABLE KEYS */;
/*!40000 ALTER TABLE `lti_outcome_url` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `lti_user_id`
--

DROP TABLE IF EXISTS `lti_user_id`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `lti_user_id` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lti_user_id` varchar(255) DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`),
  CONSTRAINT `fk_ltiuserid_user_id` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `lti_user_id`
--

LOCK TABLES `lti_user_id` WRITE;
/*!40000 ALTER TABLE `lti_user_id` DISABLE KEYS */;
/*!40000 ALTER TABLE `lti_user_id` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `model_assessment_knowledge`
--

DROP TABLE IF EXISTS `model_assessment_knowledge`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `model_assessment_knowledge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `model_assessment_knowledge`
--

LOCK TABLES `model_assessment_knowledge` WRITE;
/*!40000 ALTER TABLE `model_assessment_knowledge` DISABLE KEYS */;
/*!40000 ALTER TABLE `model_assessment_knowledge` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `model_cluster`
--

DROP TABLE IF EXISTS `model_cluster`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `model_cluster` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `model_element_type` char(100) NOT NULL,
  `minimum_similarity` double NOT NULL,
  `exercise_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_model_cluster_exercise_id` (`exercise_id`),
  KEY `reference` (`model_element_type`),
  CONSTRAINT `fk_model_cluster_exercise_id` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `model_cluster`
--

LOCK TABLES `model_cluster` WRITE;
/*!40000 ALTER TABLE `model_cluster` DISABLE KEYS */;
/*!40000 ALTER TABLE `model_cluster` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `model_element`
--

DROP TABLE IF EXISTS `model_element`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `model_element` (
  `model_element_id` char(36) NOT NULL,
  `model_element_type` char(30) NOT NULL,
  `submission_id` bigint NOT NULL,
  `cluster_id` bigint DEFAULT NULL,
  `knowledge_id` bigint DEFAULT NULL,
  PRIMARY KEY (`model_element_id`),
  KEY `fk_model_element_submission_id` (`submission_id`),
  KEY `fk_model_element_cluster_id` (`cluster_id`),
  KEY `reference` (`model_element_type`),
  KEY `fk_model_element_knowledge_id` (`knowledge_id`),
  CONSTRAINT `fk_model_element_cluster_id` FOREIGN KEY (`cluster_id`) REFERENCES `model_cluster` (`id`),
  CONSTRAINT `fk_model_element_knowledge_id` FOREIGN KEY (`knowledge_id`) REFERENCES `model_assessment_knowledge` (`id`),
  CONSTRAINT `fk_model_element_submission_id` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `model_element`
--

LOCK TABLES `model_element` WRITE;
/*!40000 ALTER TABLE `model_element` DISABLE KEYS */;
/*!40000 ALTER TABLE `model_element` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `model_exercise_details`
--

DROP TABLE IF EXISTS `model_exercise_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `model_exercise_details` (
  `id` bigint NOT NULL,
  `knowledge_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_model_exercise_knowledge_id` (`knowledge_id`),
  CONSTRAINT `fk_model_exercise_details` FOREIGN KEY (`id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `fk_model_exercise_knowledge_id` FOREIGN KEY (`knowledge_id`) REFERENCES `model_assessment_knowledge` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `model_exercise_details`
--

LOCK TABLES `model_exercise_details` WRITE;
/*!40000 ALTER TABLE `model_exercise_details` DISABLE KEYS */;
/*!40000 ALTER TABLE `model_exercise_details` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `multiple_choice_submitted_answer_selected_options`
--

DROP TABLE IF EXISTS `multiple_choice_submitted_answer_selected_options`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `multiple_choice_submitted_answer_selected_options` (
  `multiple_choice_submitted_answers_id` bigint NOT NULL,
  `selected_options_id` bigint NOT NULL,
  PRIMARY KEY (`multiple_choice_submitted_answers_id`,`selected_options_id`),
  KEY `FK87gmes7g3ad3qf3wmx3lu0iq0` (`selected_options_id`),
  CONSTRAINT `FK87gmes7g3ad3qf3wmx3lu0iq0` FOREIGN KEY (`selected_options_id`) REFERENCES `answer_option` (`id`),
  CONSTRAINT `FKd6kco24shh09opxvn2kmi0dw5` FOREIGN KEY (`multiple_choice_submitted_answers_id`) REFERENCES `submitted_answer` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `multiple_choice_submitted_answer_selected_options`
--

LOCK TABLES `multiple_choice_submitted_answer_selected_options` WRITE;
/*!40000 ALTER TABLE `multiple_choice_submitted_answer_selected_options` DISABLE KEYS */;
/*!40000 ALTER TABLE `multiple_choice_submitted_answer_selected_options` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `notification`
--

DROP TABLE IF EXISTS `notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `notification` (
  `discriminator` varchar(31) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notification_date` datetime DEFAULT NULL,
  `target` varchar(255) DEFAULT NULL,
  `text` longtext,
  `title` varchar(255) DEFAULT NULL,
  `expire_date` datetime DEFAULT NULL,
  `jhi_type` varchar(255) DEFAULT NULL,
  `author_id` bigint DEFAULT NULL,
  `recipient_id` bigint DEFAULT NULL,
  `course_id` bigint DEFAULT NULL,
  `priority` varchar(15) NOT NULL DEFAULT 'MEDIUM',
  `outdated` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `FK2qvynpew0iu557b4qk9go0u0c` (`course_id`),
  KEY `FKf3ah8uajs8wfda872mvl56hq4` (`recipient_id`),
  KEY `FKg8sxrh7ha8nl46lbljs12mtxy` (`author_id`),
  KEY `notification_notification_date` (`notification_date`),
  KEY `notification_target` (`target`),
  KEY `notification_jhi_type` (`jhi_type`),
  CONSTRAINT `FK2qvynpew0iu557b4qk9go0u0c` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
  CONSTRAINT `FKf3ah8uajs8wfda872mvl56hq4` FOREIGN KEY (`recipient_id`) REFERENCES `jhi_user` (`id`),
  CONSTRAINT `FKg8sxrh7ha8nl46lbljs12mtxy` FOREIGN KEY (`author_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `notification`
--

LOCK TABLES `notification` WRITE;
/*!40000 ALTER TABLE `notification` DISABLE KEYS */;
/*!40000 ALTER TABLE `notification` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `organization`
--

DROP TABLE IF EXISTS `organization`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `organization` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` varchar(255) DEFAULT NULL,
  `email_pattern` varchar(255) NOT NULL,
  `logo_url` varchar(255) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `short_name` varchar(50) NOT NULL,
  `url` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `organization`
--

LOCK TABLES `organization` WRITE;
/*!40000 ALTER TABLE `organization` DISABLE KEYS */;
/*!40000 ALTER TABLE `organization` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `participant_score`
--

DROP TABLE IF EXISTS `participant_score`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `participant_score` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `team_id` bigint DEFAULT NULL,
  `exercise_id` bigint DEFAULT NULL,
  `last_result_id` bigint DEFAULT NULL,
  `last_rated_result_id` bigint DEFAULT NULL,
  `last_score` double DEFAULT NULL,
  `last_points` double DEFAULT NULL,
  `last_rated_score` double DEFAULT NULL,
  `last_rated_points` double DEFAULT NULL,
  `discriminator` varchar(31) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `exercise_id` (`exercise_id`,`user_id`,`last_result_id`),
  UNIQUE KEY `exercise_id_2` (`exercise_id`,`user_id`,`last_rated_result_id`),
  UNIQUE KEY `exercise_id_3` (`exercise_id`,`team_id`,`last_result_id`),
  UNIQUE KEY `exercise_id_4` (`exercise_id`,`team_id`,`last_rated_result_id`),
  KEY `fk_participant_score_last_result_id` (`last_result_id`),
  KEY `fk_participant_score_last_rated_result_id` (`last_rated_result_id`),
  KEY `fk_participant_score_user_id` (`user_id`),
  KEY `fk_participant_score_team_id` (`team_id`),
  CONSTRAINT `fk_participant_score_exercise_id` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `fk_participant_score_last_rated_result_id` FOREIGN KEY (`last_rated_result_id`) REFERENCES `result` (`id`),
  CONSTRAINT `fk_participant_score_last_result_id` FOREIGN KEY (`last_result_id`) REFERENCES `result` (`id`),
  CONSTRAINT `fk_participant_score_team_id` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`),
  CONSTRAINT `fk_participant_score_user_id` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `participant_score`
--

LOCK TABLES `participant_score` WRITE;
/*!40000 ALTER TABLE `participant_score` DISABLE KEYS */;
/*!40000 ALTER TABLE `participant_score` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `participation`
--

DROP TABLE IF EXISTS `participation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `participation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `repository_url` varchar(255) DEFAULT NULL,
  `build_plan_id` varchar(255) DEFAULT NULL,
  `student_id` bigint DEFAULT NULL,
  `team_id` bigint DEFAULT NULL,
  `exercise_id` bigint DEFAULT NULL,
  `initialization_state` varchar(255) DEFAULT NULL,
  `initialization_date` datetime(6) DEFAULT NULL,
  `presentation_score` int DEFAULT NULL,
  `discriminator` varchar(31) NOT NULL,
  `test_run` bit(1) DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6skuc03gvp2gw5lyji5lu0v0v` (`student_id`,`exercise_id`,`initialization_state`),
  UNIQUE KEY `UC_PARTICIPATION_TEAM_ID_EXERCISE_ID_INITIALIZATION_STATE_COLS` (`team_id`,`exercise_id`,`initialization_state`),
  KEY `fk_participation_exercise_id` (`exercise_id`),
  KEY `fk_participation_student_id` (`student_id`),
  KEY `fk_participation_team_id` (`team_id`),
  KEY `build_plan_id` (`build_plan_id`),
  KEY `repository_url` (`repository_url`),
  KEY `participation_initialization_date` (`initialization_date`),
  KEY `participation_test_run` (`test_run`),
  KEY `participation_presentation_score` (`presentation_score`),
  KEY `participation_discriminator` (`discriminator`),
  CONSTRAINT `fk_participation_exercise_id` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `fk_participation_student_id` FOREIGN KEY (`student_id`) REFERENCES `jhi_user` (`id`),
  CONSTRAINT `fk_participation_team_id` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `participation`
--

LOCK TABLES `participation` WRITE;
/*!40000 ALTER TABLE `participation` DISABLE KEYS */;
/*!40000 ALTER TABLE `participation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `plagiarism_comparison`
--

DROP TABLE IF EXISTS `plagiarism_comparison`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `plagiarism_comparison` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plagiarism_result_id` bigint DEFAULT NULL,
  `submission_a_id` bigint DEFAULT NULL,
  `submission_b_id` bigint DEFAULT NULL,
  `similarity` float DEFAULT NULL,
  `status` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_plagiarism_result_comparison` (`plagiarism_result_id`),
  KEY `fk_plagiarism_comparison_submission_a` (`submission_a_id`),
  KEY `fk_plagiarism_comparison_submission_b` (`submission_b_id`),
  CONSTRAINT `fk_plagiarism_comparison_submission_a` FOREIGN KEY (`submission_a_id`) REFERENCES `plagiarism_submission` (`id`),
  CONSTRAINT `fk_plagiarism_comparison_submission_b` FOREIGN KEY (`submission_b_id`) REFERENCES `plagiarism_submission` (`id`),
  CONSTRAINT `fk_plagiarism_result_comparison` FOREIGN KEY (`plagiarism_result_id`) REFERENCES `plagiarism_result` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plagiarism_comparison`
--

LOCK TABLES `plagiarism_comparison` WRITE;
/*!40000 ALTER TABLE `plagiarism_comparison` DISABLE KEYS */;
/*!40000 ALTER TABLE `plagiarism_comparison` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `plagiarism_comparison_matches`
--

DROP TABLE IF EXISTS `plagiarism_comparison_matches`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `plagiarism_comparison_matches` (
  `plagiarism_comparison_id` bigint DEFAULT NULL,
  `start_a` int DEFAULT NULL,
  `start_b` int DEFAULT NULL,
  `length` int DEFAULT NULL,
  KEY `fk_plagiarism_comparison_matches` (`plagiarism_comparison_id`),
  CONSTRAINT `fk_plagiarism_comparison_matches` FOREIGN KEY (`plagiarism_comparison_id`) REFERENCES `plagiarism_comparison` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plagiarism_comparison_matches`
--

LOCK TABLES `plagiarism_comparison_matches` WRITE;
/*!40000 ALTER TABLE `plagiarism_comparison_matches` DISABLE KEYS */;
/*!40000 ALTER TABLE `plagiarism_comparison_matches` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `plagiarism_result`
--

DROP TABLE IF EXISTS `plagiarism_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `plagiarism_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `discriminator` varchar(31) NOT NULL,
  `duration` bigint DEFAULT NULL,
  `exercise_id` bigint DEFAULT NULL,
  `created_by` varchar(50) NOT NULL,
  `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_modified_by` varchar(50) DEFAULT NULL,
  `last_modified_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_plagiarism_result_exercise` (`exercise_id`),
  CONSTRAINT `fk_plagiarism_result_exercise` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plagiarism_result`
--

LOCK TABLES `plagiarism_result` WRITE;
/*!40000 ALTER TABLE `plagiarism_result` DISABLE KEYS */;
/*!40000 ALTER TABLE `plagiarism_result` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `plagiarism_result_similarity_distribution`
--

DROP TABLE IF EXISTS `plagiarism_result_similarity_distribution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `plagiarism_result_similarity_distribution` (
  `plagiarism_result_id` bigint DEFAULT NULL,
  `idx` int DEFAULT NULL,
  `value` int DEFAULT NULL,
  KEY `fk_plagiarism_result_similarity_distribution` (`plagiarism_result_id`),
  CONSTRAINT `fk_plagiarism_result_similarity_distribution` FOREIGN KEY (`plagiarism_result_id`) REFERENCES `plagiarism_result` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plagiarism_result_similarity_distribution`
--

LOCK TABLES `plagiarism_result_similarity_distribution` WRITE;
/*!40000 ALTER TABLE `plagiarism_result_similarity_distribution` DISABLE KEYS */;
/*!40000 ALTER TABLE `plagiarism_result_similarity_distribution` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `plagiarism_submission`
--

DROP TABLE IF EXISTS `plagiarism_submission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `plagiarism_submission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `submission_id` bigint DEFAULT NULL,
  `student_login` varchar(50) DEFAULT NULL,
  `size` int DEFAULT NULL,
  `score` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plagiarism_submission`
--

LOCK TABLES `plagiarism_submission` WRITE;
/*!40000 ALTER TABLE `plagiarism_submission` DISABLE KEYS */;
/*!40000 ALTER TABLE `plagiarism_submission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `plagiarism_submission_element`
--

DROP TABLE IF EXISTS `plagiarism_submission_element`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `plagiarism_submission_element` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `discriminator` varchar(31) NOT NULL,
  `file_column` int DEFAULT NULL,
  `line` int DEFAULT NULL,
  `token_type` int DEFAULT NULL,
  `length` int DEFAULT NULL,
  `file` varchar(255) DEFAULT NULL,
  `model_element_id` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plagiarism_submission_element`
--

LOCK TABLES `plagiarism_submission_element` WRITE;
/*!40000 ALTER TABLE `plagiarism_submission_element` DISABLE KEYS */;
/*!40000 ALTER TABLE `plagiarism_submission_element` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `plagiarism_submission_elements`
--

DROP TABLE IF EXISTS `plagiarism_submission_elements`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `plagiarism_submission_elements` (
  `plagiarism_submission_id` bigint DEFAULT NULL,
  `plagiarism_submission_element_id` bigint DEFAULT NULL,
  KEY `fk_plagiarism_submission_elements_submission` (`plagiarism_submission_id`),
  KEY `fk_plagiarism_submission_elements_element` (`plagiarism_submission_element_id`),
  CONSTRAINT `fk_plagiarism_submission_elements_element` FOREIGN KEY (`plagiarism_submission_element_id`) REFERENCES `plagiarism_submission_element` (`id`),
  CONSTRAINT `fk_plagiarism_submission_elements_submission` FOREIGN KEY (`plagiarism_submission_id`) REFERENCES `plagiarism_submission` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `plagiarism_submission_elements`
--

LOCK TABLES `plagiarism_submission_elements` WRITE;
/*!40000 ALTER TABLE `plagiarism_submission_elements` DISABLE KEYS */;
/*!40000 ALTER TABLE `plagiarism_submission_elements` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `post`
--

DROP TABLE IF EXISTS `post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `post` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `creation_date` datetime DEFAULT NULL,
  `content` varchar(1000) DEFAULT NULL,
  `visible_for_students` bit(1) DEFAULT NULL,
  `author_id` bigint DEFAULT NULL,
  `exercise_id` bigint DEFAULT NULL,
  `lecture_id` bigint DEFAULT NULL,
  `votes` int DEFAULT '0',
  `title` varchar(200) DEFAULT NULL,
  `tokenized_content` varchar(1000) DEFAULT NULL,
  `course_id` bigint DEFAULT NULL,
  `course_wide_context` varchar(50) DEFAULT NULL,
  `display_priority` varchar(25) NOT NULL DEFAULT 'NONE',
  PRIMARY KEY (`id`),
  KEY `FK6kgo0sse7sait8q665s16wynf` (`exercise_id`),
  KEY `FKcu2dpdk9qef0yaithkxcjebwg` (`lecture_id`),
  KEY `FKjpgnpph3i2h0n8xekqiytaers` (`author_id`),
  KEY `fk_post_course_id` (`course_id`),
  CONSTRAINT `FK6kgo0sse7sait8q665s16wynf` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `fk_post_course_id` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`),
  CONSTRAINT `FKcu2dpdk9qef0yaithkxcjebwg` FOREIGN KEY (`lecture_id`) REFERENCES `lecture` (`id`),
  CONSTRAINT `FKjpgnpph3i2h0n8xekqiytaers` FOREIGN KEY (`author_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `post`
--

LOCK TABLES `post` WRITE;
/*!40000 ALTER TABLE `post` DISABLE KEYS */;
/*!40000 ALTER TABLE `post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `post_tag`
--

DROP TABLE IF EXISTS `post_tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `post_tag` (
  `post_id` bigint NOT NULL,
  `text` varchar(50) DEFAULT NULL,
  KEY `fk_post_tag_post_id` (`post_id`),
  CONSTRAINT `fk_post_tag_post_id` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `post_tag`
--

LOCK TABLES `post_tag` WRITE;
/*!40000 ALTER TABLE `post_tag` DISABLE KEYS */;
/*!40000 ALTER TABLE `post_tag` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `programming_exercise_auxiliary_repositories`
--

DROP TABLE IF EXISTS `programming_exercise_auxiliary_repositories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `programming_exercise_auxiliary_repositories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `checkout_directory` varchar(100) DEFAULT NULL,
  `repository_url` varchar(500) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `exercise_id` bigint DEFAULT NULL,
  `programming_exercise_auxiliary_repositories_order` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_auxiliary_repository_exercise` (`exercise_id`),
  CONSTRAINT `fk_programming_exercise_auxiliary_repositories_exercise` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `programming_exercise_auxiliary_repositories`
--

LOCK TABLES `programming_exercise_auxiliary_repositories` WRITE;
/*!40000 ALTER TABLE `programming_exercise_auxiliary_repositories` DISABLE KEYS */;
/*!40000 ALTER TABLE `programming_exercise_auxiliary_repositories` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `programming_exercise_details`
--

DROP TABLE IF EXISTS `programming_exercise_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `programming_exercise_details` (
  `id` bigint NOT NULL,
  `build_and_test_student_submissions_after_due_date` datetime DEFAULT NULL,
  `test_cases_changed` bit(1) DEFAULT NULL,
  `project_key` varchar(255) DEFAULT NULL,
  `allow_online_editor` bit(1) DEFAULT NULL,
  `allow_offline_ide` bit(1) DEFAULT b'1',
  `static_code_analysis_enabled` bit(1) DEFAULT b'0',
  `max_static_code_analysis_penalty` int DEFAULT NULL,
  `project_type` varchar(45) DEFAULT NULL,
  `show_test_names_to_students` bit(1) DEFAULT b'0',
  `allow_complaints_for_automatic_assessments` bit(1) DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `programming_exercise_details_due_date` (`build_and_test_student_submissions_after_due_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `programming_exercise_details`
--

LOCK TABLES `programming_exercise_details` WRITE;
/*!40000 ALTER TABLE `programming_exercise_details` DISABLE KEYS */;
/*!40000 ALTER TABLE `programming_exercise_details` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `programming_exercise_test_case`
--

DROP TABLE IF EXISTS `programming_exercise_test_case`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `programming_exercise_test_case` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `test_name` varchar(255) NOT NULL,
  `weight` double DEFAULT '1',
  `active` bit(1) NOT NULL,
  `exercise_id` bigint DEFAULT NULL,
  `bonus_multiplier` double DEFAULT '1',
  `bonus_points` double DEFAULT '0',
  `visibility` varchar(255) DEFAULT 'ALWAYS',
  PRIMARY KEY (`id`),
  UNIQUE KEY `exercise_test_case` (`test_name`,`exercise_id`),
  KEY `fk_programming_exercise_test_case_exercise_id` (`exercise_id`),
  CONSTRAINT `fk_programming_exercise_test_case_exercise_id` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `programming_exercise_test_case`
--

LOCK TABLES `programming_exercise_test_case` WRITE;
/*!40000 ALTER TABLE `programming_exercise_test_case` DISABLE KEYS */;
/*!40000 ALTER TABLE `programming_exercise_test_case` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `quiz_question`
--

DROP TABLE IF EXISTS `quiz_question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `quiz_question` (
  `discriminator` varchar(31) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `text` varchar(5000) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `background_file_path` varchar(255) DEFAULT NULL,
  `explanation` varchar(255) DEFAULT NULL,
  `hint` varchar(255) DEFAULT NULL,
  `randomize_order` bit(1) DEFAULT NULL,
  `points` int DEFAULT NULL,
  `scoring_type` varchar(255) DEFAULT NULL,
  `exercise_id` bigint DEFAULT NULL,
  `quiz_questions_order` bigint DEFAULT NULL,
  `quiz_question_statistic_id` bigint DEFAULT NULL,
  `invalid` bit(1) DEFAULT NULL,
  `similarity_value` int NOT NULL DEFAULT '85',
  `match_letter_case` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_QUESTIONQUESTION_STATISTIC_ID_COL` (`quiz_question_statistic_id`),
  KEY `FKcw89k10abejup8p3hif0kjpw` (`exercise_id`),
  CONSTRAINT `FKcw89k10abejup8p3hif0kjpw` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `FKoi0sqphft8m7f92qa7ui8i3ac` FOREIGN KEY (`quiz_question_statistic_id`) REFERENCES `quiz_statistic` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quiz_question`
--

LOCK TABLES `quiz_question` WRITE;
/*!40000 ALTER TABLE `quiz_question` DISABLE KEYS */;
/*!40000 ALTER TABLE `quiz_question` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `quiz_statistic`
--

DROP TABLE IF EXISTS `quiz_statistic`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `quiz_statistic` (
  `discriminator` varchar(31) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `participants_rated` int DEFAULT NULL,
  `participants_unrated` int DEFAULT NULL,
  `rated_correct_counter` int DEFAULT NULL,
  `un_rated_correct_counter` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quiz_statistic`
--

LOCK TABLES `quiz_statistic` WRITE;
/*!40000 ALTER TABLE `quiz_statistic` DISABLE KEYS */;
/*!40000 ALTER TABLE `quiz_statistic` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `quiz_statistic_counter`
--

DROP TABLE IF EXISTS `quiz_statistic_counter`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `quiz_statistic_counter` (
  `discriminator` varchar(31) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rated_counter` int DEFAULT NULL,
  `un_rated_counter` int DEFAULT NULL,
  `points` double DEFAULT NULL,
  `drag_and_drop_question_statistic_id` bigint DEFAULT NULL,
  `drop_location_id` bigint DEFAULT NULL,
  `quiz_point_statistic_id` bigint DEFAULT NULL,
  `answer_id` bigint DEFAULT NULL,
  `multiple_choice_question_statistic_id` bigint DEFAULT NULL,
  `short_answer_question_statistic_id` bigint DEFAULT NULL,
  `spot_id` bigint DEFAULT NULL,
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quiz_statistic_counter`
--

LOCK TABLES `quiz_statistic_counter` WRITE;
/*!40000 ALTER TABLE `quiz_statistic_counter` DISABLE KEYS */;
/*!40000 ALTER TABLE `quiz_statistic_counter` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `reaction`
--

DROP TABLE IF EXISTS `reaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `reaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `emoji_id` varchar(255) DEFAULT NULL,
  `creation_date` datetime DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `post_id` bigint DEFAULT NULL,
  `answer_post_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_reaction_user_id` (`user_id`),
  KEY `fk_reaction_post_id` (`post_id`),
  KEY `fk_reaction_answer_post_id` (`answer_post_id`),
  CONSTRAINT `fk_reaction_answer_post_id` FOREIGN KEY (`answer_post_id`) REFERENCES `answer_post` (`id`),
  CONSTRAINT `fk_reaction_post_id` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`),
  CONSTRAINT `fk_reaction_user_id` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `reaction`
--

LOCK TABLES `reaction` WRITE;
/*!40000 ALTER TABLE `reaction` DISABLE KEYS */;
/*!40000 ALTER TABLE `reaction` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `result`
--

DROP TABLE IF EXISTS `result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `result_string` varchar(255) DEFAULT NULL,
  `completion_date` datetime(6) DEFAULT NULL,
  `jhi_successful` bit(1) DEFAULT NULL,
  `participation_id` bigint DEFAULT NULL,
  `score` double DEFAULT NULL,
  `submission_id` bigint DEFAULT NULL,
  `rated` bit(1) DEFAULT NULL,
  `has_feedback` bit(1) DEFAULT NULL,
  `assessment_type` varchar(255) DEFAULT NULL,
  `assessor_id` bigint DEFAULT NULL,
  `example_result` bit(1) DEFAULT NULL,
  `has_complaint` bit(1) DEFAULT NULL,
  `results_order` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `FKkkdvousfa95ueh46xg741fadg` (`assessor_id`),
  KEY `fk_result_participation_id` (`participation_id`),
  KEY `fk_result_submission_id` (`submission_id`),
  KEY `result_completion_date` (`completion_date`),
  KEY `result_assessment_type` (`assessment_type`),
  KEY `result_rated` (`rated`),
  CONSTRAINT `fk_result_participation_id` FOREIGN KEY (`participation_id`) REFERENCES `participation` (`id`),
  CONSTRAINT `fk_result_submission_id` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`),
  CONSTRAINT `FKkkdvousfa95ueh46xg741fadg` FOREIGN KEY (`assessor_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `result`
--

LOCK TABLES `result` WRITE;
/*!40000 ALTER TABLE `result` DISABLE KEYS */;
/*!40000 ALTER TABLE `result` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `result_rating`
--

DROP TABLE IF EXISTS `result_rating`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `result_rating` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rating` tinyint DEFAULT NULL,
  `result_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `result_id` (`result_id`),
  KEY `fk_result_rating_result_id` (`result_id`),
  CONSTRAINT `fk_result_rating_result_id` FOREIGN KEY (`result_id`) REFERENCES `result` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `result_rating`
--

LOCK TABLES `result_rating` WRITE;
/*!40000 ALTER TABLE `result_rating` DISABLE KEYS */;
/*!40000 ALTER TABLE `result_rating` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `short_answer_mapping`
--

DROP TABLE IF EXISTS `short_answer_mapping`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `short_answer_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invalid` bit(1) DEFAULT NULL,
  `short_answer_solution_index` int DEFAULT NULL,
  `short_answer_spot_index` int DEFAULT NULL,
  `question_id` bigint DEFAULT NULL,
  `solution_id` bigint DEFAULT NULL,
  `spot_id` bigint DEFAULT NULL,
  `correct_mappings_order` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5opt512154y0g5llppcaqe19e` (`spot_id`),
  KEY `FKbiq7gp760m9y8xpycnguw78dp` (`solution_id`),
  KEY `FK4ehrp6xueea0pyiiidtuouc0s` (`question_id`),
  CONSTRAINT `FK4ehrp6xueea0pyiiidtuouc0s` FOREIGN KEY (`question_id`) REFERENCES `quiz_question` (`id`),
  CONSTRAINT `FK5opt512154y0g5llppcaqe19e` FOREIGN KEY (`spot_id`) REFERENCES `short_answer_spot` (`id`),
  CONSTRAINT `FKbiq7gp760m9y8xpycnguw78dp` FOREIGN KEY (`solution_id`) REFERENCES `short_answer_solution` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `short_answer_mapping`
--

LOCK TABLES `short_answer_mapping` WRITE;
/*!40000 ALTER TABLE `short_answer_mapping` DISABLE KEYS */;
/*!40000 ALTER TABLE `short_answer_mapping` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `short_answer_solution`
--

DROP TABLE IF EXISTS `short_answer_solution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `short_answer_solution` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invalid` bit(1) DEFAULT NULL,
  `text` varchar(255) DEFAULT NULL,
  `question_id` bigint DEFAULT NULL,
  `solutions_order` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKejag8le5jrd03enkmhxircugx` (`question_id`),
  CONSTRAINT `FKejag8le5jrd03enkmhxircugx` FOREIGN KEY (`question_id`) REFERENCES `quiz_question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `short_answer_solution`
--

LOCK TABLES `short_answer_solution` WRITE;
/*!40000 ALTER TABLE `short_answer_solution` DISABLE KEYS */;
/*!40000 ALTER TABLE `short_answer_solution` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `short_answer_spot`
--

DROP TABLE IF EXISTS `short_answer_spot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `short_answer_spot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invalid` bit(1) DEFAULT NULL,
  `width` int DEFAULT NULL,
  `question_id` bigint DEFAULT NULL,
  `spots_order` int DEFAULT NULL,
  `spot_nr` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK2kwul05ckvpi145gc0bmf7obh` (`question_id`),
  CONSTRAINT `FK2kwul05ckvpi145gc0bmf7obh` FOREIGN KEY (`question_id`) REFERENCES `quiz_question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `short_answer_spot`
--

LOCK TABLES `short_answer_spot` WRITE;
/*!40000 ALTER TABLE `short_answer_spot` DISABLE KEYS */;
/*!40000 ALTER TABLE `short_answer_spot` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `short_answer_submitted_text`
--

DROP TABLE IF EXISTS `short_answer_submitted_text`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `short_answer_submitted_text` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `text` varchar(255) DEFAULT NULL,
  `spot_id` bigint DEFAULT NULL,
  `submitted_answer_id` bigint DEFAULT NULL,
  `is_correct` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKkp1tfjoaawuiskayomp85e0lj` (`submitted_answer_id`),
  KEY `FKpkb6e1yjqhma5tgvabb9smyv4` (`spot_id`),
  CONSTRAINT `FKkp1tfjoaawuiskayomp85e0lj` FOREIGN KEY (`submitted_answer_id`) REFERENCES `submitted_answer` (`id`),
  CONSTRAINT `FKpkb6e1yjqhma5tgvabb9smyv4` FOREIGN KEY (`spot_id`) REFERENCES `short_answer_spot` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `short_answer_submitted_text`
--

LOCK TABLES `short_answer_submitted_text` WRITE;
/*!40000 ALTER TABLE `short_answer_submitted_text` DISABLE KEYS */;
/*!40000 ALTER TABLE `short_answer_submitted_text` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `static_code_analysis_category`
--

DROP TABLE IF EXISTS `static_code_analysis_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `static_code_analysis_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `penalty` double NOT NULL DEFAULT '1',
  `max_penalty` double DEFAULT NULL,
  `state` varchar(255) NOT NULL,
  `exercise_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_static_code_analysis_category_exercise_id` (`exercise_id`),
  CONSTRAINT `fk_static_code_analysis_category_exercise_id` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `static_code_analysis_category`
--

LOCK TABLES `static_code_analysis_category` WRITE;
/*!40000 ALTER TABLE `static_code_analysis_category` DISABLE KEYS */;
/*!40000 ALTER TABLE `static_code_analysis_category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `student_exam`
--

DROP TABLE IF EXISTS `student_exam`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `student_exam` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `exam_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `working_time` int DEFAULT NULL,
  `submitted` bit(1) DEFAULT NULL,
  `started` bit(1) DEFAULT b'0',
  `test_run` bit(1) DEFAULT b'0',
  `created_by` varchar(45) DEFAULT NULL,
  `last_modified_by` varchar(45) DEFAULT NULL,
  `last_modified_date` timestamp NULL DEFAULT NULL,
  `created_date` timestamp NULL DEFAULT NULL,
  `submission_date` timestamp NULL DEFAULT NULL,
  `started_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKm3tx9n7w4hpjyu130hp3bueh7` (`exam_id`),
  KEY `FKsqyvk8gb9eiknlgrj6xld1nel` (`user_id`),
  KEY `student_exam_test_run` (`test_run`),
  KEY `student_exam_started_date` (`started_date`),
  KEY `student_exam_started` (`started`),
  KEY `student_exam_submitted` (`submitted`),
  KEY `student_exam_submission_date` (`submission_date`),
  CONSTRAINT `FKm3tx9n7w4hpjyu130hp3bueh7` FOREIGN KEY (`exam_id`) REFERENCES `exam` (`id`),
  CONSTRAINT `FKsqyvk8gb9eiknlgrj6xld1nel` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student_exam`
--

LOCK TABLES `student_exam` WRITE;
/*!40000 ALTER TABLE `student_exam` DISABLE KEYS */;
/*!40000 ALTER TABLE `student_exam` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `student_exam_exercise`
--

DROP TABLE IF EXISTS `student_exam_exercise`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `student_exam_exercise` (
  `student_exam_id` bigint NOT NULL,
  `exercise_id` bigint NOT NULL,
  `exercise_order` int NOT NULL,
  PRIMARY KEY (`student_exam_id`,`exercise_order`),
  KEY `FK48u8v93a8nmwv58c7eyjp25nt` (`exercise_id`),
  CONSTRAINT `FK48u8v93a8nmwv58c7eyjp25nt` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `FKtj4uelcky6dvb0knrvgfufbyv` FOREIGN KEY (`student_exam_id`) REFERENCES `student_exam` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student_exam_exercise`
--

LOCK TABLES `student_exam_exercise` WRITE;
/*!40000 ALTER TABLE `student_exam_exercise` DISABLE KEYS */;
/*!40000 ALTER TABLE `student_exam_exercise` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `submission`
--

DROP TABLE IF EXISTS `submission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `submission` (
  `discriminator` varchar(31) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `jhi_type` varchar(255) DEFAULT NULL,
  `submitted` bit(1) DEFAULT NULL,
  `score_in_points` double DEFAULT NULL,
  `commit_hash` varchar(255) DEFAULT NULL,
  `participation_id` bigint DEFAULT NULL,
  `submission_date` datetime(6) DEFAULT NULL,
  `file_path` varchar(255) DEFAULT NULL,
  `text` longtext,
  `explanation_text` longtext,
  `example_submission` bit(1) DEFAULT NULL,
  `model` longtext,
  `language` varchar(10) DEFAULT NULL,
  `build_failed` bit(1) NOT NULL DEFAULT b'0',
  `build_artifact` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `FKeap67y7pxd6ffvwbs6d6sfhmq` (`participation_id`),
  KEY `submission_date` (`submission_date`),
  KEY `submission_discriminator` (`discriminator`),
  KEY `jhi_type` (`jhi_type`),
  CONSTRAINT `FKeap67y7pxd6ffvwbs6d6sfhmq` FOREIGN KEY (`participation_id`) REFERENCES `participation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `submission`
--

LOCK TABLES `submission` WRITE;
/*!40000 ALTER TABLE `submission` DISABLE KEYS */;
/*!40000 ALTER TABLE `submission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `submission_version`
--

DROP TABLE IF EXISTS `submission_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `submission_version` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `author_id` bigint NOT NULL,
  `submission_id` bigint NOT NULL,
  `content` longtext,
  `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_modified_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_submission_version_author_id` (`author_id`),
  KEY `fk_submission_version_submission_id` (`submission_id`),
  CONSTRAINT `fk_submission_version_author_id` FOREIGN KEY (`author_id`) REFERENCES `jhi_user` (`id`),
  CONSTRAINT `fk_submission_version_submission_id` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `submission_version`
--

LOCK TABLES `submission_version` WRITE;
/*!40000 ALTER TABLE `submission_version` DISABLE KEYS */;
/*!40000 ALTER TABLE `submission_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `submitted_answer`
--

DROP TABLE IF EXISTS `submitted_answer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `submitted_answer` (
  `discriminator` varchar(31) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `submission_id` bigint DEFAULT NULL,
  `quiz_question_id` bigint DEFAULT NULL,
  `score_in_points` double DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6drbcvqf26cahd0j1u9ff0rtr` (`quiz_question_id`),
  KEY `FKfwgc3a5vd0cxt5jsvvxm2tstf` (`submission_id`),
  CONSTRAINT `FK6drbcvqf26cahd0j1u9ff0rtr` FOREIGN KEY (`quiz_question_id`) REFERENCES `quiz_question` (`id`),
  CONSTRAINT `FKfwgc3a5vd0cxt5jsvvxm2tstf` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `submitted_answer`
--

LOCK TABLES `submitted_answer` WRITE;
/*!40000 ALTER TABLE `submitted_answer` DISABLE KEYS */;
/*!40000 ALTER TABLE `submitted_answer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `team`
--

DROP TABLE IF EXISTS `team`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `team` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `exercise_id` bigint DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `short_name` varchar(255) NOT NULL,
  `image` varchar(255) DEFAULT NULL,
  `owner_id` bigint DEFAULT NULL,
  `created_by` varchar(50) NOT NULL,
  `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_modified_by` varchar(50) DEFAULT NULL,
  `last_modified_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uc_team_exercise_id_and_short_name` (`exercise_id`,`short_name`),
  KEY `fk_team_owner_id` (`owner_id`),
  CONSTRAINT `fk_team_exercise_id` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `fk_team_owner_id` FOREIGN KEY (`owner_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `team`
--

LOCK TABLES `team` WRITE;
/*!40000 ALTER TABLE `team` DISABLE KEYS */;
/*!40000 ALTER TABLE `team` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `team_assignment_config`
--

DROP TABLE IF EXISTS `team_assignment_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `team_assignment_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `exercise_id` bigint DEFAULT NULL,
  `min_team_size` int NOT NULL DEFAULT '1',
  `max_team_size` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UC_TEAM_ASSIGNMENT_CONFIG_EXERCISE_ID_COL` (`exercise_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `team_assignment_config`
--

LOCK TABLES `team_assignment_config` WRITE;
/*!40000 ALTER TABLE `team_assignment_config` DISABLE KEYS */;
/*!40000 ALTER TABLE `team_assignment_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `team_student`
--

DROP TABLE IF EXISTS `team_student`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `team_student` (
  `team_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  PRIMARY KEY (`team_id`,`student_id`),
  KEY `fk_team_student_student_id` (`student_id`),
  CONSTRAINT `fk_team_student_student_id` FOREIGN KEY (`student_id`) REFERENCES `jhi_user` (`id`),
  CONSTRAINT `fk_team_student_team_id` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `team_student`
--

LOCK TABLES `team_student` WRITE;
/*!40000 ALTER TABLE `team_student` DISABLE KEYS */;
/*!40000 ALTER TABLE `team_student` ENABLE KEYS */;
UNLOCK TABLES;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50017 DEFINER=`root`@`%`*/ /*!50003 TRIGGER `uc_team_student_exercise_id_and_student_id` BEFORE INSERT ON `team_student` FOR EACH ROW BEGIN
                DECLARE conflict_exercise_id BIGINT(20);
                DECLARE conflict_team_id BIGINT(20);
                DECLARE error_message varchar(256);

                /* get id of exercise for which the student should be added to a team */
                SET @conflict_exercise_id :=
                    (SELECT exercise_id FROM team WHERE team.id = NEW.team_id);

                /* get id of other team to which the student already belongs to for exercise */
                SET @conflict_team_id :=
                    (SELECT team_student.team_id FROM team
                     LEFT JOIN team_student ON team.id = team_student.team_id
                     WHERE team.exercise_id = @conflict_exercise_id AND team_student.team_id != NEW.team_id AND team_student.student_id = NEW.student_id
                     LIMIT 1);

                /* if there is such a conflict team, abort the insert by throwing an error */
                IF @conflict_team_id THEN
                  SET @error_message := (
                      CONCAT_WS(' ', 'Trying to add student', cast(NEW.student_id as char), 'to team', cast(NEW.team_id as char), 'but the student is already part of team',
                                cast(@conflict_team_id as char), 'for exercise', cast(@conflict_exercise_id as char))
                  );
                  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = @error_message;
                END IF;
            END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;

--
-- Table structure for table `text_assessment_event`
--

DROP TABLE IF EXISTS `text_assessment_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `text_assessment_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `event_type` varchar(55) DEFAULT NULL,
  `feedback_type` varchar(55) DEFAULT NULL,
  `segment_type` varchar(55) DEFAULT NULL,
  `course_id` bigint DEFAULT NULL,
  `text_exercise_id` bigint DEFAULT NULL,
  `participation_id` bigint DEFAULT NULL,
  `submission_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `text_assessment_event`
--

LOCK TABLES `text_assessment_event` WRITE;
/*!40000 ALTER TABLE `text_assessment_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `text_assessment_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `text_assessment_knowledge`
--

DROP TABLE IF EXISTS `text_assessment_knowledge`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `text_assessment_knowledge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `text_assessment_knowledge`
--

LOCK TABLES `text_assessment_knowledge` WRITE;
/*!40000 ALTER TABLE `text_assessment_knowledge` DISABLE KEYS */;
/*!40000 ALTER TABLE `text_assessment_knowledge` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `text_block`
--

DROP TABLE IF EXISTS `text_block`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `text_block` (
  `id` char(40) COLLATE utf8_unicode_ci NOT NULL,
  `text` longtext COLLATE utf8_unicode_ci NOT NULL,
  `start_index` int NOT NULL,
  `end_index` int NOT NULL,
  `submission_id` bigint DEFAULT NULL,
  `cluster_id` bigint DEFAULT NULL,
  `position_in_cluster` int DEFAULT NULL,
  `added_distance` float DEFAULT NULL,
  `type` varchar(255) COLLATE utf8_unicode_ci DEFAULT 'MANUAL',
  `feedback_id` bigint DEFAULT NULL,
  `knowledge_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_text_block_submission_id` (`submission_id`),
  KEY `fk_text_block_cluster_id` (`cluster_id`),
  KEY `fk_text_block_knowledge_id` (`knowledge_id`),
  CONSTRAINT `fk_text_block_cluster_id` FOREIGN KEY (`cluster_id`) REFERENCES `text_cluster` (`id`),
  CONSTRAINT `fk_text_block_knowledge_id` FOREIGN KEY (`knowledge_id`) REFERENCES `text_assessment_knowledge` (`id`),
  CONSTRAINT `fk_text_block_submission_id` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `text_block`
--

LOCK TABLES `text_block` WRITE;
/*!40000 ALTER TABLE `text_block` DISABLE KEYS */;
/*!40000 ALTER TABLE `text_block` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `text_cluster`
--

DROP TABLE IF EXISTS `text_cluster`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `text_cluster` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `probabilities` longblob,
  `distance_matrix` longblob,
  `exercise_id` bigint DEFAULT NULL,
  `disabled` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `fk_text_block_exercise_id` (`exercise_id`),
  CONSTRAINT `fk_text_block_exercise_id` FOREIGN KEY (`exercise_id`) REFERENCES `exercise` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `text_cluster`
--

LOCK TABLES `text_cluster` WRITE;
/*!40000 ALTER TABLE `text_cluster` DISABLE KEYS */;
/*!40000 ALTER TABLE `text_cluster` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `text_exercise_details`
--

DROP TABLE IF EXISTS `text_exercise_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `text_exercise_details` (
  `id` bigint NOT NULL,
  `knowledge_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_text_exercise_knowledge_id` (`knowledge_id`),
  CONSTRAINT `fk_text_exercise_details` FOREIGN KEY (`id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `fk_text_exercise_knowledge_id` FOREIGN KEY (`knowledge_id`) REFERENCES `text_assessment_knowledge` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `text_exercise_details`
--

LOCK TABLES `text_exercise_details` WRITE;
/*!40000 ALTER TABLE `text_exercise_details` DISABLE KEYS */;
/*!40000 ALTER TABLE `text_exercise_details` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tutor_group`
--

DROP TABLE IF EXISTS `tutor_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tutor_group` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `capacity` int DEFAULT NULL,
  `language` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `room` varchar(255) DEFAULT NULL,
  `time_slot` varchar(255) DEFAULT NULL,
  `weekday` varchar(255) DEFAULT NULL,
  `course_id` bigint DEFAULT NULL,
  `tutor_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKcf8nhxegaw4eemea12s6q6p5j` (`tutor_id`),
  KEY `FKhlflc2uq6jxpt1ajaru95ud2c` (`course_id`),
  CONSTRAINT `FKcf8nhxegaw4eemea12s6q6p5j` FOREIGN KEY (`tutor_id`) REFERENCES `jhi_user` (`id`),
  CONSTRAINT `FKhlflc2uq6jxpt1ajaru95ud2c` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tutor_group`
--

LOCK TABLES `tutor_group` WRITE;
/*!40000 ALTER TABLE `tutor_group` DISABLE KEYS */;
/*!40000 ALTER TABLE `tutor_group` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tutor_group_students`
--

DROP TABLE IF EXISTS `tutor_group_students`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tutor_group_students` (
  `tutor_group_id` bigint NOT NULL,
  `students_id` bigint NOT NULL,
  PRIMARY KEY (`tutor_group_id`,`students_id`),
  KEY `FK3kx69s41qyv7k9s10ia5im91n` (`students_id`),
  CONSTRAINT `FK2cvxih0qjqys4ld9gdol39ngj` FOREIGN KEY (`tutor_group_id`) REFERENCES `tutor_group` (`id`),
  CONSTRAINT `FK3kx69s41qyv7k9s10ia5im91n` FOREIGN KEY (`students_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tutor_group_students`
--

LOCK TABLES `tutor_group_students` WRITE;
/*!40000 ALTER TABLE `tutor_group_students` DISABLE KEYS */;
/*!40000 ALTER TABLE `tutor_group_students` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tutor_participation`
--

DROP TABLE IF EXISTS `tutor_participation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tutor_participation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `points` int DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `assessed_exercise_id` bigint DEFAULT NULL,
  `tutor_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6sioc0zhvp2bxcl4fi5labvpa` (`tutor_id`,`assessed_exercise_id`),
  KEY `fk_tutor_participation_exercise_id` (`assessed_exercise_id`),
  CONSTRAINT `fk_tutor_participation_exercise_id` FOREIGN KEY (`assessed_exercise_id`) REFERENCES `exercise` (`id`),
  CONSTRAINT `fk_tutor_participation_student_id` FOREIGN KEY (`tutor_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tutor_participation`
--

LOCK TABLES `tutor_participation` WRITE;
/*!40000 ALTER TABLE `tutor_participation` DISABLE KEYS */;
/*!40000 ALTER TABLE `tutor_participation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tutor_participation_trained_example_submissions`
--

DROP TABLE IF EXISTS `tutor_participation_trained_example_submissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tutor_participation_trained_example_submissions` (
  `tutor_participation_id` bigint NOT NULL,
  `trained_example_submissions_id` bigint NOT NULL,
  PRIMARY KEY (`tutor_participation_id`,`trained_example_submissions_id`),
  KEY `FKadxqkurtxseqg3esqrajuanb2` (`trained_example_submissions_id`),
  CONSTRAINT `FKadxqkurtxseqg3esqrajuanb2` FOREIGN KEY (`trained_example_submissions_id`) REFERENCES `example_submission` (`id`),
  CONSTRAINT `FKmjdonimu07r4di2f49qpuajhs` FOREIGN KEY (`tutor_participation_id`) REFERENCES `tutor_participation` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tutor_participation_trained_example_submissions`
--

LOCK TABLES `tutor_participation_trained_example_submissions` WRITE;
/*!40000 ALTER TABLE `tutor_participation_trained_example_submissions` DISABLE KEYS */;
/*!40000 ALTER TABLE `tutor_participation_trained_example_submissions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_groups`
--

DROP TABLE IF EXISTS `user_groups`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user_groups` (
  `user_id` bigint NOT NULL,
  `groups` varchar(255) DEFAULT NULL,
  KEY `FK_qhsd8ovn89o4usyr3fgaqkdjt` (`user_id`),
  KEY `user_groups_groups` (`groups`),
  CONSTRAINT `FK_qhsd8ovn89o4usyr3fgaqkdjt` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_groups`
--

LOCK TABLES `user_groups` WRITE;
/*!40000 ALTER TABLE `user_groups` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_groups` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_organization`
--

DROP TABLE IF EXISTS `user_organization`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user_organization` (
  `user_id` bigint NOT NULL,
  `organization_id` bigint NOT NULL,
  PRIMARY KEY (`organization_id`,`user_id`),
  KEY `FKnfyuy63doxihtl3pu8rnm46n4` (`user_id`),
  CONSTRAINT `FKfdnaj8emi62iffmg6w6ykjxf4` FOREIGN KEY (`organization_id`) REFERENCES `organization` (`id`),
  CONSTRAINT `FKnfyuy63doxihtl3pu8rnm46n4` FOREIGN KEY (`user_id`) REFERENCES `jhi_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_organization`
--

LOCK TABLES `user_organization` WRITE;
/*!40000 ALTER TABLE `user_organization` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_organization` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2021-10-30 21:10:52
