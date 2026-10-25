package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.util.WebsocketDestinationMatchers.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.assessment.domain.ScaFeedback;
import de.tum.cit.aet.artemis.assessment.domain.TestCaseFeedback;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCIJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCaseType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisTool;
import de.tum.cit.aet.artemis.programming.dto.BuildResultNotification;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTestCaseResponseDTO;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisReportDTO;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTaskTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;

class ProgrammingExerciseFeedbackCreationServiceTest extends AbstractProgrammingIntegrationIndependentTest {

    private ProgrammingExercise programmingExercise;

    @Autowired
    private ProgrammingExerciseTaskTestRepository taskRepository;

    @BeforeEach
    void init() {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = (ProgrammingExercise) course.getExercises().iterator().next();
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setProjectType(ProjectType.PLAIN_MAVEN);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);
    }

    private String createFeedbackFromTestCase(String testName, List<String> testMessages, boolean successful) {
        // pass all test cases (not only active ones) so that the message-processing pipeline is exercised
        // regardless of the fixture's active flags
        var testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        return feedbackCreationService.createFeedbackFromTestCase(testName, testMessages, successful, programmingExercise, testCases).map(TestCaseFeedback::getMessageText)
                .orElse(null);
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
    void createFeedbackFromTestCaseRemovesExceptionNamesOfAres1AndAres2() {
        String msgAres = """
                de.tum.in.test.api.util.UnexpectedExceptionError: Unexpected exception in Ares 1
                de.tum.cit.ase.ares.api.util.UnexpectedExceptionError: Unexpected exception in Ares 2""";
        String actualFeedback = createFeedbackFromTestCase("test1", List.of(msgAres), false);
        assertThat(actualFeedback).isEqualTo("""
                Unexpected exception in Ares 1
                Unexpected exception in Ares 2""");
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

    private BuildResultNotification generateResult(List<String> successfulTests, List<String> failedTests) {
        return ProgrammingExerciseFactory.generateTestResultDTO(null, "SOLUTION", null, programmingExercise.getProgrammingLanguage(), false, successfulTests, failedTests, null,
                null, null);
    }

    /**
     * The grading table subscribes to {@code /topic/programming-exercises/{id}/test-cases} and replaces its rows with
     * whatever arrives there, so the push has to carry the same projection as the REST read that fills the table
     * ({@link ProgrammingExerciseTestCaseResponseDTO}) and not the {@link ProgrammingExerciseTestCase} entity: the
     * entity would drag its exercise and task sub-graphs onto the wire and hand the table a different row shape than
     * the one it loaded.
     */
    @Test
    void shouldBroadcastChangedTestCasesAsResponseDTOs() {
        // start from no test cases so the build result changes them and the push happens
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(programmingExercise.getId()));
        var buildResult = generateResult(List.of("test1"), List.of("test2"));

        feedbackCreationService.extractTestCasesFromResultAndBroadcastUpdates(buildResult, programmingExercise);

        var payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(websocketMessagingService).sendMessage(topic("/topic/programming-exercises/" + programmingExercise.getId() + "/test-cases"), payloadCaptor.capture());

        Map<String, Long> testCaseIds = testCaseRepository.findByExerciseId(programmingExercise.getId()).stream()
                .collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, ProgrammingExerciseTestCase::getId));
        // spelled out rather than mapped through ProgrammingExerciseTestCaseResponseDTO#of: this pins the field set the
        // grading table reads, including the 1.0 / 0.0 bonus defaults the entity getters substitute
        var expected = List.of(
                new ProgrammingExerciseTestCaseResponseDTO(testCaseIds.get("test1"), "test1", 1.0, 1.0, 0.0, true, Visibility.ALWAYS, ProgrammingExerciseTestCaseType.BEHAVIORAL),
                new ProgrammingExerciseTestCaseResponseDTO(testCaseIds.get("test2"), "test2", 1.0, 1.0, 0.0, true, Visibility.ALWAYS, ProgrammingExerciseTestCaseType.BEHAVIORAL));
        assertThat(payloadCaptor.getValue()).asInstanceOf(InstanceOfAssertFactories.iterable(Object.class)).hasOnlyElementsOfType(ProgrammingExerciseTestCaseResponseDTO.class)
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void shouldSetAllTestCasesToInactiveIfFeedbackListIsEmpty() {
        var result = generateResult(List.of(), List.of());
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

    @ParameterizedTest
    @CsvSource({ "false,JAVA", "true,JAVA", "false,C", "true,C" })
    void shouldClassifyDisappearingLegacyDefaultCases(boolean localCI, ProgrammingLanguage language) {
        programmingExercise.setProgrammingLanguage(language);
        programmingExerciseRepository.save(programmingExercise);
        var originalCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        var structuralCase = originalCases.stream().filter(testCase -> "test3".equals(testCase.getTestName())).findFirst().orElseThrow();
        structuralCase.setTestName("testMethods[Legacy]");
        testCaseRepository.save(structuralCase);
        assertThat(originalCases).allSatisfy(testCase -> assertThat(testCase.getType()).isEqualTo(ProgrammingExerciseTestCaseType.DEFAULT));
        Map<String, Long> originalIds = originalCases.stream().collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, ProgrammingExerciseTestCase::getId));

        BuildResultNotification buildResult = localCI
                ? new BuildResult("main", "solution", "tests", false, null, List.of(new LocalCIJobDTO(List.of(), List.of(new LocalCITestJobDTO("test2", List.of())))), List.of(),
                        List.of(), false, 1)
                : generateResult(List.of("test2"), List.of());
        feedbackCreationService.generateTestCasesFromBuildResult(buildResult, programmingExercise);

        var persistedCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(persistedCases).hasSize(3).allSatisfy(testCase -> {
            assertThat(testCase.getId()).isEqualTo(originalIds.get(testCase.getTestName()));
            assertThat(testCase.isActive()).isEqualTo("test2".equals(testCase.getTestName()));
            var expectedType = language != ProgrammingLanguage.JAVA ? ProgrammingExerciseTestCaseType.DEFAULT
                    : "testMethods[Legacy]".equals(testCase.getTestName()) ? ProgrammingExerciseTestCaseType.STRUCTURAL : ProgrammingExerciseTestCaseType.BEHAVIORAL;
            assertThat(testCase.getType()).isEqualTo(expectedType);
        });
        assertThat(persistedCases).filteredOn(testCase -> "testMethods[Legacy]".equals(testCase.getTestName())).singleElement().satisfies(testCase -> {
            assertThat(testCase.getWeight()).isEqualTo(3.0);
            assertThat(testCase.getVisibility()).isEqualTo(Visibility.AFTER_DUE_DATE);
            assertThat(testCase.getBonusMultiplier()).isEqualTo(1.0);
            assertThat(testCase.getBonusPoints()).isZero();
        });
    }

    @Test
    void shouldGenerateNewTestCases() {
        // We do not want to use the test cases generated in the setup
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(programmingExercise.getId()));

        var result = generateResult(List.of("test1", "test2"), List.of());
        feedbackCreationService.generateTestCasesFromBuildResult(result, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(2);

        assertThat(testCases.stream().allMatch(ProgrammingExerciseTestCase::isActive)).isTrue();
    }

    @Test
    void shouldNormalizeLongTestNamesForJenkinsBuildResults() {
        assertLongTestNamesRemainDistinctAndMatchFeedback(false);
    }

    @Test
    void shouldNormalizeLongTestNamesForLocalCIBuildResults() {
        assertLongTestNamesRemainDistinctAndMatchFeedback(true);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void shouldKeepLongStructuralTestNamesClassifiedAndMatched(boolean localCI) {
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(programmingExercise.getId()));
        String commonPrefix = "testMethods[" + "a".repeat(250);
        String successfulName = commonPrefix + "First]";
        String failedName = commonPrefix + "Second]";
        String shortStructuralName = "testClass[Example]";
        String shortBehavioralName = "testBehavior";
        BuildResultNotification buildResult = localCI
                ? new BuildResult("main", "solution", "tests", false, null,
                        List.of(new LocalCIJobDTO(List.of(new LocalCITestJobDTO(failedName, List.of("Assertion failed"))),
                                List.of(new LocalCITestJobDTO(successfulName, List.of()), new LocalCITestJobDTO(shortStructuralName, List.of()),
                                        new LocalCITestJobDTO(shortBehavioralName, List.of())))),
                        List.of(), List.of(), false, 1)
                : generateResult(List.of(successfulName, shortStructuralName, shortBehavioralName), List.of(failedName));

        feedbackCreationService.generateTestCasesFromBuildResult(buildResult, programmingExercise);
        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(4);
        var successfulFeedback = feedbackCreationService.createFeedbackFromTestCase(successfulName, List.of(), true, programmingExercise, testCases).orElseThrow();
        var failedFeedback = feedbackCreationService.createFeedbackFromTestCase(failedName, List.of("Assertion failed"), false, programmingExercise, testCases).orElseThrow();
        assertThat(successfulFeedback.getTestCase().getType()).isEqualTo(ProgrammingExerciseTestCaseType.STRUCTURAL);
        assertThat(failedFeedback.getTestCase().getType()).isEqualTo(ProgrammingExerciseTestCaseType.STRUCTURAL);
        assertThat(successfulFeedback.getTestCase().getId()).isNotEqualTo(failedFeedback.getTestCase().getId());
        assertThat(successfulFeedback.isPositive()).isTrue();
        assertThat(failedFeedback.isPositive()).isFalse();
        assertThat(failedFeedback.getMessageText()).isEqualTo("Assertion failed");
        assertThat(testCases).filteredOn(testCase -> shortStructuralName.equals(testCase.getTestName())).singleElement().extracting(ProgrammingExerciseTestCase::getType)
                .isEqualTo(ProgrammingExerciseTestCaseType.STRUCTURAL);
        assertThat(testCases).filteredOn(testCase -> shortBehavioralName.equals(testCase.getTestName())).singleElement().extracting(ProgrammingExerciseTestCase::getType)
                .isEqualTo(ProgrammingExerciseTestCaseType.BEHAVIORAL);

        Map<Long, ProgrammingExerciseTestCaseType> originalTypes = testCases.stream()
                .collect(Collectors.toMap(ProgrammingExerciseTestCase::getId, ProgrammingExerciseTestCase::getType));
        feedbackCreationService.generateTestCasesFromBuildResult(generateResult(List.of(), List.of()), programmingExercise);
        assertThat(testCaseRepository.findByExerciseId(programmingExercise.getId())).allSatisfy(testCase -> {
            assertThat(testCase.isActive()).isFalse();
            assertThat(testCase.getType()).isEqualTo(originalTypes.get(testCase.getId()));
        });
        feedbackCreationService.generateTestCasesFromBuildResult(buildResult, programmingExercise);
        assertThat(testCaseRepository.findByExerciseId(programmingExercise.getId())).allSatisfy(testCase -> {
            assertThat(testCase.isActive()).isTrue();
            assertThat(testCase.getType()).isEqualTo(originalTypes.get(testCase.getId()));
        });
    }

    @Test
    void shouldBindLongTestNameInProblemStatementToPersistedCase() {
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(programmingExercise.getId()));
        String originalName = "testMethods[" + "a".repeat(250) + "]";
        programmingExercise.setProblemStatement("[task][Structure](" + originalName + ")");
        programmingExerciseRepository.save(programmingExercise);

        feedbackCreationService.generateTestCasesFromBuildResult(generateResult(List.of(originalName), List.of()), programmingExercise);

        var persistedTestCase = testCaseRepository.findByExerciseId(programmingExercise.getId()).iterator().next();
        assertThat(taskRepository.findByExerciseIdWithTestCases(programmingExercise.getId())).singleElement().satisfies(task -> {
            assertThat(task.getTaskName()).isEqualTo("Structure");
            assertThat(task.getTestCases()).extracting(ProgrammingExerciseTestCase::getId).containsExactly(persistedTestCase.getId());
        });
        assertThat(programmingExerciseRepository.findById(programmingExercise.getId()).orElseThrow().getProblemStatement())
                .isEqualTo("[task][Structure](<testid>" + persistedTestCase.getId() + "</testid>)");
    }

    @ParameterizedTest
    @CsvSource({ "Type,type,true", "İ,i,true", "ß,SS,false" })
    void shouldPreserveLongTestIdentityForEquivalentCaseChanges(String originalArgument, String renamedArgument, boolean sameTest) {
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(programmingExercise.getId()));
        // Only the tail, beyond the stored prefix, changes. Case equivalence must match isSameTestCase even for Unicode.
        String commonPrefix = "parameterized(" + "x".repeat(260);
        String originalName = commonPrefix + originalArgument + ")";
        String renamedName = commonPrefix + renamedArgument + ")";
        feedbackCreationService.generateTestCasesFromBuildResult(generateResult(List.of(originalName), List.of()), programmingExercise);
        var originalTestCase = testCaseRepository.findByExerciseId(programmingExercise.getId()).iterator().next();
        originalTestCase.setWeight(3.25);
        originalTestCase.setBonusMultiplier(1.5);
        originalTestCase.setBonusPoints(0.25);
        originalTestCase.setVisibility(Visibility.AFTER_DUE_DATE);
        testCaseRepository.save(originalTestCase);

        feedbackCreationService.generateTestCasesFromBuildResult(generateResult(List.of(renamedName), List.of()), programmingExercise);

        var testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(sameTest ? 1 : 2);
        var renamedFeedback = feedbackCreationService.createFeedbackFromTestCase(renamedName, List.of(), true, programmingExercise, testCases).orElseThrow();
        var activeTestCase = renamedFeedback.getTestCase();
        assertThat(activeTestCase.isActive()).isTrue();
        if (sameTest) {
            assertThat(activeTestCase.getId()).isEqualTo(originalTestCase.getId());
            assertThat(activeTestCase.getWeight()).isEqualTo(3.25);
            assertThat(activeTestCase.getBonusMultiplier()).isEqualTo(1.5);
            assertThat(activeTestCase.getBonusPoints()).isEqualTo(0.25);
            assertThat(activeTestCase.getVisibility()).isEqualTo(Visibility.AFTER_DUE_DATE);
        }
        else {
            assertThat(activeTestCase.getId()).isNotEqualTo(originalTestCase.getId());
            assertThat(activeTestCase.getWeight()).isEqualTo(1.0);
            assertThat(testCases).filteredOn(testCase -> testCase.getId().equals(originalTestCase.getId())).singleElement()
                    .satisfies(testCase -> assertThat(testCase.isActive()).isFalse());
        }
    }

    private void assertLongTestNamesRemainDistinctAndMatchFeedback(boolean localCI) {
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(programmingExercise.getId()));
        // Put a supplementary character at the shortening boundary, and distinguish the names only after 255 characters.
        String commonPrefix = "parameterized(" + "x".repeat(175) + "😀" + "x".repeat(100);
        String successfulName = commonPrefix + "first argument)";
        String failedName = commonPrefix + "second argument)";
        BuildResultNotification buildResult = localCI
                ? new BuildResult("main", "solution", "tests", false, null,
                        List.of(new LocalCIJobDTO(List.of(new LocalCITestJobDTO(failedName, List.of("Assertion failed"))),
                                List.of(new LocalCITestJobDTO(successfulName, List.of())))),
                        List.of(), List.of(), false, 1)
                : generateResult(List.of(successfulName), List.of(failedName));

        feedbackCreationService.generateTestCasesFromBuildResult(buildResult, programmingExercise);

        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).hasSize(2).allSatisfy(testCase -> {
            assertThat(testCase.getTestName()).startsWith("parameterized(").hasSizeLessThanOrEqualTo(255);
            assertThat(testCase.getTestName().codePoints().noneMatch(codePoint -> codePoint >= 0xD800 && codePoint <= 0xDFFF)).isTrue();
        });
        assertThat(testCases).extracting(ProgrammingExerciseTestCase::getTestName).doesNotHaveDuplicates();
        var successfulFeedback = feedbackCreationService.createFeedbackFromTestCase(successfulName, List.of(), true, programmingExercise, testCases).orElseThrow();
        var failedFeedback = feedbackCreationService.createFeedbackFromTestCase(failedName, List.of("Assertion failed"), false, programmingExercise, testCases).orElseThrow();
        assertThat(successfulFeedback.getTestCase().getId()).isNotEqualTo(failedFeedback.getTestCase().getId());
        assertThat(successfulFeedback.isPositive()).isTrue();
        assertThat(failedFeedback.isPositive()).isFalse();
        assertThat(failedFeedback.getMessageText()).isEqualTo("Assertion failed");

        Map<String, Long> originalIds = testCases.stream().collect(Collectors.toMap(ProgrammingExerciseTestCase::getTestName, ProgrammingExerciseTestCase::getId));
        assertThat(feedbackCreationService.generateTestCasesFromBuildResult(buildResult, programmingExercise)).isFalse();
        assertThat(testCaseRepository.findByExerciseId(programmingExercise.getId())).extracting(ProgrammingExerciseTestCase::getId)
                .containsExactlyInAnyOrderElementsOf(originalIds.values());
    }

    @Test
    void shouldPreserveTestNamesWithinDatabaseLimit() {
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(programmingExercise.getId()));
        String maximumLengthName = "x".repeat(255);
        feedbackCreationService.generateTestCasesFromBuildResult(generateResult(List.of("shortTest", maximumLengthName), List.of()), programmingExercise);
        assertThat(testCaseRepository.findByExerciseId(programmingExercise.getId())).extracting(ProgrammingExerciseTestCase::getTestName).containsExactlyInAnyOrder("shortTest",
                maximumLengthName);
    }

    @Test
    void shouldPreserveSupplementaryCharactersWithinCharacterLimit() {
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(programmingExercise.getId()));
        String maximumLengthName = "😀".repeat(127) + "x";
        feedbackCreationService.generateTestCasesFromBuildResult(generateResult(List.of(maximumLengthName), List.of()), programmingExercise);
        assertThat(testCaseRepository.findByExerciseId(programmingExercise.getId())).extracting(ProgrammingExerciseTestCase::getTestName).containsExactly(maximumLengthName);
    }

    @Test
    void shouldGenerateNewTestCasesWithVisibilityAlways() {
        programmingExercise.setExerciseGroup(null);

        testGenerateNewTestCases(programmingExercise, Visibility.ALWAYS);
    }

    @Test
    void shouldGenerateNewTestCasesWithVisibilityAfterDueDate() {
        ExerciseGroup exerciseGroup1 = examUtilService.addExerciseGroupWithExamAndCourse(true);
        // Moving the exercise into an exam means giving up its own course: an exercise belongs to one or the other.
        programmingExercise.setCourse(null);
        programmingExercise.setExerciseGroup(exerciseGroup1);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        testGenerateNewTestCases(programmingExercise, Visibility.AFTER_DUE_DATE);
    }

    private void testGenerateNewTestCases(ProgrammingExercise programmingExercise, Visibility expectedVisibility) {
        // We do not want to use the test cases generated in the setup
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(programmingExercise.getId()));

        var result = generateResult(List.of("test1", "test2"), List.of());
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

        final List<ScaFeedback> scaFeedbacks = feedbackCreationService.createFeedbackFromStaticCodeAnalysisReports(List.of(scaReport));
        assertThat(scaFeedbacks).hasSize(1);

        final ScaFeedback scaFeedback = scaFeedbacks.getFirst();
        assertThat(scaFeedback.getMessageText()).hasSizeGreaterThan(Constants.FEEDBACK_DETAIL_TEXT_SOFT_MAX_LENGTH)
                .hasSizeLessThanOrEqualTo(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH);
    }

    @NonNull
    private static StaticCodeAnalysisReportDTO createStaticCodeAnalysisReportDTO() {
        final String longText = "0".repeat(Constants.FEEDBACK_DETAIL_TEXT_SOFT_MAX_LENGTH * 2);
        final StaticCodeAnalysisIssue scaIssue = new StaticCodeAnalysisIssue("some/long/file/Path.java", null, null, 0, 123, "RuleName", "scaCategory", longText, null, null);
        return new StaticCodeAnalysisReportDTO(StaticCodeAnalysisTool.CHECKSTYLE, List.of(scaIssue));
    }
}
