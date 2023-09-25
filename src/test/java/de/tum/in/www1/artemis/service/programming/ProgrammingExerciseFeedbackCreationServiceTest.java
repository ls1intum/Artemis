package de.tum.in.www1.artemis.service.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.enumeration.StaticCodeAnalysisTool;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseFactory;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;

class ProgrammingExerciseFeedbackCreationServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "progexfeedbackcreaiontest";

    @Autowired
    private ProgrammingExerciseFeedbackCreationService feedbackCreationService;

    @Test
    void createFeedbackFromTestCaseCombineMultiple() {
        String msg1 = """
                java.lang.AssertionError: expected:
                    4
                but was:
                    5""";
        String msg2 = """
                org.opentest4j.AssertionFailedError: Message2
                with additions""";
        String msg3 = """
                org.opentest4j.AssertionFailedError:\s
                message is only here on the next line:
                  expected:
                    []
                  to contain:
                    ["Java"]""";

        String actualFeedback = feedbackCreationService.createFeedbackFromTestCase("test1", List.of(msg1, msg2, msg3), false, ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN)
                .getDetailText();

        assertThat(actualFeedback).isEqualTo("""
                expected:
                    4
                but was:
                    5

                Message2
                with additions

                message is only here on the next line:
                  expected:
                    []
                  to contain:
                    ["Java"]""");

    }

    @Test
    void createFeedbackFromTestCaseMatchMultiple() {
        String msgMatchMultiple = """
                java.lang.AssertionError: expected:
                    4
                but was:
                    5
                org.opentest4j.AssertionFailedError: expected:
                    something else""";
        String actualFeedback = feedbackCreationService.createFeedbackFromTestCase("test2", List.of(msgMatchMultiple), false, ProgrammingLanguage.KOTLIN, null).getDetailText();
        assertThat(actualFeedback).isEqualTo("""
                expected:
                    4
                but was:
                    5
                expected:
                    something else""");
    }

    @Test
    void createFeedbackFromTestCaseUnchanged() {
        String msgUnchanged = "Should not be changed";
        String actualFeedback = feedbackCreationService.createFeedbackFromTestCase("test3", List.of(msgUnchanged), false, ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN)
                .getDetailText();
        assertThat(actualFeedback).isEqualTo(msgUnchanged);

    }

    @Test
    void createFeedbackFromTestCaseWithStackTrace() {
        String msgWithStackTrace = """
                org.opentest4j.AssertionFailedError: the expected method 'getDates' of the class 'Context' with no parameters was not found or is named wrongly.
                \tat test.MethodTest.checkMethods(MethodTest.java:129)
                \tat test.MethodTest.testMethods(MethodTest.java:72)
                \tat test.MethodTest.lambda$0(MethodTest.java:52)""";
        String actualFeedback = feedbackCreationService.createFeedbackFromTestCase("test1", List.of(msgWithStackTrace), false, ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN)
                .getDetailText();
        assertThat(actualFeedback).isEqualTo("the expected method 'getDates' of the class 'Context' with no parameters was not found or is named wrongly.");
    }

    @Test
    void createFeedbackFromTestCaseWithStackTraceAndCause() {
        String msgWithStackTrace = """
                org.springframework.orm.jpa.JpaSystemException: org.springframework.orm.jpa.JpaSystemException: null index column for collection: de.tum.in.www1.artemis.domain.exam.Exam.exerciseGroups
                \tat org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:353)
                \tat org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:255)
                \tat org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:528)
                \tat org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
                \tat org.springframework.dao.support.DataAccessUtils.translateIfNecessary(DataAccessUtils.java:242)
                Caused by: org.hibernate.HibernateException: null index column for collection: de.tum.in.www1.artemis.domain.exam.Exam.exerciseGroups
                \tat org.hibernate.persister.collection.AbstractCollectionPersister.readIndex(AbstractCollectionPersister.java:874)
                \tat org.hibernate.collection.internal.PersistentList.readFrom(PersistentList.java:401)
                \tat org.hibernate.loader.plan.exec.process.internal.CollectionReferenceInitializerImpl.finishUpRow(CollectionReferenceInitializerImpl.java:76)
                \tat org.hibernate.loader.plan.exec.process.internal.AbstractRowReader.readRow(AbstractRowReader.java:125)
                \tat org.hibernate.loader.plan.exec.process.internal.ResultSetProcessorImpl.extractRows(ResultSetProcessorImpl.java:157)
                \tat org.hibernate.loader.plan.exec.process.internal.ResultSetProcessorImpl.extractResults(ResultSetProcessorImpl.java:94)""";
        String actualFeedback = feedbackCreationService.createFeedbackFromTestCase("test1", List.of(msgWithStackTrace), false, ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN)
                .getDetailText();
        assertThat(actualFeedback).isEqualTo(
                "org.springframework.orm.jpa.JpaSystemException: org.springframework.orm.jpa.JpaSystemException: null index column for collection: de.tum.in.www1.artemis.domain.exam.Exam.exerciseGroups");
    }

    @Test
    void createFeedbackFromTestCaseOfAssertJ() {
        String msgWithStackTrace = """
                org.opentest4j.AssertionFailedError:\s
                Expecting:
                 <"expected:
                    4
                but was:XXXXXXXXXXXXX
                    5
                expected:
                    something else">
                to be equal to:
                 <"expected:
                    4
                but was:
                    5
                expected:
                    something else">
                but was not.
                \tat java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
                \tat java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:64)
                \tat java.base/jdk.internal.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
                \tat java.base/java.lang.reflect.Constructor.newInstanceWithCaller(Constructor.java:500)
                \tat de.tum.in.www1.artemis.FeedbackServiceTest.createFeedbackFromTestCaseMatchMultiple(FeedbackServiceTest.java:66)
                """;
        String actualFeedback = feedbackCreationService.createFeedbackFromTestCase("test1", List.of(msgWithStackTrace), false, ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN)
                .getDetailText();
        assertThat(actualFeedback).isEqualTo("""
                Expecting:
                 <"expected:
                    4
                but was:XXXXXXXXXXXXX
                    5
                expected:
                    something else">
                to be equal to:
                 <"expected:
                    4
                but was:
                    5
                expected:
                    something else">
                but was not.""");
    }

    @Test
    void createFeedbackFromTestCaseSuccessfulWithMessage() {
        String msg = "success\nmessage";
        assertThat(feedbackCreationService.createFeedbackFromTestCase("test1", List.of(msg), true, ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN).getDetailText())
                .isEqualTo("success\nmessage");
    }

    @Test
    void createFeedbackFromTestCaseSuccessfulNoMessage() {
        assertThat(feedbackCreationService.createFeedbackFromTestCase("test1", List.of(), true, ProgrammingLanguage.JAVA, ProjectType.PLAIN_MAVEN).getDetailText()).isNull();
    }

    @Test
    void staticCodeAnalysisReportNotTruncatedFurther() {
        final String longText = "0".repeat(Constants.FEEDBACK_DETAIL_TEXT_SOFT_MAX_LENGTH * 2);

        final var scaIssue = new StaticCodeAnalysisReportDTO.StaticCodeAnalysisIssue();
        scaIssue.setCategory("scaCategory");
        scaIssue.setMessage(longText);
        scaIssue.setStartColumn(0);
        scaIssue.setEndColumn(123);
        scaIssue.setFilePath("some/long/file/Path.java");

        final StaticCodeAnalysisReportDTO scaReport = new StaticCodeAnalysisReportDTO();
        scaReport.setTool(StaticCodeAnalysisTool.CHECKSTYLE);
        scaReport.setIssues(List.of(scaIssue));

        final List<Feedback> scaFeedbacks = feedbackCreationService.createFeedbackFromStaticCodeAnalysisReports(List.of(scaReport));
        assertThat(scaFeedbacks).hasSize(1);

        final Feedback scaFeedback = scaFeedbacks.get(0);
        assertThat(scaFeedback.getHasLongFeedbackText()).isFalse();
        assertThat(scaFeedback.getLongFeedback()).isEmpty();
        assertThat(scaFeedback.getDetailText()).hasSizeGreaterThan(Constants.FEEDBACK_DETAIL_TEXT_SOFT_MAX_LENGTH)
                .hasSizeLessThanOrEqualTo(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRemoveCIDirectoriesFromPath() {
        // 1. Test that paths not containing the Constant.STUDENT_WORKING_DIRECTORY are not shortened
        String pathWithoutWorkingDir = "Path/Without/StudentWorkingDirectory/Constant";

        var resultNotification1 = ProgrammingExerciseFactory.generateBambooBuildResultWithStaticCodeAnalysisReport(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(),
                ProgrammingLanguage.JAVA);
        for (var reports : resultNotification1.getBuild().jobs().iterator().next().staticCodeAnalysisReports()) {
            for (var issue : reports.getIssues()) {
                issue.setFilePath(pathWithoutWorkingDir);
            }
        }
        var staticCodeAnalysisFeedback1 = feedbackCreationService
                .createFeedbackFromStaticCodeAnalysisReports(resultNotification1.getBuild().jobs().get(0).staticCodeAnalysisReports());

        for (var feedback : staticCodeAnalysisFeedback1) {
            JSONObject issueJSON = new JSONObject(feedback.getDetailText());
            assertThat(pathWithoutWorkingDir).isEqualTo(issueJSON.get("filePath"));
        }

        // 2. Test that null or empty paths default to FeedbackRepository.DEFAULT_FILEPATH
        var resultNotification2 = ProgrammingExerciseFactory.generateBambooBuildResultWithStaticCodeAnalysisReport(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(),
                ProgrammingLanguage.JAVA);
        var reports2 = resultNotification2.getBuild().jobs().iterator().next().staticCodeAnalysisReports();
        for (int i = 0; i < reports2.size(); i++) {
            var report = reports2.get(i);
            // Set null or empty String to test both
            if (i % 2 == 0) {
                for (var issue : report.getIssues()) {
                    issue.setFilePath("");
                }
            }
            else {
                for (var issue : report.getIssues()) {
                    issue.setFilePath(null);
                }
            }
        }
        final List<Feedback> staticCodeAnalysisFeedback2 = feedbackCreationService
                .createFeedbackFromStaticCodeAnalysisReports(resultNotification2.getBuild().jobs().get(0).staticCodeAnalysisReports());

        for (var feedback : staticCodeAnalysisFeedback2) {
            JSONObject issueJSON = new JSONObject(feedback.getDetailText());
            assertThat(issueJSON.get("filePath")).isEqualTo("notAvailable");
        }
    }
}
