package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTestCaseType;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.service.dto.AbstractBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseFeedbackCreationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseTestCaseService;
import de.tum.in.www1.artemis.util.ModelFactory;

class ProgrammingExerciseFeedbackCreationServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseFeedbackCreationService feedbackCreationService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    private ProgrammingExerciseTestCaseService testCaseService;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void init() {
        Course course = database.addCourseWithOneProgrammingExercise();
        programmingExercise = (ProgrammingExercise) course.getExercises().iterator().next();
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setProjectType(ProjectType.PLAIN_MAVEN);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        database.addTestCasesToProgrammingExercise(programmingExercise);
        // programmingExercise = programmingExerciseRepository
        // .findByIdWithEagerTestCasesStaticCodeAnalysisCategoriesHintsAndTemplateAndSolutionParticipationsAndAuxRepos(programmingExercise.getId()).orElseThrow();
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

        String actualFeedback = feedbackCreationService.createFeedbackFromTestCase("test1", List.of(msg1, msg2, msg3), false, programmingExercise).getDetailText();

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
        String actualFeedback = feedbackCreationService.createFeedbackFromTestCase("test2", List.of(msgMatchMultiple), false, programmingExercise).getDetailText();
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
        String actualFeedback = feedbackCreationService.createFeedbackFromTestCase("test3", List.of(msgUnchanged), false, programmingExercise).getDetailText();
        assertThat(actualFeedback).isEqualTo(msgUnchanged);

    }

    @Test
    void createFeedbackFromTestCaseWithStackTrace() {
        String msgWithStackTrace = """
                org.opentest4j.AssertionFailedError: the expected method 'getDates' of the class 'Context' with no parameters was not found or is named wrongly.
                \tat test.MethodTest.checkMethods(MethodTest.java:129)
                \tat test.MethodTest.testMethods(MethodTest.java:72)
                \tat test.MethodTest.lambda$0(MethodTest.java:52)""";
        String actualFeedback = feedbackCreationService.createFeedbackFromTestCase("test1", List.of(msgWithStackTrace), false, programmingExercise).getDetailText();
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
        String actualFeedback = feedbackCreationService.createFeedbackFromTestCase("test1", List.of(msgWithStackTrace), false, programmingExercise).getDetailText();
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
        String actualFeedback = feedbackCreationService.createFeedbackFromTestCase("test1", List.of(msgWithStackTrace), false, programmingExercise).getDetailText();
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
        assertThat(feedbackCreationService.createFeedbackFromTestCase("test1", List.of(msg), true, programmingExercise).getDetailText()).isEqualTo("success\nmessage");
    }

    @Test
    void createFeedbackFromTestCaseSuccessfulNoMessage() {
        assertThat(feedbackCreationService.createFeedbackFromTestCase("test1", List.of(), true, programmingExercise).getDetailText()).isEqualTo(null);
    }

    private AbstractBuildResultNotificationDTO generateResult(List<String> successfulTests, List<String> failedTests) {
        return ModelFactory.generateBambooBuildResult("SOLUTION", null, null, null, successfulTests, failedTests, null);
    }

    @Test
    void shouldSetAllTestCasesToInactiveIfFeedbackListIsEmpty() {
        var result = generateResult(Collections.emptyList(), Collections.emptyList());
        feedbackCreationService.generateTestCasesFromBuildResult(result, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(3).noneMatch(ProgrammingExerciseTestCase::isActive);
    }

    @Test
    void shouldUpdateActiveFlagsOfTestCases() {
        var result = generateResult(List.of("test1", "test2"), List.of("test4", "test5"));
        feedbackCreationService.generateTestCasesFromBuildResult(result, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(5).allMatch(testCase -> {
            if ("test3".equals(testCase.getTestName())) {
                return !testCase.isActive();
            }
            else {
                return testCase.isActive();
            }
        });
    }

    @Test
    void shouldGenerateNewTestCases() {
        // We do not want to use the test cases generated in the setup
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(programmingExercise.getId()));

        var result = generateResult(List.of("test1", "test2"), Collections.emptyList());
        feedbackCreationService.generateTestCasesFromBuildResult(result, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(2);

        assertThat(testCases.stream().allMatch(ProgrammingExerciseTestCase::isActive)).isTrue();
    }

    @Test
    void shouldFilterOutDuplicateTestCases() {
        // We do not want to use the test cases generated in the setup
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(programmingExercise.getId()));

        var result = generateResult(List.of("test1"), List.of("generateTestsForAllClasses", "generateTestsForAllClasses", "generateTestsForAllClasses"));
        feedbackCreationService.generateTestCasesFromBuildResult(result, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(2);
    }

    @Test
    void shouldMapStructuralTestCaseTypesCorrectly() {
        Set<ProgrammingExerciseTestCase> structuralTestCases = Set.of(new ProgrammingExerciseTestCase().testName("testClass[Policy]").exercise(programmingExercise),
                new ProgrammingExerciseTestCase().testName("testConstructors[BubbleSort]").exercise(programmingExercise),
                new ProgrammingExerciseTestCase().testName("testMethods[Context]").exercise(programmingExercise),
                new ProgrammingExerciseTestCase().testName("testAttributes[Starter]").exercise(programmingExercise));

        feedbackCreationService.setTestCaseType(structuralTestCases, ProgrammingLanguage.JAVA);
        assertThat(structuralTestCases).allMatch(testCase -> testCase.getType() == ProgrammingExerciseTestCaseType.STRUCTURAL);
    }

    @Test
    void shouldMapBehavioralTestCaseTypesCorrectly() {
        Set<ProgrammingExerciseTestCase> behavioralTestCases = Set.of(new ProgrammingExerciseTestCase().testName("testBubbleSort").exercise(programmingExercise),
                new ProgrammingExerciseTestCase().testName("testMergeSort").exercise(programmingExercise),
                new ProgrammingExerciseTestCase().testName("test13412").exercise(programmingExercise),
                new ProgrammingExerciseTestCase().testName("HiddenRandomTest").exercise(programmingExercise));

        feedbackCreationService.setTestCaseType(behavioralTestCases, ProgrammingLanguage.JAVA);
        assertThat(behavioralTestCases).allMatch(testCase -> testCase.getType() == ProgrammingExerciseTestCaseType.BEHAVIORAL);
    }

    @Test
    void shouldMapNonJavaTestsToDefaultTestCaseType() {
        Set<ProgrammingExerciseTestCase> testCases;

        for (ProgrammingLanguage language : ProgrammingLanguage.values()) {
            if (language == ProgrammingLanguage.JAVA) {
                continue;
            }
            testCases = Set.of(new ProgrammingExerciseTestCase().testName("testBubbleSort").exercise(programmingExercise),
                    new ProgrammingExerciseTestCase().testName("testMergeSort").exercise(programmingExercise),
                    new ProgrammingExerciseTestCase().testName("test13412").exercise(programmingExercise),
                    new ProgrammingExerciseTestCase().testName("HiddenRandomTest").exercise(programmingExercise));
            feedbackCreationService.setTestCaseType(testCases, language);
            assertThat(testCases).allMatch(testCase -> testCase.getType() == ProgrammingExerciseTestCaseType.DEFAULT);
        }
    }
}
