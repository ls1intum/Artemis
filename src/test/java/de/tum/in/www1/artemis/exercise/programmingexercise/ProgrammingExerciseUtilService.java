package de.tum.in.www1.artemis.exercise.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.CategoryState;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.AuxiliaryRepositoryRepository;
import de.tum.in.www1.artemis.repository.BuildPlanRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionTestRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionPolicyRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.util.TestConstants;

/**
 * Service responsible for initializing the database with specific testdata related to programming exercises for use in integration tests.
 */
@Service
public class ProgrammingExerciseUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

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
    private ProgrammingSubmissionTestRepository programmingSubmissionRepo;

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

    @Autowired
    private GitService gitService;

    /**
     * Create an example programming exercise
     *
     * @return the created programming exercise
     */
    public ProgrammingExercise createSampleProgrammingExercise() {
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setTitle("Title");
        programmingExercise.setShortName("Shortname");
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setMaxPoints(10.0);
        programmingExercise.setBonusPoints(0.0);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        return programmingExercise;
    }

    /**
     * Adds template participation to the provided programming exercise.
     *
     * @param exercise The exercise to which the template participation should be added.
     * @return The programming exercise to which a participation was added.
     */
    public ProgrammingExercise addTemplateParticipationForProgrammingExercise(ProgrammingExercise exercise) {
        final var repoName = exercise.generateRepositoryName(RepositoryType.TEMPLATE);
        TemplateProgrammingExerciseParticipation participation = new TemplateProgrammingExerciseParticipation();
        participation.setProgrammingExercise(exercise);
        participation.setBuildPlanId(exercise.generateBuildPlanId(BuildPlanType.TEMPLATE));
        participation.setRepositoryUri(String.format("http://some.test.url/scm/%s/%s.git", exercise.getProjectKey(), repoName));
        participation.setInitializationState(InitializationState.INITIALIZED);
        templateProgrammingExerciseParticipationRepo.save(participation);
        exercise.setTemplateParticipation(participation);
        return programmingExerciseRepository.save(exercise);
    }

    /**
     * Adds a solution participation to the provided programming exercise.
     *
     * @param exercise The exercise to which the solution participation should be added.
     * @return The programming exercise to which a participation was added.
     */
    public ProgrammingExercise addSolutionParticipationForProgrammingExercise(ProgrammingExercise exercise) {
        final var repoName = exercise.generateRepositoryName(RepositoryType.SOLUTION);
        SolutionProgrammingExerciseParticipation participation = new SolutionProgrammingExerciseParticipation();
        participation.setProgrammingExercise(exercise);
        participation.setBuildPlanId(exercise.generateBuildPlanId(BuildPlanType.SOLUTION));
        participation.setRepositoryUri(String.format("http://some.test.url/scm/%s/%s.git", exercise.getProjectKey(), repoName));
        participation.setInitializationState(InitializationState.INITIALIZED);
        solutionProgrammingExerciseParticipationRepo.save(participation);
        exercise.setSolutionParticipation(participation);
        return programmingExerciseRepository.save(exercise);
    }

    /**
     * Creates and saves a course with an exam and an exercise group with a programming exercise. Test cases are added to this programming exercise.
     *
     * @return The newly created programming exercise with test cases.
     */
    public ProgrammingExercise addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases() {
        ProgrammingExercise programmingExercise = addCourseExamExerciseGroupWithOneProgrammingExercise();
        addTestCasesToProgrammingExercise(programmingExercise);
        return programmingExercise;
    }

    /**
     * Creates and saves a course with an exam and an exercise group with a programming exercise. The provided title and short name are used for the exercise and test cases are
     * added.
     *
     * @param title     The title of the exercise.
     * @param shortName The short name of the exercise.
     * @return The newly created programming exercise with test cases.
     */
    public ProgrammingExercise addCourseExamExerciseGroupWithOneProgrammingExercise(String title, String shortName) {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setExerciseGroup(exerciseGroup);
        ProgrammingExerciseFactory.populateUnreleasedProgrammingExercise(programmingExercise, shortName, title, false);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExercise = addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = addTemplateParticipationForProgrammingExercise(programmingExercise);

        return programmingExercise;
    }

    /**
     * Creates and saves course with an exam and an exercise group with a programming exercise. <code>Testtitle</code> is the title and <code>TESTEXFOREXAM</code> the short name of
     * the exercise.
     *
     * @return The newly created exam programming exercise.
     */
    public ProgrammingExercise addCourseExamExerciseGroupWithOneProgrammingExercise() {
        return addCourseExamExerciseGroupWithOneProgrammingExercise("Testtitle", "TESTEXFOREXAM");
    }

    /**
     * Adds a programming exercise into the exerciseGroupNumber-th exercise group of the provided exam.
     * exerciseGroupNumber must be smaller than the number of exercise groups!
     *
     * @param exam                The exam to which the exercise should be added.
     * @param exerciseGroupNumber Used as an index into which exercise group of the exam the programming exercise should be added. Has to be smaller than the number of exercise
     *                                groups!
     * @return The newly created exam programming exercise.
     */
    public ProgrammingExercise addProgrammingExerciseToExam(Exam exam, int exerciseGroupNumber) {
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setExerciseGroup(exam.getExerciseGroups().get(exerciseGroupNumber));
        ProgrammingExerciseFactory.populateUnreleasedProgrammingExercise(programmingExercise, "TESTEXFOREXAM", "Testtitle", false);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExercise = addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = addTemplateParticipationForProgrammingExercise(programmingExercise);

        exam.getExerciseGroups().get(exerciseGroupNumber).addExercise(programmingExercise);
        examRepository.save(exam);

        return programmingExercise;
    }

    /**
     * Creates and saves an already submitted programming submission done manually.
     *
     * @param participation The exercise participation.
     * @param buildFailed   True, if the submission resulted in a build failed.
     * @param commitHash    The commit hash of the submission.
     * @return The newly created programming submission.
     */
    public ProgrammingSubmission createProgrammingSubmission(Participation participation, boolean buildFailed, String commitHash) {
        ProgrammingSubmission programmingSubmission = ParticipationFactory.generateProgrammingSubmission(true);
        programmingSubmission.setBuildFailed(buildFailed);
        programmingSubmission.type(SubmissionType.MANUAL).submissionDate(ZonedDateTime.now());
        programmingSubmission.setCommitHash(commitHash);
        programmingSubmission.setParticipation(participation);
        return submissionRepository.save(programmingSubmission);
    }

    /**
     * Creates and saves an already submitted programming submission done manually. <code>9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d</code> is used as the commit hash.
     *
     * @param participation The exercise participation.
     * @param buildFailed   True, if the submission resulted in a build failed.
     * @return The newly created programming submission.
     */
    public ProgrammingSubmission createProgrammingSubmission(Participation participation, boolean buildFailed) {
        return createProgrammingSubmission(participation, buildFailed, TestConstants.COMMIT_HASH_STRING);
    }

    /**
     * Creates and saves a course with a programming exercise with static code analysis and test wise coverage disabled, java as the programming language.
     * Uses <code>Programming</code> as the title and <code>TSTEXC</code> as the short name of the exercise.
     *
     * @return The created course with a programming exercise.
     */
    public Course addCourseWithOneProgrammingExercise() {
        return addCourseWithOneProgrammingExercise(false);
    }

    /**
     * Creates and saves a course with a programming exercise with test wise coverage disabled and java as the programming language.
     * Uses <code>Programming</code> as the title and <code>TSTEXC</code> as the short name of the exercise.
     *
     * @param enableStaticCodeAnalysis True, if the static code analysis should be enabled for the exercise.
     * @return The created course with a programming exercise.
     */
    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis) {
        return addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, false, ProgrammingLanguage.JAVA);
    }

    /**
     * Creates and saves a course with a programming exercise with test wise coverage disabled and java as the programming language.
     *
     * @param enableStaticCodeAnalysis True, if the static code analysis should be enabled for the exercise.
     * @param title                    The title of the exercise.
     * @param shortName                The short name of the exercise.
     * @return The created course with a programming exercise.
     */
    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis, String title, String shortName) {
        return addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, false, ProgrammingLanguage.JAVA, title, shortName);
    }

    /**
     * Creates and saves a course with a programming exercise. Uses <code>Programming</code> as the title and <code>TSTEXC</code> as the short name of the exercise.
     *
     * @param enableStaticCodeAnalysis       True, if the static code analysis should be enabled for the exercise.
     * @param enableTestwiseCoverageAnalysis True, if test wise coverage analysis should be enabled for the exercise.
     * @param programmingLanguage            The programming language fo the exercise.
     * @return The created course with a programming exercise.
     */
    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis, boolean enableTestwiseCoverageAnalysis, ProgrammingLanguage programmingLanguage) {
        return addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, enableTestwiseCoverageAnalysis, programmingLanguage, "Programming", "TSTEXC");
    }

    /**
     * Creates and saves a course with a programming exercise.
     *
     * @param enableStaticCodeAnalysis       True, if the static code analysis should be enabled for the exercise.
     * @param enableTestwiseCoverageAnalysis True, if test wise coverage analysis should be enabled for the exercise.
     * @param programmingLanguage            The programming language fo the exercise.
     * @param title                          The title of the exercise.
     * @param shortName                      The short name of the exercise.
     * @return The created course with a programming exercise.
     */
    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis, boolean enableTestwiseCoverageAnalysis, ProgrammingLanguage programmingLanguage,
            String title, String shortName) {
        var course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, enableTestwiseCoverageAnalysis, programmingLanguage, title, shortName, null);
        return courseRepo.findByIdWithExercisesAndLecturesElseThrow(course.getId());
    }

    /**
     * Adds a java programming exercise with disabled test wise code coverage and static code analysis to the given course.
     *
     * @param course The course to which the exercise should be added.
     * @return The programming exercise which was added to the course.
     */
    public ProgrammingExercise addProgrammingExerciseToCourse(Course course) {
        return addProgrammingExerciseToCourse(course, false);
    }

    /**
     * Adds a java programming exercise with disabled test wise code coverage analysis to the given course.
     *
     * @param course                   The course to which the exercise should be added.
     * @param enableStaticCodeAnalysis True, if the static code analysis should be enabled for the exercise.
     * @return The programming exercise which was added to the course.
     */
    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis) {
        return addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, false, ProgrammingLanguage.JAVA);
    }

    /**
     * Adds a java programming exercise with disabled test wise code coverage analysis to the given course.
     *
     * @param course                   The course to which the exercise should be added.
     * @param enableStaticCodeAnalysis True, if the static code analysis should be enabled for the exercise.
     * @param assessmentDueDate        The assessment due date of the exercise.
     * @return The programming exercise which was added to the course.
     */
    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis, ZonedDateTime assessmentDueDate) {
        return addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, false, ProgrammingLanguage.JAVA, assessmentDueDate);
    }

    /**
     * Adds a programming exercise to the given course. Uses <code>Programming</code> as the title and <code>TSTEXC</code> as the short name of the exercise.
     *
     * @param course                         The course to which the exercise should be added.
     * @param enableStaticCodeAnalysis       True, if the static code analysis should be enabled for the exercise.
     * @param enableTestwiseCoverageAnalysis True, if test wise coverage analysis should be enabled for the exercise.
     * @param programmingLanguage            The programming language used in the exercise.
     * @param assessmentDueDate              The assessment due date of the exercise.
     * @return The programming exercise which was added to the course.
     */
    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis, boolean enableTestwiseCoverageAnalysis,
            ProgrammingLanguage programmingLanguage, ZonedDateTime assessmentDueDate) {
        return addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, enableTestwiseCoverageAnalysis, programmingLanguage, "Programming", "TSTEXC", assessmentDueDate);
    }

    /**
     * Adds a programming exercise without an assessment due date to the given course. Uses <code>Programming</code> as the title and <code>TSTEXC</code> as the short name of the
     * exercise.
     *
     * @param course                         The course to which the exercise should be added.
     * @param enableStaticCodeAnalysis       True, if the static code analysis should be enabled for the exercise.
     * @param enableTestwiseCoverageAnalysis True, if test wise coverage analysis should be enabled for the exercise.
     * @param programmingLanguage            The programming language used in the exercise.
     * @return The programming exercise which was added to the course.
     */
    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis, boolean enableTestwiseCoverageAnalysis,
            ProgrammingLanguage programmingLanguage) {
        return addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, enableTestwiseCoverageAnalysis, programmingLanguage, "Programming", "TSTEXC", null);
    }

    /**
     * Adds a programming exercise to the given course.
     *
     * @param course                         The course to which the exercise should be added.
     * @param enableStaticCodeAnalysis       True, if the static code analysis should be enabled for the exercise.
     * @param enableTestwiseCoverageAnalysis True, if test wise coverage analysis should be enabled for the exercise.
     * @param programmingLanguage            The programming language used in the exercise.
     * @param title                          The title of the exercise.
     * @param shortName                      The short name of the exercise.
     * @param assessmentDueDate              The assessment due date of the exercise.
     * @return The programming exercise which was added to the course.
     */
    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis, boolean enableTestwiseCoverageAnalysis,
            ProgrammingLanguage programmingLanguage, String title, String shortName, ZonedDateTime assessmentDueDate) {
        var programmingExercise = (ProgrammingExercise) new ProgrammingExercise().course(course);
        ProgrammingExerciseFactory.populateUnreleasedProgrammingExercise(programmingExercise, shortName, title, enableStaticCodeAnalysis, enableTestwiseCoverageAnalysis,
                programmingLanguage);
        programmingExercise.setAssessmentDueDate(assessmentDueDate);
        programmingExercise.setPresentationScoreEnabled(course.getPresentationScore() != 0);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        course.addExercises(programmingExercise);
        programmingExercise = addSolutionParticipationForProgrammingExercise(programmingExercise);

        return addTemplateParticipationForProgrammingExercise(programmingExercise);
    }

    /**
     * Creates and saves a course with a programming exercise with the given title.
     *
     * @param programmingExerciseTitle The title of the exercise.
     * @param scaActive                True, if static code analysis should be enabled.
     * @return The newly created course with a programming exercise.
     */
    public Course addCourseWithNamedProgrammingExercise(String programmingExerciseTitle, boolean scaActive) {
        var course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);

        var programmingExercise = (ProgrammingExercise) new ProgrammingExercise().course(course);
        ProgrammingExerciseFactory.populateUnreleasedProgrammingExercise(programmingExercise, "TSTEXC", programmingExerciseTitle, scaActive);
        programmingExercise.setPresentationScoreEnabled(course.getPresentationScore() != 0);

        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        course.addExercises(programmingExercise);
        programmingExercise = addSolutionParticipationForProgrammingExercise(programmingExercise);
        addTemplateParticipationForProgrammingExercise(programmingExercise);

        return courseRepo.findByIdWithExercisesAndLecturesElseThrow(course.getId());
    }

    /**
     * Creates and saves a course with a programming exercise and 3 active, always visible test cases with different weights.
     *
     * @return The newly created course with a programming exercise.
     */
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

        return courseRepo.findByIdWithEagerExercisesElseThrow(course.getId());
    }

    /**
     * Creates and saves a course with a java programming exercise with static code analysis enabled.
     *
     * @return The newly created programming exercise.
     */
    public ProgrammingExercise addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories() {
        return addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories(ProgrammingLanguage.JAVA);
    }

    /**
     * Creates and saves a course with a programming exercise with static code analysis enabled.
     *
     * @param programmingLanguage The programming language of the exercise.
     * @return The newly created programming exercise.
     */
    public ProgrammingExercise addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories(ProgrammingLanguage programmingLanguage) {
        Course course = addCourseWithOneProgrammingExercise(true, false, programmingLanguage);
        ProgrammingExercise programmingExercise = exerciseUtilService.findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        addStaticCodeAnalysisCategoriesToProgrammingExercise(programmingExercise);

        return programmingExercise;
    }

    /**
     * Adds 4 static code analysis categories to the given programming exercise. 2 are graded, 1 is inactive and 1 is feedback.
     *
     * @param programmingExercise The programming exercise to which static code analysis categories should be added.
     */
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

    /**
     * Creates and saves a course with a programming exercise and test cases.
     *
     * @return The newly created course with a programming exercise.
     */
    public Course addCourseWithOneProgrammingExerciseAndTestCases() {
        Course course = addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = exerciseUtilService.findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
        addTestCasesToProgrammingExercise(programmingExercise);
        return courseRepo.findByIdWithExercisesAndLecturesElseThrow(course.getId());
    }

    /**
     * Creates and saves a course with a named programming exercise and test cases.
     *
     * @param programmingExerciseTitle The title of the programming exercise.
     */
    public void addCourseWithNamedProgrammingExerciseAndTestCases(String programmingExerciseTitle) {
        addCourseWithNamedProgrammingExerciseAndTestCases(programmingExerciseTitle, false);
    }

    /**
     * Creates and saves a course with a named programming exercise and test cases.
     *
     * @param programmingExerciseTitle The title of the programming exercise.
     * @param scaActive                True, if the static code analysis should be activated.
     */
    public void addCourseWithNamedProgrammingExerciseAndTestCases(String programmingExerciseTitle, boolean scaActive) {
        Course course = addCourseWithNamedProgrammingExercise(programmingExerciseTitle, scaActive);
        ProgrammingExercise programmingExercise = exerciseUtilService.findProgrammingExerciseWithTitle(course.getExercises(), programmingExerciseTitle);

        addTestCasesToProgrammingExercise(programmingExercise);

        courseRepo.findById(course.getId()).orElseThrow();
    }

    /**
     * Adds 3 test cases to the given programming exercise. 2 are always visible and 1 is visible after due date. The test cases are weighted differently.
     *
     * @param programmingExercise The programming exercise to which test cases should be added.
     * @return The created programming exercise test cases.
     */
    public List<ProgrammingExerciseTestCase> addTestCasesToProgrammingExercise(ProgrammingExercise programmingExercise) {
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

        return testCases;
    }

    /**
     * Adds an active test case to the given programming exercise. The test case is always visible.
     *
     * @param programmingExercise The programming exercise to which a test case should be added.
     * @param testName            The name of the test case.
     * @return The created programming exercise test case.
     */
    public ProgrammingExerciseTestCase addTestCaseToProgrammingExercise(ProgrammingExercise programmingExercise, String testName) {
        var testCase = new ProgrammingExerciseTestCase().testName(testName).weight(1.).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS).bonusMultiplier(1.)
                .bonusPoints(0.);
        return testCaseRepository.save(testCase);
    }

    /**
     * Adds build plan and build plan access secret to the given programming exercise.
     *
     * @param programmingExercise The exercise to which the build plan should be added.
     * @param buildPlan           The build plan script.
     */
    public void addBuildPlanAndSecretToProgrammingExercise(ProgrammingExercise programmingExercise, String buildPlan) {
        buildPlanRepository.setBuildPlanForExercise(buildPlan, programmingExercise);
        programmingExercise.generateAndSetBuildPlanAccessSecret();
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        var buildPlanOptional = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(programmingExercise.getId());
        assertThat(buildPlanOptional).isPresent();
        assertThat(buildPlanOptional.get().getBuildPlan()).as("build plan is set").isNotNull();
        assertThat(programmingExercise.getBuildPlanAccessSecret()).as("build plan access secret is set").isNotNull();
    }

    /**
     * Creates, saves and adds an auxiliary repository to the given programming exercise.
     *
     * @param programmingExercise The exercise to which the auxiliary repository should be added.
     * @return The newly created auxiliary repository.
     */
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

    /**
     * Adds submission policy to a programming exercise and saves the exercise.
     *
     * @param policy              The submission policy which should be added to the exercise.
     * @param programmingExercise The exercise to which the submission policy should be added.
     */
    public void addSubmissionPolicyToExercise(SubmissionPolicy policy, ProgrammingExercise programmingExercise) {
        policy = submissionPolicyRepository.save(policy);
        programmingExercise.setSubmissionPolicy(policy);
        programmingExerciseRepository.save(programmingExercise);
    }

    /**
     * Adds programming submission to provided programming exercise. The provided login is used to access or create a participation.
     *
     * @param exercise   The exercise to which the submission should be added.
     * @param submission The submission which should be added to the programming exercise.
     * @param login      The login of the user used to access or create an exercise participation.
     * @return The created programming submission.
     */
    public ProgrammingSubmission addProgrammingSubmission(ProgrammingExercise exercise, ProgrammingSubmission submission, String login) {
        StudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, login);
        submission.setParticipation(participation);
        submission = programmingSubmissionRepo.save(submission);
        return submission;
    }

    /**
     * Adds a submission with a result to the given programming exercise. The submission will be assigned to the corresponding participation of the given login (if exists or
     * create a new participation).
     * The method will make sure that all necessary entities are connected.
     *
     * @param exercise   The exercise for which to create the submission/participation/result combination.
     * @param submission The submission to use for adding to the exercise/participation/result.
     * @param login      The login of the user used to access or create an exercise participation.
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

    /**
     * Adds a programming submission with a result and assessor to the given programming exercise.
     *
     * @param exercise          The exercise to which the submission should be added.
     * @param submission        The submission which should be added to the exercise.
     * @param login             The user login used to access or create the exercise participation.
     * @param assessorLogin     The login of the user assessing the exercise.
     * @param assessmentType    The type of the assessment.
     * @param hasCompletionDate True, if the result has a completion date.
     * @return The programming submission.
     */
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

    /**
     * Adds a programming submission to result and participation of a programming exercise.
     *
     * @param result        The result of the programming exercise.
     * @param participation The participation of the programming exercise.
     * @param commitHash    The commit hash of the submission.
     * @return The newly created programming submission.
     */
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

    /**
     * Adds 3 hints to the given programming exercise. Each hint has a unique content and title. All have a display threshold of 3.
     *
     * @param exercise The exercise to which hints should be added.
     */
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

    /**
     * Adds a task for each test case and adds it to the problem statement of the programming exercise.
     *
     * @param programmingExercise The programming exercise to which tasks should be added.
     */
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

    /**
     * Adds a solution entry to each test case of the given programming exercise.
     *
     * @param programmingExercise The exercise to which solution entries should be added.
     */
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

    /**
     * Adds a code hint to each task of the given programming exercise.
     *
     * @param programmingExercise The programming exercise to which code hints should be added.
     */
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
            codeHint = codeHintRepository.save(codeHint);
            for (ProgrammingExerciseSolutionEntry solutionEntry : solutionEntries) {
                solutionEntry.setCodeHint(codeHint);
                solutionEntryRepository.save(solutionEntry);
            }
        }
    }

    /**
     * Loads a programming exercise with eager references from the repository.
     *
     * @param lazyExercise The exercise without references, the id is used when accessing the repository.
     * @return The programming exercise with references.
     */
    public ProgrammingExercise loadProgrammingExerciseWithEagerReferences(ProgrammingExercise lazyExercise) {
        return programmingExerciseTestRepository.findOneWithEagerEverything(lazyExercise.getId());
    }

    /**
     * Creates an example repository and makes the given GitService return it when asked to check it out.
     *
     * @throws Exception if creating the repository fails
     */
    public void createGitRepository() throws Exception {
        // Create repository
        var testRepo = new LocalRepository(defaultBranch);
        testRepo.configureRepos("testLocalRepo", "testOriginRepo");
        // Add test file to the repository folder
        Path filePath = Path.of(testRepo.localRepoFile + "/Test.java");
        var file = Files.createFile(filePath).toFile();
        FileUtils.write(file, "Test", Charset.defaultCharset());
        // Create mock repo that has the file
        var mockRepository = mock(Repository.class);
        doReturn(true).when(mockRepository).isValidFile(any());
        doReturn(testRepo.localRepoFile.toPath()).when(mockRepository).getLocalPath();
        // Mock Git service operations
        doReturn(mockRepository).when(gitService).getOrCheckoutRepository(any(), any(), any(), anyBoolean(), anyString());
        doNothing().when(gitService).resetToOriginHead(any());
        doReturn(Paths.get("repo.zip")).when(gitService).zipRepositoryWithParticipation(any(), anyString(), anyBoolean());
    }
}
