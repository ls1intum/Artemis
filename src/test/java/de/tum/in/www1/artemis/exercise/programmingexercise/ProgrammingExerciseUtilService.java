package de.tum.in.www1.artemis.exercise.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.hestia.*;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.hestia.*;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.TestConstants;

/**
 * Service responsible for initializing the database with specific testdata related to programming exercises for use in integration tests.
 */
@Service
public class ProgrammingExerciseUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    @Autowired
    private TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepo;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepo;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    private StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    @Autowired
    private BuildPlanRepository buildPlanRepository;

    @Autowired
    private AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Autowired
    private SubmissionPolicyRepository submissionPolicyRepository;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepo;

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private StudentParticipationRepository studentParticipationRepo;

    @Autowired
    private ExerciseHintRepository exerciseHintRepository;

    @Autowired
    private ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    @Autowired
    private ProgrammingExerciseSolutionEntryRepository solutionEntryRepository;

    @Autowired
    private CodeHintRepository codeHintRepository;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseTestRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    public ProgrammingExercise addTemplateParticipationForProgrammingExercise(ProgrammingExercise exercise) {
        final var repoName = exercise.generateRepositoryName(RepositoryType.TEMPLATE);
        TemplateProgrammingExerciseParticipation participation = new TemplateProgrammingExerciseParticipation();
        participation.setProgrammingExercise(exercise);
        participation.setBuildPlanId(exercise.generateBuildPlanId(BuildPlanType.TEMPLATE));
        participation.setRepositoryUrl(String.format("http://some.test.url/scm/%s/%s.git", exercise.getProjectKey(), repoName));
        participation.setInitializationState(InitializationState.INITIALIZED);
        templateProgrammingExerciseParticipationRepo.save(participation);
        exercise.setTemplateParticipation(participation);
        return programmingExerciseRepository.save(exercise);
    }

    public ProgrammingExercise addSolutionParticipationForProgrammingExercise(ProgrammingExercise exercise) {
        final var repoName = exercise.generateRepositoryName(RepositoryType.SOLUTION);
        SolutionProgrammingExerciseParticipation participation = new SolutionProgrammingExerciseParticipation();
        participation.setProgrammingExercise(exercise);
        participation.setBuildPlanId(exercise.generateBuildPlanId(BuildPlanType.SOLUTION));
        participation.setRepositoryUrl(String.format("http://some.test.url/scm/%s/%s.git", exercise.getProjectKey(), repoName));
        participation.setInitializationState(InitializationState.INITIALIZED);
        solutionProgrammingExerciseParticipationRepo.save(participation);
        exercise.setSolutionParticipation(participation);
        return programmingExerciseRepository.save(exercise);
    }

    public ProgrammingExercise addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases() {
        ProgrammingExercise programmingExercise = addCourseExamExerciseGroupWithOneProgrammingExercise();
        addTestCasesToProgrammingExercise(programmingExercise);
        return programmingExercise;
    }

    public ProgrammingExercise addCourseExamExerciseGroupWithOneProgrammingExercise(String title, String shortName) {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setExerciseGroup(exerciseGroup);
        ProgrammingExerciseFactory.populateProgrammingExercise(programmingExercise, shortName, title, false);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExercise = addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = addTemplateParticipationForProgrammingExercise(programmingExercise);

        return programmingExercise;
    }

    public ProgrammingExercise addCourseExamExerciseGroupWithOneProgrammingExercise() {
        return addCourseExamExerciseGroupWithOneProgrammingExercise("Testtitle", "TESTEXFOREXAM");
    }

    public ProgrammingExercise addProgrammingExerciseToExam(Exam exam, int exerciseGroupNumber) {
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setExerciseGroup(exam.getExerciseGroups().get(exerciseGroupNumber));
        ProgrammingExerciseFactory.populateProgrammingExercise(programmingExercise, "TESTEXFOREXAM", "Testtitle", false);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExercise = addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = addTemplateParticipationForProgrammingExercise(programmingExercise);

        exam.getExerciseGroups().get(exerciseGroupNumber).addExercise(programmingExercise);
        examRepository.save(exam);

        return programmingExercise;
    }

    public ProgrammingSubmission createProgrammingSubmission(Participation participation, boolean buildFailed, String commitHash) {
        ProgrammingSubmission programmingSubmission = ParticipationFactory.generateProgrammingSubmission(true);
        programmingSubmission.setBuildFailed(buildFailed);
        programmingSubmission.type(SubmissionType.MANUAL).submissionDate(ZonedDateTime.now());
        programmingSubmission.setCommitHash(commitHash);
        programmingSubmission.setParticipation(participation);
        return submissionRepository.save(programmingSubmission);
    }

    public ProgrammingSubmission createProgrammingSubmission(Participation participation, boolean buildFailed) {
        return createProgrammingSubmission(participation, buildFailed, TestConstants.COMMIT_HASH_STRING);
    }

    public Course addCourseWithOneProgrammingExercise() {
        return addCourseWithOneProgrammingExercise(false);
    }

    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis) {
        return addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, false, ProgrammingLanguage.JAVA);
    }

    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis, String title, String shortName) {
        return addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, false, ProgrammingLanguage.JAVA, title, shortName);
    }

    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis, boolean enableTestwiseCoverageAnalysis, ProgrammingLanguage programmingLanguage) {
        return addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, enableTestwiseCoverageAnalysis, programmingLanguage, "Programming", "TSTEXC");
    }

    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis, boolean enableTestwiseCoverageAnalysis, ProgrammingLanguage programmingLanguage,
            String title, String shortName) {
        var course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        var programmingExercise = addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, enableTestwiseCoverageAnalysis, programmingLanguage, title, shortName);
        assertThat(programmingExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();
        return courseRepo.findByIdWithExercisesAndLecturesElseThrow(course.getId());
    }

    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis) {
        return addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, false, ProgrammingLanguage.JAVA);
    }

    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis, boolean enableTestwiseCoverageAnalysis,
            ProgrammingLanguage programmingLanguage) {
        return addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, enableTestwiseCoverageAnalysis, programmingLanguage, "Programming", "TSTEXC");
    }

    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis, boolean enableTestwiseCoverageAnalysis,
            ProgrammingLanguage programmingLanguage, String title, String shortName) {
        var programmingExercise = (ProgrammingExercise) new ProgrammingExercise().course(course);
        ProgrammingExerciseFactory.populateProgrammingExercise(programmingExercise, shortName, title, enableStaticCodeAnalysis, enableTestwiseCoverageAnalysis,
                programmingLanguage);
        programmingExercise.setPresentationScoreEnabled(course.getPresentationScore() != 0);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        course.addExercises(programmingExercise);
        programmingExercise = addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = addTemplateParticipationForProgrammingExercise(programmingExercise);

        assertThat(programmingExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();

        return programmingExercise;
    }

    public Course addCourseWithNamedProgrammingExercise(String programmingExerciseTitle, boolean scaActive) {
        var course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);

        var programmingExercise = (ProgrammingExercise) new ProgrammingExercise().course(course);
        ProgrammingExerciseFactory.populateProgrammingExercise(programmingExercise, "TSTEXC", programmingExerciseTitle, scaActive);
        programmingExercise.setPresentationScoreEnabled(course.getPresentationScore() != 0);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        course.addExercises(programmingExercise);
        programmingExercise = addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = addTemplateParticipationForProgrammingExercise(programmingExercise);

        assertThat(programmingExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();

        return courseRepo.findByIdWithExercisesAndLecturesElseThrow(course.getId());
    }

    public Course addCourseWithOneProgrammingExerciseAndSpecificTestCases() {
        Course course = addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = exerciseUtilService.findProgrammingExerciseWithTitle(course.getExercises(), "Programming");

        List<ProgrammingExerciseTestCase> testCases = new ArrayList<>();
        testCases.add(new ProgrammingExerciseTestCase().testName("testClass[BubbleSort]").weight(1.0).active(true).exercise(programmingExercise).bonusMultiplier(1D).bonusPoints(0D)
                .visibility(Visibility.ALWAYS));
        testCases.add(new ProgrammingExerciseTestCase().testName("testMethods[Context]").weight(2.0).active(true).exercise(programmingExercise).bonusMultiplier(1D).bonusPoints(0D)
                .visibility(Visibility.ALWAYS));
        testCases.add(new ProgrammingExerciseTestCase().testName("testMethods[Policy]").weight(3.0).active(true).exercise(programmingExercise).bonusMultiplier(1D).bonusPoints(0D)
                .visibility(Visibility.ALWAYS));
        testCaseRepository.saveAll(testCases);

        List<ProgrammingExerciseTestCase> tests = new ArrayList<>(testCaseRepository.findByExerciseId(programmingExercise.getId()));
        assertThat(tests).as("test case is initialized").hasSize(3);

        return courseRepo.findByIdWithEagerExercisesElseThrow(course.getId());
    }

    public ProgrammingExercise addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories() {
        return addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories(ProgrammingLanguage.JAVA);
    }

    public ProgrammingExercise addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories(ProgrammingLanguage programmingLanguage) {
        Course course = addCourseWithOneProgrammingExercise(true, false, programmingLanguage);
        ProgrammingExercise programmingExercise = exerciseUtilService.findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        addStaticCodeAnalysisCategoriesToProgrammingExercise(programmingExercise);

        return programmingExercise;
    }

    public void addStaticCodeAnalysisCategoriesToProgrammingExercise(ProgrammingExercise programmingExercise) {
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExerciseRepository.save(programmingExercise);
        var category1 = ProgrammingExerciseFactory.generateStaticCodeAnalysisCategory(programmingExercise, "Bad Practice", CategoryState.GRADED, 3D, 10D);
        var category2 = ProgrammingExerciseFactory.generateStaticCodeAnalysisCategory(programmingExercise, "Code Style", CategoryState.GRADED, 5D, 10D);
        var category3 = ProgrammingExerciseFactory.generateStaticCodeAnalysisCategory(programmingExercise, "Miscellaneous", CategoryState.INACTIVE, 2D, 10D);
        var category4 = ProgrammingExerciseFactory.generateStaticCodeAnalysisCategory(programmingExercise, "Potential Bugs", CategoryState.FEEDBACK, 5D, 20D);
        var categories = staticCodeAnalysisCategoryRepository.saveAll(List.of(category1, category2, category3, category4));
        programmingExercise.setStaticCodeAnalysisCategories(new HashSet<>(categories));
    }

    public Course addCourseWithOneProgrammingExerciseAndTestCases() {
        Course course = addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = exerciseUtilService.findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
        addTestCasesToProgrammingExercise(programmingExercise);
        return courseRepo.findByIdWithExercisesAndLecturesElseThrow(course.getId());
    }

    public void addCourseWithNamedProgrammingExerciseAndTestCases(String programmingExerciseTitle) {
        addCourseWithNamedProgrammingExerciseAndTestCases(programmingExerciseTitle, false);
    }

    /**
     * @param programmingExerciseTitle The title of the programming exercise
     */
    public void addCourseWithNamedProgrammingExerciseAndTestCases(String programmingExerciseTitle, boolean scaActive) {
        Course course = addCourseWithNamedProgrammingExercise(programmingExerciseTitle, scaActive);
        ProgrammingExercise programmingExercise = exerciseUtilService.findProgrammingExerciseWithTitle(course.getExercises(), programmingExerciseTitle);

        addTestCasesToProgrammingExercise(programmingExercise);

        courseRepo.findById(course.getId()).get();
    }

    public void addTestCasesToProgrammingExercise(ProgrammingExercise programmingExercise) {
        // Clean up existing test cases
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(programmingExercise.getId()));

        List<ProgrammingExerciseTestCase> testCases = new ArrayList<>();
        testCases.add(new ProgrammingExerciseTestCase().testName("test1").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS).bonusMultiplier(1D)
                .bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("test2").weight(2.0).active(false).exercise(programmingExercise).visibility(Visibility.ALWAYS).bonusMultiplier(1D)
                .bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("test3").weight(3.0).active(true).exercise(programmingExercise).visibility(Visibility.AFTER_DUE_DATE)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCaseRepository.saveAll(testCases);

        List<ProgrammingExerciseTestCase> tests = new ArrayList<>(testCaseRepository.findByExerciseId(programmingExercise.getId()));
        assertThat(tests).as("test case is initialized").hasSize(3);
    }

    public void addBuildPlanAndSecretToProgrammingExercise(ProgrammingExercise programmingExercise, String buildPlan) {
        buildPlanRepository.setBuildPlanForExercise(buildPlan, programmingExercise);
        programmingExercise.generateAndSetBuildPlanAccessSecret();
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        var buildPlanOptional = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(programmingExercise.getId());
        assertThat(buildPlanOptional).isPresent();
        assertThat(buildPlanOptional.get().getBuildPlan()).as("build plan is set").isNotNull();
        assertThat(programmingExercise.getBuildPlanAccessSecret()).as("build plan access secret is set").isNotNull();
    }

    public AuxiliaryRepository addAuxiliaryRepositoryToExercise(ProgrammingExercise programmingExercise) {
        AuxiliaryRepository repository = new AuxiliaryRepository();
        repository.setName("auxrepo");
        repository.setDescription("Description");
        repository.setCheckoutDirectory("assignment/src");
        repository = auxiliaryRepositoryRepository.save(repository);
        programmingExercise.setAuxiliaryRepositories(List.of(repository));
        repository.setExercise(programmingExercise);
        programmingExerciseRepository.save(programmingExercise);
        return repository;
    }

    public void addSubmissionPolicyToExercise(SubmissionPolicy policy, ProgrammingExercise programmingExercise) {
        policy = submissionPolicyRepository.save(policy);
        programmingExercise.setSubmissionPolicy(policy);
        programmingExerciseRepository.save(programmingExercise);
    }

    public ProgrammingSubmission addProgrammingSubmission(ProgrammingExercise exercise, ProgrammingSubmission submission, String login) {
        StudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, login);
        submission.setParticipation(participation);
        submission = programmingSubmissionRepo.save(submission);
        return submission;
    }

    /**
     * Add a submission with a result to the given programming exercise. The submission will be assigned to the corresponding participation of the given login (if exists or
     * create a new participation).
     * The method will make sure that all necessary entities are connected.
     *
     * @param exercise   for which to create the submission/participation/result combination.
     * @param submission to use for adding to the exercise/participation/result.
     * @param login      of the user to identify the corresponding student participation.
     */
    public void addProgrammingSubmissionWithResult(ProgrammingExercise exercise, ProgrammingSubmission submission, String login) {
        StudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, login);
        submission = programmingSubmissionRepo.save(submission);
        Result result = resultRepo.save(new Result().participation(participation));
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submission.addResult(result);
        submission = programmingSubmissionRepo.save(submission);
        result.setSubmission(submission);
        result = resultRepo.save(result);
        participation.addResult(result);
        studentParticipationRepo.save(participation);
    }

    public ProgrammingSubmission addProgrammingSubmissionWithResultAndAssessor(ProgrammingExercise exercise, ProgrammingSubmission submission, String login, String assessorLogin,
            AssessmentType assessmentType, boolean hasCompletionDate) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        Result result = new Result();
        result.setAssessor(userUtilService.getUserByLogin(assessorLogin));
        result.setAssessmentType(assessmentType);
        result.setScore(50D);
        if (hasCompletionDate) {
            result.setCompletionDate(ZonedDateTime.now());
        }

        studentParticipationRepo.save(participation);
        programmingSubmissionRepo.save(submission);

        submission.setParticipation(participation);
        result.setParticipation(participation);

        result = resultRepo.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        // Manual results are always rated
        if (assessmentType == AssessmentType.SEMI_AUTOMATIC) {
            result.rated(true);
        }
        submission = programmingSubmissionRepo.save(submission);
        return submission;
    }

    public ProgrammingSubmission addProgrammingSubmissionToResultAndParticipation(Result result, StudentParticipation participation, String commitHash) {
        ProgrammingSubmission submission = createProgrammingSubmission(participation, false);
        submission.addResult(result);
        submission.setCommitHash(commitHash);
        resultRepo.save(result);
        result.setSubmission(submission);
        participation.addSubmission(submission);
        studentParticipationRepo.save(participation);
        return submissionRepository.save(submission);
    }

    public void addHintsToExercise(ProgrammingExercise exercise) {
        ExerciseHint exerciseHint1 = new ExerciseHint().content("content 1").exercise(exercise).title("title 1");
        ExerciseHint exerciseHint2 = new ExerciseHint().content("content 2").exercise(exercise).title("title 2");
        ExerciseHint exerciseHint3 = new ExerciseHint().content("content 3").exercise(exercise).title("title 3");
        exerciseHint1.setDisplayThreshold((short) 3);
        exerciseHint2.setDisplayThreshold((short) 3);
        exerciseHint3.setDisplayThreshold((short) 3);
        Set<ExerciseHint> hints = new HashSet<>();
        hints.add(exerciseHint1);
        hints.add(exerciseHint2);
        hints.add(exerciseHint3);
        exercise.setExerciseHints(hints);
        exerciseHintRepository.saveAll(hints);
        programmingExerciseRepository.save(exercise);
    }

    public void addTasksToProgrammingExercise(ProgrammingExercise programmingExercise) {
        StringBuilder problemStatement = new StringBuilder(programmingExercise.getProblemStatement());
        problemStatement.append('\n');

        var tasks = programmingExercise.getTestCases().stream().map(testCase -> {
            var task = new ProgrammingExerciseTask();
            task.setTaskName("Task for " + testCase.getTestName());
            task.setExercise(programmingExercise);
            task.setTestCases(Collections.singleton(testCase));
            testCase.setTasks(Collections.singleton(task));
            problemStatement.append("[task][").append(task.getTaskName()).append("](")
                    .append(task.getTestCases().stream().map(ProgrammingExerciseTestCase::getTestName).collect(Collectors.joining(","))).append(")\n");
            return task;
        }).toList();
        programmingExercise.setTasks(tasks);
        programmingExercise.setProblemStatement(problemStatement.toString());
        programmingExerciseTaskRepository.saveAll(tasks);
        programmingExerciseRepository.save(programmingExercise);
    }

    public void addSolutionEntriesToProgrammingExercise(ProgrammingExercise programmingExercise) {
        for (ProgrammingExerciseTestCase testCase : programmingExercise.getTestCases()) {
            var solutionEntry = new ProgrammingExerciseSolutionEntry();
            solutionEntry.setFilePath("test.txt");
            solutionEntry.setLine(1);
            solutionEntry.setCode("Line for " + testCase.getTestName());
            solutionEntry.setTestCase(testCase);

            testCase.setSolutionEntries(Collections.singleton(solutionEntry));
            solutionEntryRepository.save(solutionEntry);
        }
    }

    public void addCodeHintsToProgrammingExercise(ProgrammingExercise programmingExercise) {
        for (ProgrammingExerciseTask task : programmingExercise.getTasks()) {
            var solutionEntries = task.getTestCases().stream().flatMap(testCase -> testCase.getSolutionEntries().stream()).collect(Collectors.toSet());
            var codeHint = new CodeHint();
            codeHint.setTitle("Code Hint for " + task.getTaskName());
            codeHint.setContent("Content for " + task.getTaskName());
            codeHint.setExercise(programmingExercise);
            codeHint.setSolutionEntries(solutionEntries);
            codeHint.setProgrammingExerciseTask(task);

            programmingExercise.getExerciseHints().add(codeHint);
            codeHintRepository.save(codeHint);
            for (ProgrammingExerciseSolutionEntry solutionEntry : solutionEntries) {
                solutionEntry.setCodeHint(codeHint);
                solutionEntryRepository.save(solutionEntry);
            }
        }
    }

    public ProgrammingExercise loadProgrammingExerciseWithEagerReferences(ProgrammingExercise lazyExercise) {
        return programmingExerciseTestRepository.findOneWithEagerEverything(lazyExercise.getId());
    }
}
