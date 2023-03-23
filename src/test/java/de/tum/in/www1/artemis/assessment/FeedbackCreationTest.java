package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.AbstractContinuousIntegrationService;

class FeedbackCreationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private AbstractContinuousIntegrationService continuousIntegrationService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void init() {
        Course course = database.addCourseWithOneProgrammingExercise();
        programmingExercise = (ProgrammingExercise) course.getExercises().iterator().next();
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setProjectType(ProjectType.PLAIN_MAVEN);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
    }

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

        String actualFeedback = continuousIntegrationService.createFeedbackFromTestCase("test1", List.of(msg1, msg2, msg3), false, programmingExercise).getDetailText();

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
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.KOTLIN);
        programmingExercise.setProjectType(null);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        String actualFeedback = continuousIntegrationService.createFeedbackFromTestCase("test2", List.of(msgMatchMultiple), false, programmingExercise).getDetailText();
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
        String actualFeedback = continuousIntegrationService.createFeedbackFromTestCase("test3", List.of(msgUnchanged), false, programmingExercise).getDetailText();
        assertThat(actualFeedback).isEqualTo(msgUnchanged);

    }

    @Test
    void createFeedbackFromTestCaseWithStackTrace() {
        String msgWithStackTrace = """
                org.opentest4j.AssertionFailedError: the expected method 'getDates' of the class 'Context' with no parameters was not found or is named wrongly.
                \tat test.MethodTest.checkMethods(MethodTest.java:129)
                \tat test.MethodTest.testMethods(MethodTest.java:72)
                \tat test.MethodTest.lambda$0(MethodTest.java:52)""";
        String actualFeedback = continuousIntegrationService.createFeedbackFromTestCase("test1", List.of(msgWithStackTrace), false, programmingExercise).getDetailText();
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
        String actualFeedback = continuousIntegrationService.createFeedbackFromTestCase("test1", List.of(msgWithStackTrace), false, programmingExercise).getDetailText();
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
        String actualFeedback = continuousIntegrationService.createFeedbackFromTestCase("test1", List.of(msgWithStackTrace), false, programmingExercise).getDetailText();
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
        assertThat(continuousIntegrationService.createFeedbackFromTestCase("test1", List.of(msg), true, programmingExercise).getDetailText()).isEqualTo("success\nmessage");
    }

    @Test
    void createFeedbackFromTestCaseSuccessfulNoMessage() {
        assertThat(continuousIntegrationService.createFeedbackFromTestCase("test1", List.of(), true, programmingExercise).getDetailText()).isEqualTo(null);
    }
}
