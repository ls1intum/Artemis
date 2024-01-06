package de.tum.in.www1.artemis.exercise.programmingexercise;

import static de.tum.in.www1.artemis.exercise.ExerciseFactory.populateExerciseForExam;
import static java.time.ZonedDateTime.now;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.exercise.ExerciseFactory;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildLogDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildPlanDTO;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.connectors.ci.notification.dto.*;
import de.tum.in.www1.artemis.service.dto.StaticCodeAnalysisReportDTO;
import de.tum.in.www1.artemis.util.TestConstants;

/**
 * Factory for creating ProgrammingExercises and related objects.
 */
public class ProgrammingExerciseFactory {

    public static final String DEFAULT_BRANCH = "main";

    /**
     * Generates a programming exercise with the given release and due date. This exercise is added to the provided course.
     *
     * @param releaseDate The release date of the exercise.
     * @param dueDate     The due date of the exercise.
     * @param course      The course of the exercise.
     * @return The newly generated programming exercise.
     */
    public static ProgrammingExercise generateProgrammingExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, Course course) {
        return generateProgrammingExercise(releaseDate, dueDate, course, ProgrammingLanguage.JAVA);
    }

    /**
     * Generates a programming exercise with the given release, due date, and programming language. This exercise is added to the provided course.
     *
     * @param releaseDate         The release date of the exercise.
     * @param dueDate             The due date of the exercise.
     * @param course              The course of the exercise.
     * @param programmingLanguage The programming language of the exercise.
     * @return The newly generated programming exercise.
     */
    public static ProgrammingExercise generateProgrammingExercise(ZonedDateTime releaseDate, ZonedDateTime dueDate, Course course, ProgrammingLanguage programmingLanguage) {
        var programmingExercise = (ProgrammingExercise) ExerciseFactory.populateExercise(new ProgrammingExercise(), releaseDate, dueDate, null, course);
        populateUnreleasedProgrammingExercise(programmingExercise, programmingLanguage);
        return programmingExercise;
    }

    /**
     * Generates a programming exercise for an exam and adds it to the provided exercise group.
     *
     * @param exerciseGroup The exercise group of an exam the exercise should be added to.
     * @return The newly generated programming exercise.
     */
    public static ProgrammingExercise generateProgrammingExerciseForExam(ExerciseGroup exerciseGroup) {
        return generateProgrammingExerciseForExam(exerciseGroup, ProgrammingLanguage.JAVA);
    }

    /**
     * Generates a programming exercise with the given title for an exam and adds it to the provided exercise group.
     *
     * @param exerciseGroup The exercise group of an exam the exercise should be added to.
     * @param title         The title of the exercise.
     * @return The newly generated programming exercise.
     */
    public static ProgrammingExercise generateProgrammingExerciseForExam(ExerciseGroup exerciseGroup, String title) {
        var programmingExercise = (ProgrammingExercise) populateExerciseForExam(new ProgrammingExercise(), exerciseGroup, title);
        populateUnreleasedProgrammingExercise(programmingExercise, ProgrammingLanguage.JAVA);
        return programmingExercise;
    }

    /**
     * Generates a programming exercise with the given programming language for an exam and adds it to the provided exercise group.
     *
     * @param exerciseGroup       The exercise group of an exam the exercise should be added to.
     * @param programmingLanguage The programming language of the exercise.
     * @return The newly generated programming exercise.
     */
    public static ProgrammingExercise generateProgrammingExerciseForExam(ExerciseGroup exerciseGroup, ProgrammingLanguage programmingLanguage) {
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        ExerciseFactory.populateExerciseForExam(programmingExercise, exerciseGroup);
        populateUnreleasedProgrammingExercise(programmingExercise, programmingLanguage);
        exerciseGroup.addExercise(programmingExercise);
        return programmingExercise;
    }

    /**
     * Populates the provided programming exercise with the given programming language and default values. The assessment type is set to semi-automatic.
     *
     * @param programmingExercise The exercise which should be populated.
     * @param programmingLanguage The programming language which should be used in the exercise.
     */
    private static void populateUnreleasedProgrammingExercise(ProgrammingExercise programmingExercise, ProgrammingLanguage programmingLanguage) {
        programmingExercise.generateAndSetProjectKey();
        programmingExercise.setAllowOfflineIde(true);
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        programmingExercise.setTestwiseCoverageEnabled(false);
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExercise.setProgrammingLanguage(programmingLanguage);
        if (programmingLanguage == ProgrammingLanguage.JAVA) {
            programmingExercise.setProjectType(ProjectType.PLAIN_MAVEN);
        }
        else if (programmingLanguage == ProgrammingLanguage.SWIFT) {
            programmingExercise.setProjectType(ProjectType.PLAIN);
        }
        else {
            programmingExercise.setProjectType(null);
        }
        programmingExercise.setPackageName(programmingLanguage == ProgrammingLanguage.SWIFT ? "swiftTest" : "de.test");
        final var repoName = programmingExercise.generateRepositoryName(RepositoryType.TESTS);
        String testRepoUri = String.format("http://some.test.url/scm/%s/%s.git", programmingExercise.getProjectKey(), repoName);
        programmingExercise.setTestRepositoryUri(testRepoUri);
        programmingExercise.setBranch(DEFAULT_BRANCH);
    }

    /**
     * Generates a programming exercise which can be imported. The exercise is generated using the provided title, short name, template, target course.
     *
     * @param title        The title of the exercise.
     * @param shortName    The short name of the exercise.
     * @param template     The template programming exercise. It is used to set the id, package name, dates, and other attributes of the exercise.
     * @param targetCourse The course into which the new exercise should be imported.
     * @return The newly generated programming exercise.
     */
    public static ProgrammingExercise generateToBeImportedProgrammingExercise(String title, String shortName, ProgrammingExercise template, Course targetCourse) {
        ProgrammingExercise toBeImported = new ProgrammingExercise();
        toBeImported.setCourse(targetCourse);
        toBeImported.setTitle(title);
        toBeImported.setShortName(shortName);
        toBeImported.setId(template.getId());
        toBeImported.setTestCases(null);
        toBeImported.setStaticCodeAnalysisCategories(null);
        toBeImported.setTotalNumberOfAssessments(template.getTotalNumberOfAssessments());
        toBeImported.setNumberOfComplaints(template.getNumberOfComplaints());
        toBeImported.setNumberOfMoreFeedbackRequests(template.getNumberOfMoreFeedbackRequests());
        toBeImported.setExerciseHints(null);
        toBeImported.setSolutionParticipation(null);
        toBeImported.setTemplateParticipation(null);
        toBeImported.setPublishBuildPlanUrl(template.isPublishBuildPlanUrl());
        toBeImported.setSequentialTestRuns(template.hasSequentialTestRuns());
        toBeImported.setProblemStatement(template.getProblemStatement());
        toBeImported.setMaxPoints(template.getMaxPoints());
        toBeImported.setBonusPoints(template.getBonusPoints());
        toBeImported.setGradingInstructions(template.getGradingInstructions());
        toBeImported.setDifficulty(template.getDifficulty());
        toBeImported.setMode(template.getMode());
        toBeImported.setAssessmentType(template.getAssessmentType());
        toBeImported.setCategories(template.getCategories());
        toBeImported.setPackageName(template.getPackageName());
        toBeImported.setAllowOnlineEditor(template.isAllowOnlineEditor());
        toBeImported.setAllowOfflineIde(template.isAllowOfflineIde());
        toBeImported.setStaticCodeAnalysisEnabled(template.isStaticCodeAnalysisEnabled());
        toBeImported.setTestwiseCoverageEnabled(template.isTestwiseCoverageEnabled());
        toBeImported.setTutorParticipations(null);
        toBeImported.setPosts(null);
        toBeImported.setStudentParticipations(null);
        toBeImported.setNumberOfSubmissions(template.getNumberOfSubmissions());
        toBeImported.setExampleSubmissions(null);
        toBeImported.setTestRepositoryUri(template.getTestRepositoryUri());
        toBeImported.setProgrammingLanguage(template.getProgrammingLanguage());
        toBeImported.setProjectType(template.getProjectType());
        toBeImported.setAssessmentDueDate(template.getAssessmentDueDate());
        toBeImported.setAttachments(null);
        toBeImported.setDueDate(template.getDueDate());
        toBeImported.setReleaseDate(template.getReleaseDate());
        toBeImported.setExampleSolutionPublicationDate(null);
        toBeImported.setSequentialTestRuns(template.hasSequentialTestRuns());
        toBeImported.setBuildAndTestStudentSubmissionsAfterDueDate(template.getBuildAndTestStudentSubmissionsAfterDueDate());
        toBeImported.generateAndSetProjectKey();
        toBeImported.setPlagiarismDetectionConfig(template.getPlagiarismDetectionConfig());

        return toBeImported;
    }

    /**
     * Creates a dummy DTO used by Jenkins, which notifies about new programming exercise results.
     *
     * @param fullName                    The full name of the build (includes Folder, Job and Build number).
     * @param repoName                    The name of the repository.
     * @param buildRunDate                The date of the build run, can be null.
     * @param programmingLanguage         The programming language to use.
     * @param enableStaticAnalysisReports True, if the notification should include static analysis reports.
     * @param successfulTestNames         The names of successful tests.
     * @param failedTestNames             The names of failed tests.
     * @param logs                        The logs produced by the test result.
     * @param commits                     The involved commits, can be null or empty.
     * @param testSuiteDto                The test suite.
     * @return TestResultDTO with dummy data.
     */
    public static TestResultsDTO generateTestResultDTO(String fullName, String repoName, ZonedDateTime buildRunDate, ProgrammingLanguage programmingLanguage,
            boolean enableStaticAnalysisReports, List<String> successfulTestNames, List<String> failedTestNames, List<String> logs, List<CommitDTO> commits,
            TestSuiteDTO testSuiteDto) {

        final var testSuite = new TestSuiteDTO("TestSuiteName1", now().toEpochSecond(), 0, 0, failedTestNames.size(), successfulTestNames.size() + failedTestNames.size(),
                new ArrayList<>());
        testSuite.testCases().addAll(successfulTestNames.stream().map(name -> new TestCaseDTO(name, "Class", 0d)).toList());
        testSuite.testCases().addAll(failedTestNames.stream()
                .map(name -> new TestCaseDTO(name, "Class", 0d, new ArrayList<>(), List.of(new TestCaseDetailMessageDTO(name + " error message")), new ArrayList<>())).toList());

        final var commitDTO = new CommitDTO(TestConstants.COMMIT_HASH_STRING, repoName, DEFAULT_BRANCH);
        final var staticCodeAnalysisReports = enableStaticAnalysisReports ? generateStaticCodeAnalysisReports(programmingLanguage) : new ArrayList<StaticCodeAnalysisReportDTO>();

        return new TestResultsDTO(successfulTestNames.size(), 0, 0, failedTestNames.size(), fullName, commits != null && commits.size() > 0 ? commits : List.of(commitDTO),
                List.of(testSuiteDto != null ? testSuiteDto : testSuite), staticCodeAnalysisReports, List.of(), buildRunDate != null ? buildRunDate : now(), false, logs);
    }

    /**
     * Creates a dummy DTO with custom feedbacks used by Jenkins, which notifies about new programming exercise results.
     * Uses {@link #generateTestResultDTO(String, String, ZonedDateTime, ProgrammingLanguage, boolean, List, List, List, List, TestSuiteDTO)} as basis.
     * Then adds a new {@link TestSuiteDTO} with name "CustomFeedbacks" to it.
     * This Testsuite has 4 {@link TestCaseDTO TestCaseDTOs}:
     * <ul>
     * <li>CustomSuccessMessage: successful test with a message</li>
     * <li>CustomSuccessNoMessage: successful test without message</li>
     * <li>CustomFailedMessage: failed test with a message</li>
     * </ul>
     *
     * @param repoName                    The Name of the repository.
     * @param successfulTestNames         The names of successful tests.
     * @param failedTestNames             The names of failed tests.
     * @param programmingLanguage         The Programming language to use.
     * @param enableStaticAnalysisReports True, if the notification should include static analysis reports.
     * @return TestResultDTO with dummy data.
     */
    public static TestResultsDTO generateTestResultsDTOWithCustomFeedback(String repoName, List<String> successfulTestNames, List<String> failedTestNames,
            ProgrammingLanguage programmingLanguage, boolean enableStaticAnalysisReports) {

        final List<TestCaseDTO> testCases = new ArrayList<>();

        // successful with message
        {
            var testCase = new TestCaseDTO("CustomSuccessMessage", null, 0d);
            testCase.successInfos().add(new TestCaseDetailMessageDTO("Successful test with message"));
            testCases.add(testCase);
        }

        // successful without message
        {
            var testCase = new TestCaseDTO("CustomSuccessNoMessage", null, 0d);
            testCase.successInfos().add(new TestCaseDetailMessageDTO(null));
            testCases.add(testCase);
        }

        // failed with message
        {
            var testCase = new TestCaseDTO("CustomFailedMessage", null, 0d);
            testCase.failures().add(new TestCaseDetailMessageDTO("Failed test with message"));
            testCases.add(testCase);
        }

        // failed without message
        {
            var testCase = new TestCaseDTO("CustomFailedNoMessage", null, 0d);
            testCase.failures().add(new TestCaseDetailMessageDTO(null));
            testCases.add(testCase);
        }
        var testSuite = new TestSuiteDTO("customFeedbacks", 0d, 0, 0, failedTestNames.size(), successfulTestNames.size() + failedTestNames.size(), testCases);
        return generateTestResultDTO(null, repoName, null, programmingLanguage, enableStaticAnalysisReports, successfulTestNames, failedTestNames, new ArrayList<>(),
                new ArrayList<>(), testSuite);
    }

    /**
     * Generates a Bamboo build result notification DTO using the provided values. The successful boolean value is se to true if there are no failed test names.
     * It first creates a BambooTestSummaryDTO, BambooJobDTO, BambooVCSDTO and BambooBuildDTO which are used to create the BambooBuildResultNotificationDTO.
     *
     * @param repoName               The repository name.
     * @param planKey                The key of the build plan.
     * @param testSummaryDescription The test summary description used to create a bamboo test summary DTO.
     * @param buildCompletionDate    The completion date of the build.
     * @param successfulTestNames    The names of successful tests.
     * @param failedTestNames        The names of failed tests.
     * @param vcsDtos                The vcs objects containing commit information.
     * @return The generated Bamboo build result notification DTO
     */
    public static BambooBuildResultNotificationDTO generateBambooBuildResult(String repoName, String planKey, String testSummaryDescription, ZonedDateTime buildCompletionDate,
            List<String> successfulTestNames, List<String> failedTestNames, List<BambooBuildResultNotificationDTO.BambooVCSDTO> vcsDtos) {
        return generateBambooBuildResult(repoName, planKey, testSummaryDescription, buildCompletionDate, successfulTestNames, failedTestNames, vcsDtos, failedTestNames.isEmpty());
    }

    /**
     * Generates a Bamboo build result notification DTO using the provided values.
     * It first creates a BambooTestSummaryDTO, BambooJobDTO, BambooVCSDTO and BambooBuildDTO which are used to create the BambooBuildResultNotificationDTO.
     *
     * @param repoName               The repository name.
     * @param planKey                The key of the build plan.
     * @param testSummaryDescription The test summary description used to create a bamboo test summary DTO.
     * @param buildCompletionDate    The completion date of the build.
     * @param successfulTestNames    The names of successful tests.
     * @param failedTestNames        The names of failed tests.
     * @param vcsDtos                The vcs objects containing commit information.
     * @param successful             True, id the build was successful. Used to create the Bamboo build dto.
     * @return The generated Bamboo build result notification DTO
     */
    public static BambooBuildResultNotificationDTO generateBambooBuildResult(String repoName, String planKey, String testSummaryDescription, ZonedDateTime buildCompletionDate,
            List<String> successfulTestNames, List<String> failedTestNames, List<BambooBuildResultNotificationDTO.BambooVCSDTO> vcsDtos, boolean successful) {

        final var summary = new BambooBuildResultNotificationDTO.BambooTestSummaryDTO(42, 0, failedTestNames.size(), failedTestNames.size(), 0, successfulTestNames.size(),
                testSummaryDescription, 0, 0, successfulTestNames.size() + failedTestNames.size(), failedTestNames.size());

        final var successfulTests = successfulTestNames.stream().map(name -> generateBambooTestJob(name, true)).toList();
        final var failedTests = failedTestNames.stream().map(name -> generateBambooTestJob(name, false)).toList();
        final var job = new BambooBuildResultNotificationDTO.BambooJobDTO(42, failedTests, successfulTests, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        final var vcs = new BambooBuildResultNotificationDTO.BambooVCSDTO(TestConstants.COMMIT_HASH_STRING, repoName, DEFAULT_BRANCH, new ArrayList<>());
        final var plan = new BambooBuildPlanDTO(planKey != null ? planKey : "TEST201904BPROGRAMMINGEXERCISE6-STUDENT1");

        final var build = new BambooBuildResultNotificationDTO.BambooBuildDTO(false, 42, "foobar", buildCompletionDate != null ? buildCompletionDate : now().minusSeconds(5),
                successful, summary, vcsDtos != null && vcsDtos.size() > 0 ? vcsDtos : List.of(vcs), List.of(job));

        return new BambooBuildResultNotificationDTO("secret", "TestNotification", plan, build);
    }

    /**
     * Generate a Bamboo result notification DTO with 10 build logs of various sizes. The successful boolean value is set to true if there are no failed test names.
     *
     * @param buildPlanKey        The key of the build plan
     * @param repoName            The repository name.
     * @param successfulTestNames The names of successful tests.
     * @param failedTestNames     The names of failed tests.
     * @param buildCompletionDate The completion date of the build.
     * @param vcsDtos             The vcs objects containing commit information.
     * @return The Bamboo result notification DTO with build logs.
     */
    public static BambooBuildResultNotificationDTO generateBambooBuildResultWithLogs(String buildPlanKey, String repoName, List<String> successfulTestNames,
            List<String> failedTestNames, ZonedDateTime buildCompletionDate, List<BambooBuildResultNotificationDTO.BambooVCSDTO> vcsDtos) {
        return generateBambooBuildResultWithLogs(buildPlanKey, repoName, successfulTestNames, failedTestNames, buildCompletionDate, vcsDtos, failedTestNames.isEmpty());
    }

    /**
     * Generate a Bamboo result notification DTO with 10 build logs of various sizes.
     *
     * @param buildPlanKey        The key of the build plan
     * @param repoName            The repository name.
     * @param successfulTestNames The names of successful tests.
     * @param failedTestNames     The names of failed tests.
     * @param buildCompletionDate The completion date of the build.
     * @param vcsDtos             The vcs objects containing commit information.
     * @return The Bamboo result notification DTO with build logs.
     */
    public static BambooBuildResultNotificationDTO generateBambooBuildResultWithLogs(String buildPlanKey, String repoName, List<String> successfulTestNames,
            List<String> failedTestNames, ZonedDateTime buildCompletionDate, List<BambooBuildResultNotificationDTO.BambooVCSDTO> vcsDtos, boolean successful) {
        var notification = generateBambooBuildResult(repoName, buildPlanKey, "No tests found", buildCompletionDate, successfulTestNames, failedTestNames, vcsDtos, successful);

        String logWith254Chars = "a".repeat(254);

        var buildLogDTO254Chars = new BambooBuildLogDTO(now(), logWith254Chars, null);
        var buildLogDTO255Chars = new BambooBuildLogDTO(now(), logWith254Chars + "a", null);
        var buildLogDTO256Chars = new BambooBuildLogDTO(now(), logWith254Chars + "aa", null);
        var largeBuildLogDTO = new BambooBuildLogDTO(now(), logWith254Chars + logWith254Chars, null);
        var logTypicalErrorLog = new BambooBuildLogDTO(now(), "error: the java class ABC does not exist", null);
        var logTypicalDuplicatedErrorLog = new BambooBuildLogDTO(now(), "error: the java class ABC does not exist", null);
        var logCompilationError = new BambooBuildLogDTO(now(), "COMPILATION ERROR", null);
        var logBuildError = new BambooBuildLogDTO(now(), "BUILD FAILURE", null);
        var logWarning = new BambooBuildLogDTO(now(), "[WARNING]", null);
        var logWarningIllegalReflectiveAccess = new BambooBuildLogDTO(now(), "WARNING: Illegal reflective access by", null);

        notification.getBuild().jobs().get(0).logs().addAll(List.of(buildLogDTO254Chars, buildLogDTO255Chars, buildLogDTO256Chars, largeBuildLogDTO, logTypicalErrorLog,
                logTypicalDuplicatedErrorLog, logWarning, logWarningIllegalReflectiveAccess, logCompilationError, logBuildError));

        return notification;
    }

    /**
     * Generate a Bamboo result notification DTO with 9 analytics logs.
     *
     * @param buildPlanKey        The key of the build plan
     * @param repoName            The repository name.
     * @param successfulTestNames The names of successful tests.
     * @param failedTestNames     The names of failed tests.
     * @param buildCompletionDate The completion date of the build.
     * @param vcsDtos             The vcs objects containing commit information.
     * @return The Bamboo result notification DTO with analytics logs.
     */
    public static BambooBuildResultNotificationDTO generateBambooBuildResultWithAnalyticsLogs(String buildPlanKey, String repoName, List<String> successfulTestNames,
            List<String> failedTestNames, ZonedDateTime buildCompletionDate, List<BambooBuildResultNotificationDTO.BambooVCSDTO> vcsDtos, boolean sca) {
        var notification = generateBambooBuildResult(repoName, buildPlanKey, "Test executed", buildCompletionDate, successfulTestNames, failedTestNames, vcsDtos, true);

        var jobStarted = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 14, 58, 30, 0, ZoneId.systemDefault()), "started building on agent 1", null);

        var executingBuild = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 15, 0, 0, 0, ZoneId.systemDefault()), "Executing build", null);

        var testingStarted = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 15, 0, 5, 0, ZoneId.systemDefault()), "Starting task 'Tests'", null);

        var dependency1Downloaded = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 15, 0, 10, 0, ZoneId.systemDefault()), "Dependency 1 Downloaded from", null);

        var testingFinished = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 15, 0, 15, 0, ZoneId.systemDefault()), "Finished task 'Tests' with result", null);

        var scaStarted = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 15, 0, 16, 0, ZoneId.systemDefault()), "Starting task 'Static Code Analysis'", null);

        var dependency2Downloaded = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 15, 0, 20, 0, ZoneId.systemDefault()), "Dependency 2 Downloaded from", null);

        var scaFinished = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 15, 0, 27, 0, ZoneId.systemDefault()), "Finished task 'Static Code Analysis'", null);

        var jobFinished = new BambooBuildLogDTO(ZonedDateTime.of(2021, 5, 10, 15, 0, 30, 0, ZoneId.systemDefault()), "Finished building", null);

        notification.getBuild().jobs().get(0).logs().clear();
        if (sca) {
            notification.getBuild().jobs().get(0).logs().addAll(
                    List.of(jobStarted, executingBuild, testingStarted, dependency1Downloaded, testingFinished, scaStarted, dependency2Downloaded, scaFinished, jobFinished));
        }
        else {
            notification.getBuild().jobs().get(0).logs().addAll(List.of(jobStarted, executingBuild, testingStarted, dependency1Downloaded, testingFinished, jobFinished));
        }

        return notification;
    }

    /**
     * Creates a static code analysis feedback with inactive category.
     *
     * @param result The result of the feedback.
     * @return The newly created feedback.
     */
    public static Feedback createSCAFeedbackWithInactiveCategory(Result result) {
        return new Feedback().result(result).text(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER).reference("CHECKSTYLE").detailText("{\"category\": \"miscellaneous\"}")
                .type(FeedbackType.AUTOMATIC).positive(false);
    }

    /**
     * Generates Bamboo build result notification DTO with a static code analysis report.
     *
     * @param repoName            The repository name.
     * @param successfulTestNames The names of successful tests.
     * @param failedTestNames     The names of failed tests.
     * @param programmingLanguage The programming language of the static code analysis report.
     * @return The created Bamboo build result notification DTO.
     */
    public static BambooBuildResultNotificationDTO generateBambooBuildResultWithStaticCodeAnalysisReport(String repoName, List<String> successfulTestNames,
            List<String> failedTestNames, ProgrammingLanguage programmingLanguage) {
        var notification = generateBambooBuildResult(repoName, null, null, null, successfulTestNames, failedTestNames, new ArrayList<>(), true);
        var reports = generateStaticCodeAnalysisReports(programmingLanguage);
        notification.getBuild().jobs().get(0).staticCodeAnalysisReports().addAll(reports);
        return notification;
    }

    /**
     * Generates a static code analysis report for each tool supported by the given programming language.
     *
     * @param language The programming language of the tools used to generate reports.
     * @return The list of generated static code analysis reports.
     */
    public static List<StaticCodeAnalysisReportDTO> generateStaticCodeAnalysisReports(ProgrammingLanguage language) {
        return StaticCodeAnalysisTool.getToolsForProgrammingLanguage(language).stream().map(ProgrammingExerciseFactory::generateStaticCodeAnalysisReport).toList();
    }

    /**
     * Generates a static code analysis report using the given static code analysis tool. A static code analysis issue is created and added to the report.
     *
     * @param tool The static code analysis tool added to the report and used to create the issue.
     * @return The generated static code analysis report DTO.
     */
    private static StaticCodeAnalysisReportDTO generateStaticCodeAnalysisReport(StaticCodeAnalysisTool tool) {
        var report = new StaticCodeAnalysisReportDTO();
        report.setTool(tool);
        report.setIssues(List.of(generateStaticCodeAnalysisIssue(tool)));
        return report;
    }

    /**
     * Generates a static code analysis issue based on the given static code analysis tool.
     *
     * @param tool The static code analysis tool used to set the category of the issue.
     * @return The created static code analysis issue.
     */
    private static StaticCodeAnalysisReportDTO.StaticCodeAnalysisIssue generateStaticCodeAnalysisIssue(StaticCodeAnalysisTool tool) {
        // Use a category which is not invisible in the default configuration
        String category = switch (tool) {
            case SPOTBUGS -> "BAD_PRACTICE";
            case PMD -> "Best Practices";
            case CHECKSTYLE -> "coding";
            case PMD_CPD -> "Copy/Paste Detection";
            case SWIFTLINT -> "swiftLint"; // TODO: rene: set better value after categories are better defined
            case GCC -> "Memory";
        };

        var issue = new StaticCodeAnalysisReportDTO.StaticCodeAnalysisIssue();
        issue.setFilePath(Constants.STUDENT_WORKING_DIRECTORY + "/www/packagename/Class1.java");
        issue.setStartLine(1);
        issue.setEndLine(2);
        issue.setStartColumn(1);
        issue.setEndColumn(10);
        issue.setRule("Rule");
        issue.setCategory(category);
        issue.setMessage("Message");
        issue.setPriority("Priority");

        return issue;
    }

    /**
     * Generates a static code analysis category for the given programming exercise.
     *
     * @param programmingExercise The programming exercise of the category.
     * @param name                The name of the category.
     * @param state               The state of the category.
     * @param penalty             The penalty of the category.
     * @param maxPenalty          The maximal penalty of the category.
     * @return The newly created static code analysis category.
     */
    public static StaticCodeAnalysisCategory generateStaticCodeAnalysisCategory(ProgrammingExercise programmingExercise, String name, CategoryState state, Double penalty,
            Double maxPenalty) {
        var category = new StaticCodeAnalysisCategory();
        category.setName(name);
        category.setPenalty(penalty);
        category.setMaxPenalty(maxPenalty);
        category.setState(state);
        category.setProgrammingExercise(programmingExercise);
        return category;
    }

    /**
     * Generates a Bamboo test job DTO with the given name, <code>SpringTestClass</code> class name and errors based on the successful value.
     *
     * @param name       The name and method name of the DTO.
     * @param successful If true, an empty list of errors is added to the DTO. Otherwise, the error <code>bad solution, did not work</code> is added.
     * @return The created Bamboo test job DTO.
     */
    private static BambooBuildResultNotificationDTO.BambooTestJobDTO generateBambooTestJob(String name, boolean successful) {
        return new BambooBuildResultNotificationDTO.BambooTestJobDTO(name, name, "SpringTestClass", successful ? List.of() : List.of("bad solution, did not work"));
    }

    /**
     * Populates the provided programming exercise with the given short name, title, and other values. The release date of the exercise is set in the future.
     * The programming language is set to java and test wise coverage analysis is disabled.
     *
     * @param programmingExercise      The exercise to be populated.
     * @param shortName                The short name of the exercise.
     * @param title                    The title of the exercise.
     * @param enableStaticCodeAnalysis True, if the static code analysis should be enabled for the exercise.
     */
    public static void populateUnreleasedProgrammingExercise(ProgrammingExercise programmingExercise, String shortName, String title, boolean enableStaticCodeAnalysis) {
        populateUnreleasedProgrammingExercise(programmingExercise, shortName, title, enableStaticCodeAnalysis, false, ProgrammingLanguage.JAVA);
    }

    /**
     * Populates the provided programming exercise with the given short name, title, and other values. The release date of the exercise is set in the future.
     *
     * @param programmingExercise            The exercise to be populated.
     * @param shortName                      The short name of the exercise.
     * @param title                          The title of the exercise.
     * @param enableStaticCodeAnalysis       True, if the static code analysis should be enabled for the exercise.
     * @param enableTestwiseCoverageAnalysis True, if test wise coverage analysis should be enabled for the exercise.
     * @param programmingLanguage            The programming language used in the exercise.
     */
    public static void populateUnreleasedProgrammingExercise(ProgrammingExercise programmingExercise, String shortName, String title, boolean enableStaticCodeAnalysis,
            boolean enableTestwiseCoverageAnalysis, ProgrammingLanguage programmingLanguage) {
        programmingExercise.setProgrammingLanguage(programmingLanguage);
        programmingExercise.setShortName(shortName);
        programmingExercise.generateAndSetProjectKey();
        programmingExercise.setReleaseDate(ZonedDateTime.now().plusDays(1));
        programmingExercise.setDueDate(ZonedDateTime.now().plusDays(2));
        programmingExercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(3));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(5));
        programmingExercise.setBonusPoints(0D);
        programmingExercise.setPublishBuildPlanUrl(false);
        programmingExercise.setMaxPoints(42.0);
        programmingExercise.setDifficulty(DifficultyLevel.EASY);
        programmingExercise.setMode(ExerciseMode.INDIVIDUAL);
        programmingExercise.setProblemStatement("Lorem Ipsum");
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExercise.setGradingInstructions("Lorem Ipsum");
        programmingExercise.setTitle(title);
        if (programmingLanguage == ProgrammingLanguage.JAVA) {
            programmingExercise.setProjectType(ProjectType.PLAIN_MAVEN);
        }
        else if (programmingLanguage == ProgrammingLanguage.SWIFT) {
            programmingExercise.setProjectType(ProjectType.PLAIN);
        }
        else if (programmingLanguage == ProgrammingLanguage.C) {
            programmingExercise.setProjectType(ProjectType.GCC);
        }
        else {
            programmingExercise.setProjectType(null);
        }
        programmingExercise.setAllowOnlineEditor(true);
        programmingExercise.setStaticCodeAnalysisEnabled(enableStaticCodeAnalysis);
        if (enableStaticCodeAnalysis) {
            programmingExercise.setMaxStaticCodeAnalysisPenalty(40);
        }
        programmingExercise.setTestwiseCoverageEnabled(enableTestwiseCoverageAnalysis);
        // Note: no separators are allowed for Swift package names
        if (programmingLanguage == ProgrammingLanguage.SWIFT) {
            programmingExercise.setPackageName("swiftTest");
        }
        else {
            programmingExercise.setPackageName("de.test");
        }
        programmingExercise.setCategories(new HashSet<>(Set.of("cat1", "cat2")));
        programmingExercise.setTestRepositoryUri("http://nadnasidni.tum/scm/" + programmingExercise.getProjectKey() + "/" + programmingExercise.getProjectKey() + "-tests.git");
        programmingExercise.setShowTestNamesToStudents(false);
        programmingExercise.setBranch(DEFAULT_BRANCH);
    }
}
