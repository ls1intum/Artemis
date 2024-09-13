package de.tum.cit.aet.artemis.service.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.ExamUtilService;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.programming.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.exercise.programming.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseTestCaseType;
import de.tum.cit.aet.artemis.programming.dto.AbstractBuildResultNotificationDTO;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseFeedbackCreationService;

class ProgrammingExerciseFeedbackCreationServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ProgrammingExerciseFeedbackCreationService feedbackCreationService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void init() {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = (ProgrammingExercise) course.getExercises().iterator().next();
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setProjectType(ProjectType.PLAIN_MAVEN);
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);
    }

    private String createFeedbackFromTestCase(String testName, List<String> testMessages, boolean successful) {
        var activeTestCases = testCaseRepository.findByExerciseIdAndActive(programmingExercise.getId(), true);
        return feedbackCreationService.createFeedbackFromTestCase(testName, testMessages, successful, programmingExercise, activeTestCases).getDetailText();
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

        String actualFeedback = createFeedbackFromTestCase("test1", List.of(msg1, msg2, msg3), false);

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
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        String actualFeedback = createFeedbackFromTestCase("test2", List.of(msgMatchMultiple), false);
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
        String actualFeedback = createFeedbackFromTestCase("test3", List.of(msgUnchanged), false);
        assertThat(actualFeedback).isEqualTo(msgUnchanged);

    }

    @Test
    void createFeedbackFromTestCaseWithStackTrace() {
        String msgWithStackTrace = """
                org.opentest4j.AssertionFailedError: the expected method 'getDates' of the class 'Context' with no parameters was not found or is named wrongly.
                \tat test.MethodTest.checkMethods(MethodTest.java:129)
                \tat test.MethodTest.testMethods(MethodTest.java:72)
                \tat test.MethodTest.lambda$0(MethodTest.java:52)""";
        String actualFeedback = createFeedbackFromTestCase("test1", List.of(msgWithStackTrace), false);
        assertThat(actualFeedback).isEqualTo("the expected method 'getDates' of the class 'Context' with no parameters was not found or is named wrongly.");
    }

    @Test
    void createFeedbackFromTestCaseWithStackTraceAndCause() {
        String msgWithStackTrace = """
                org.springframework.orm.jpa.JpaSystemException: org.springframework.orm.jpa.JpaSystemException: null index column for collection: de.tum.cit.aet.artemis.domain.exam.Exam.exerciseGroups
                \tat org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException(HibernateJpaDialect.java:353)
                \tat org.springframework.orm.jpa.vendor.HibernateJpaDialect.translateExceptionIfPossible(HibernateJpaDialect.java:255)
                \tat org.springframework.orm.jpa.AbstractEntityManagerFactoryBean.translateExceptionIfPossible(AbstractEntityManagerFactoryBean.java:528)
                \tat org.springframework.dao.support.ChainedPersistenceExceptionTranslator.translateExceptionIfPossible(ChainedPersistenceExceptionTranslator.java:61)
                \tat org.springframework.dao.support.DataAccessUtils.translateIfNecessary(DataAccessUtils.java:242)
                Caused by: org.hibernate.HibernateException: null index column for collection: de.tum.cit.aet.artemis.domain.exam.Exam.exerciseGroups
                \tat org.hibernate.persister.collection.AbstractCollectionPersister.readIndex(AbstractCollectionPersister.java:874)
                \tat org.hibernate.collection.internal.PersistentList.readFrom(PersistentList.java:401)
                \tat org.hibernate.loader.plan.exec.process.internal.CollectionReferenceInitializerImpl.finishUpRow(CollectionReferenceInitializerImpl.java:76)
                \tat org.hibernate.loader.plan.exec.process.internal.AbstractRowReader.readRow(AbstractRowReader.java:125)
                \tat org.hibernate.loader.plan.exec.process.internal.ResultSetProcessorImpl.extractRows(ResultSetProcessorImpl.java:157)
                \tat org.hibernate.loader.plan.exec.process.internal.ResultSetProcessorImpl.extractResults(ResultSetProcessorImpl.java:94)""";
        String actualFeedback = createFeedbackFromTestCase("test1", List.of(msgWithStackTrace), false);
        assertThat(actualFeedback).isEqualTo(
                "org.springframework.orm.jpa.JpaSystemException: org.springframework.orm.jpa.JpaSystemException: null index column for collection: de.tum.cit.aet.artemis.domain.exam.Exam.exerciseGroups");
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
                \tat de.tum.cit.aet.artemis.FeedbackServiceTest.createFeedbackFromTestCaseMatchMultiple(FeedbackServiceTest.java:66)
                """;
        String actualFeedback = createFeedbackFromTestCase("test1", List.of(msgWithStackTrace), false);
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
    void createTimeoutFeedback() {
        String msgWithStackTrace = """
                org.opentest4j.AssertionFailedError: execution timed out after 1 s
                \tat de.tum.in.test.api.localization.Messages.localizedFailure(Messages.java:34)
                \tat de.tum.in.test.api.internal.TimeoutUtils.generateTimeoutFailure(TimeoutUtils.java:79)
                \tat de.tum.in.test.api.internal.TimeoutUtils.executeWithTimeout(TimeoutUtils.java:72)
                \tat de.tum.in.test.api.internal.TimeoutUtils.performTimeoutExecution(TimeoutUtils.java:42)
                    """;
        String actualFeedback = createFeedbackFromTestCase("test1", List.of(msgWithStackTrace), false);
        assertThat(actualFeedback)
                .isEqualTo("The test case execution timed out. This indicates issues in your code such as endless loops, issues with recursion or really slow performance. "
                        + "Please carefully review your code to avoid such issues. In case you are absolutely sure that there are no issues like this, please contact your instructor to check the setup of the test.\n"
                        + "Exception message: execution timed out after 1 s");
    }

    @Test
    void createFeedbackFromTestCaseSuccessfulWithMessage() {
        String msg = "success\nmessage";
        assertThat(createFeedbackFromTestCase("test1", List.of(msg), true)).isEqualTo("success\nmessage");
    }

    @Test
    void createFeedbackFromTestCaseSuccessfulNoMessage() {
        assertThat(createFeedbackFromTestCase("test1", List.of(), true)).isNull();
    }

    private AbstractBuildResultNotificationDTO generateResult(List<String> successfulTests, List<String> failedTests) {
        return ProgrammingExerciseFactory.generateTestResultDTO(null, "SOLUTION", null, programmingExercise.getProgrammingLanguage(), false, successfulTests, failedTests, null,
                null, null);
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
    void shouldGenerateNewTestCasesWithVisibilityAlways() {
        programmingExercise.setExerciseGroup(null);

        testGenerateNewTestCases(programmingExercise, Visibility.ALWAYS);
    }

    @Test
    void shouldGenerateNewTestCasesWithVisibilityAfterDueDate() {
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        programmingExercise.setExerciseGroup(exerciseGroup1);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        testGenerateNewTestCases(programmingExercise, Visibility.AFTER_DUE_DATE);
    }

    private void testGenerateNewTestCases(ProgrammingExercise programmingExercise, Visibility expectedVisibility) {
        // We do not want to use the test cases generated in the setup
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(programmingExercise.getId()));

        var result = generateResult(List.of("test1", "test2"), Collections.emptyList());
        feedbackCreationService.generateTestCasesFromBuildResult(result, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(2);

        for (ProgrammingExerciseTestCase testCase : testCases) {
            assertThat(testCase.getVisibility()).isEqualTo(expectedVisibility);
        }
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

        for (ProgrammingLanguage language : ProgrammingLanguage.getEnabledLanguages()) {
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

    @Test
    void staticCodeAnalysisReportNotTruncatedFurther() {
        final StaticCodeAnalysisReportDTO scaReport = createStaticCodeAnalysisReportDTO();

        final List<Feedback> scaFeedbacks = feedbackCreationService.createFeedbackFromStaticCodeAnalysisReports(List.of(scaReport));
        assertThat(scaFeedbacks).hasSize(1);

        final Feedback scaFeedback = scaFeedbacks.getFirst();
        assertThat(scaFeedback.getHasLongFeedbackText()).isFalse();
        assertThat(scaFeedback.getLongFeedback()).isEmpty();
        assertThat(scaFeedback.getDetailText()).hasSizeGreaterThan(Constants.FEEDBACK_DETAIL_TEXT_SOFT_MAX_LENGTH)
                .hasSizeLessThanOrEqualTo(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH);
    }

    @NotNull
    private static StaticCodeAnalysisReportDTO createStaticCodeAnalysisReportDTO() {
        final String longText = "0".repeat(Constants.FEEDBACK_DETAIL_TEXT_SOFT_MAX_LENGTH * 2);
        final StaticCodeAnalysisIssue scaIssue = new StaticCodeAnalysisIssue("some/long/file/Path.java", null, null, 0, 123, "RuleName", "scaCategory", longText, null, null);
        return new StaticCodeAnalysisReportDTO(StaticCodeAnalysisTool.CHECKSTYLE, List.of(scaIssue));
    }
}
