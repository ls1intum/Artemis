package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.service.FeedbackCreationService;

class FeedbackCreationServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private FeedbackCreationService feedbackCreationService;

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
                \tat test.MethodTest.lambda$0(MethodTest.java:52)""".stripIndent();
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
                \tat org.hibernate.loader.plan.exec.process.internal.ResultSetProcessorImpl.extractResults(ResultSetProcessorImpl.java:94)"""
                .stripIndent();
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
                """.stripIndent();
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
                but was not.""".stripIndent());
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
    void createFeedbackFromTimeoutMessage() {
        final String message = """
                ERROR: org.junit.runners.model.TestTimedOutException: detail message
                \tat dummy stack trace
                """;

        final Feedback feedback = feedbackCreationService.createFeedbackFromTestCase("test1", List.of(message), false, ProgrammingLanguage.JAVA, ProjectType.PLAIN_GRADLE);
        assertThat(feedback.getDetailText()).contains("The test case execution timed out.").contains("Exception message: detail message");
    }

    @Test
    void createFeedbackFromGeneralTimeoutMessage() {
        final String message = """
                exit: Jenkins build run timed out after 120s.
                """;

        final Feedback feedback = feedbackCreationService.createFeedbackFromTestCase("test1", List.of(message), false, ProgrammingLanguage.C, null);
        assertThat(feedback.getDetailText()).contains("The test case execution timed out.").contains("Exception message: Jenkins build run timed out after 120s.");
    }

    @Test
    void trimPythonAssertionFailure() {
        final String message = """
                def test__failed():
                >       assert 1 == 2
                E       assert 1 == 2

                tests/test_cli.py:89: AssertionError
                """.stripIndent();

        final Feedback feedback = feedbackCreationService.createFeedbackFromTestCase("test1", List.of(message), false, ProgrammingLanguage.PYTHON, null);
        assertThat(feedback.getDetailText()).startsWith("assert 1 == 2").contains(message);
    }

    @Test
    void trimPythonAssertionFailureWithMessage() {
        final String message = """
                def test__failed():
                >       assert 1 == 2, "some message"
                E       AssertionError: some message
                E       assert 1 == 2

                tests/test_cli.py:89: AssertionError
                """.stripIndent();

        final Feedback feedback = feedbackCreationService.createFeedbackFromTestCase("test1", List.of(message), false, ProgrammingLanguage.PYTHON, null);
        assertThat(feedback.getDetailText()).startsWith("AssertionError: some message").contains(message);
    }

    @Test
    void pythonMessageNotMatchingUnchanged() {
        final String message = """
                AssertionError: some message

                Not trimmed
                """;

        final Feedback feedback = feedbackCreationService.createFeedbackFromTestCase("test1", List.of(message), false, ProgrammingLanguage.PYTHON, null);
        assertThat(feedback.getDetailText()).isEqualTo(message);
    }

    @Test
    void gradleFailureMessage() {
        final String message = """
                org.opentest4j.AssertionFailedError: expected: <1> but was: <2>
                \tat app//org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:151)
                """.stripIndent();

        final Feedback feedback = feedbackCreationService.createFeedbackFromTestCase("test1", List.of(message), false, ProgrammingLanguage.JAVA, ProjectType.GRADLE_GRADLE);
        assertThat(feedback.getDetailText()).doesNotContain("AssertionFailedError").isEqualTo("expected: <1> but was: <2>");
    }

    @Test
    void gradleAssertJFailureMessage() {
        final String message = """
                org.opentest4j.AssertionFailedError:
                  expected: "a
                  bc"
                  but was: "d
                  ef"
                \tat java.base@17.0.6/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
                """.stripIndent();

        final Feedback feedback = feedbackCreationService.createFeedbackFromTestCase("test1", List.of(message), false, ProgrammingLanguage.JAVA, ProjectType.GRADLE_GRADLE);
        assertThat(feedback.getDetailText()).doesNotContain("AssertionFailedError").contains("expected: \"a").contains("but was: \"d").contains("ef\"");
    }
}
