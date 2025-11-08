package de.tum.cit.aet.artemis.exam.util;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationFactory;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamSession;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.ExamSessionRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadSubmissionRepository;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseFactory;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.test_repository.ModelingExerciseTestRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Service responsible for initializing the database with specific testdata related to exams for use in integration tests.
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class ExamUtilService {

    private static final ZonedDateTime PAST_TIMESTAMP = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime FUTURE_FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(2);

    @Autowired
    private CourseTestRepository courseRepo;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepo;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Autowired
    private ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private UserTestRepository userRepo;

    @Autowired
    private ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ConversationTestRepository conversationRepository;

    @Autowired
    private PostTestRepository postRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ExamSessionRepository examSessionRepository;

    @Autowired
    private ModelingExerciseTestRepository modelingExerciseRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private FileUploadSubmissionRepository fileUploadSubmissionRepo;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    /**
     * Creates and saves a course with an exam and an exercise group with all exercise types excluding programming exercises.
     *
     * @param user    The User who should be registered for the Exam
     * @param visible The visible date of the Exam
     * @param start   The start date of the Exam
     * @param end     The end date of the Exam
     * @return The newly created course
     */
    public Course createCourseWithExamAndExerciseGroupAndExercises(Course course, User user, ZonedDateTime visible, ZonedDateTime start, ZonedDateTime end) {
        Exam exam = addExamWithUser(course, user, false, visible, start, end);
        course.addExam(exam);
        addExerciseGroupsAndExercisesToExam(exam, false);
        return courseRepo.save(course);
    }

    /**
     * Creates and saves a ModelingExercise. Also creates an active Course and an Exam with a mandatory ExerciseGroup the Modeling Exercise belongs to.
     *
     * @return The created ModelingExercise
     */
    public ModelingExercise addCourseExamExerciseGroupWithOneModelingExercise() {
        return addCourseExamExerciseGroupWithOneModelingExercise("ClassDiagram");
    }

    /**
     * Creates and saves a ModelingExercise. Also creates an active Course and an Exam with a mandatory ExerciseGroup the Modeling Exercise belongs to.
     *
     * @param title The title of the ModelingExercise
     * @return The created ModelingExercise
     */
    public ModelingExercise addCourseExamExerciseGroupWithOneModelingExercise(String title) {
        ExerciseGroup exerciseGroup = addExerciseGroupWithExamAndCourse(true);
        ModelingExercise classExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup);
        classExercise.setTitle(title);
        classExercise = modelingExerciseRepository.save(classExercise);
        return classExercise;
    }

    /**
     * Creates and saves a course with an exam and an exercise group with all exercise types excluding programming exercises. Sets the visible (now - 1 min), start (now) and end
     * (now + 1 min) date with default values.
     *
     * @param user The User who should be registered for the Exam
     * @return The newly created course
     */
    public Course createCourseWithExamAndExerciseGroupAndExercises(Course course, User user) {
        Exam exam = addExamWithUser(course, user, false, ZonedDateTime.now().minusMinutes(1), ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(1));
        course.addExam(exam);
        addExerciseGroupsAndExercisesToExam(exam, false);
        return courseRepo.save(course);
    }

    /**
     * Creates and saves a StudentExam for a test run.
     *
     * @param exam                        The exam for which the test run should be created
     * @param instructor                  The User who should be registered for the test run
     * @param exerciseGroupsWithExercises The ExerciseGroups with Exercises that should be added to the test run
     * @return The newly created StudentExam
     */
    public StudentExam setupTestRunForExamWithExerciseGroupsForInstructor(Exam exam, User instructor, List<ExerciseGroup> exerciseGroupsWithExercises) {
        List<Exercise> exercises = new ArrayList<>();
        exerciseGroupsWithExercises.forEach(exerciseGroup -> exercises.add(exerciseGroup.getExercises().iterator().next()));
        var testRun = generateTestRunForInstructor(exam, instructor, exercises);
        return studentExamRepository.save(testRun);
    }

    /**
     * Creates and saves a StudentExam and StudentParticipations for a test run.
     *
     * @param exam       The exam for which the test run should be created
     * @param instructor The User who should be registered for the test run
     * @param exercises  The Exercises that should be added to the test run
     * @return The newly created StudentExam
     */
    public StudentExam generateTestRunForInstructor(Exam exam, User instructor, List<Exercise> exercises) {
        var testRun = ExamFactory.generateExamTestRun(exam);
        testRun.setUser(instructor);
        for (final var exercise : exercises) {
            testRun.addExercise(exercise);
            assertThat(exercise.isExamExercise()).isTrue();
            Submission submission = null;
            switch (exercise) {
                case ModelingExercise modelingExercise -> submission = modelingExerciseUtilService.addModelingSubmission(modelingExercise,
                        ParticipationFactory.generateModelingSubmission("", true), instructor.getLogin());
                case TextExercise textExercise ->
                    submission = textExerciseUtilService.saveTextSubmission(textExercise, ParticipationFactory.generateTextSubmission("", null, true), instructor.getLogin());
                case QuizExercise quizExercise ->
                    submission = quizExerciseUtilService.saveQuizSubmission(quizExercise, ParticipationFactory.generateQuizSubmission(true), instructor.getLogin());
                case ProgrammingExercise programmingExercise -> {
                    submission = new ProgrammingSubmission().submitted(true);
                    addProgrammingSubmission(programmingExercise, (ProgrammingSubmission) submission, instructor.getLogin());
                    submission = submissionRepository.save(submission);
                }
                case FileUploadExercise fileUploadExercise ->
                    submission = saveFileUploadSubmission(fileUploadExercise, ParticipationFactory.generateFileUploadSubmission(true), instructor.getLogin());
                default -> {
                }
            }
            var studentParticipation = (StudentParticipation) submission.getParticipation();
            studentParticipation.setTestRun(true);
            studentParticipationRepo.save(studentParticipation);
        }
        return testRun;
    }

    /**
     * Adds programming submission to provided programming exercise. The provided login is used to access or create a participation.
     * NOTE: this code is duplicated in ProgrammingExerciseUtilService to avoid circular dependencies, so if you change it here, please also change it there.
     * TODO: refactor this code into a common smaller test service to avoid circular dependencies and code duplication in the future.
     *
     * @param exercise   The exercise to which the submission should be added.
     * @param submission The submission which should be added to the programming exercise.
     * @param login      The login of the user used to access or create an exercise participation.
     * @return The created programming submission.
     */
    public ProgrammingSubmission addProgrammingSubmission(ProgrammingExercise exercise, ProgrammingSubmission submission, String login) {
        StudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, login);
        submission.setParticipation(participation);
        submission = programmingSubmissionRepository.save(submission);
        return submission;
    }

    /**
     * Creates and saves a StudentParticipation for the given FileUploadExercise, the FileUploadSubmission, and login.
     * NOTE: this code is duplicated in FileUploadExerciseUtilService to avoid circular dependencies, so if you change it here, please also change it there.
     * TODO: refactor this code into a common smaller test service to avoid circular dependencies and code duplication in the future.
     *
     * @param exercise   The FileUploadExercise the StudentParticipation should belong to
     * @param submission The FileUploadSubmission the StudentParticipation should belong to
     * @param login      The login of the user the StudentParticipation should belong to
     * @return The updated FileUploadSubmission
     */
    public FileUploadSubmission saveFileUploadSubmission(FileUploadExercise exercise, FileUploadSubmission submission, String login) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        fileUploadSubmissionRepo.save(submission);
        return submission;
    }

    /**
     * Creates and saves an Exam with three mandatory and two optional ExerciseGroups. Each ExerciseGroup contains three TextExercises. Registers students for the Exam given the
     * userPrefix and numberOfStudents.
     *
     * @param userPrefix       The prefix of the users
     * @param course           The Course to which the Exam should be added
     * @param numberOfStudents The number of students that should be registered for the Exam
     * @return The newly created Exam
     */
    public Exam setupExamWithExerciseGroupsExercisesRegisteredStudents(String userPrefix, Course course, int numberOfStudents) {
        Exam exam = ExamFactory.generateExam(course);
        exam.setNumberOfExercisesInExam(4);
        exam.setRandomizeExerciseOrder(true);
        exam.setStartDate(ZonedDateTime.now().plusHours(2));
        exam.setEndDate(ZonedDateTime.now().plusHours(4));
        exam.setWorkingTime(2 * 60 * 60);
        exam.setExamMaxPoints(20);
        exam = examRepository.save(exam);

        // add exercise groups: 3 mandatory, 2 optional
        ExamFactory.generateExerciseGroup(true, exam);
        ExamFactory.generateExerciseGroup(true, exam);
        ExamFactory.generateExerciseGroup(true, exam);
        ExamFactory.generateExerciseGroup(false, exam);
        ExamFactory.generateExerciseGroup(false, exam);
        exam = examRepository.save(exam);

        // TODO: also add other exercise types

        // add exercises
        var exercise1a = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().getFirst());
        var exercise1b = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().getFirst());
        var exercise1c = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().getFirst());
        exerciseRepository.saveAll(List.of(exercise1a, exercise1b, exercise1c));

        var exercise2a = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(1));
        var exercise2b = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(1));
        var exercise2c = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(1));
        exerciseRepository.saveAll(List.of(exercise2a, exercise2b, exercise2c));

        var exercise3a = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(2));
        var exercise3b = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(2));
        var exercise3c = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(2));
        exerciseRepository.saveAll(List.of(exercise3a, exercise3b, exercise3c));

        var exercise4a = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(3));
        var exercise4b = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(3));
        var exercise4c = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(3));
        exerciseRepository.saveAll(List.of(exercise4a, exercise4b, exercise4c));

        var exercise5a = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(4));
        var exercise5b = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(4));
        var exercise5c = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(4));
        exerciseRepository.saveAll(List.of(exercise5a, exercise5b, exercise5c));

        // register user
        return registerUsersForExamAndSaveExam(exam, userPrefix, numberOfStudents);
    }

    /**
     * Saves and updates the Exam after registering new Users.
     *
     * @param exam             The exam for which the users should be registered
     * @param userPrefix       The prefix of the users
     * @param numberOfStudents The number of students that should be registered for the Exam
     * @return The updated Exam
     */
    public Exam registerUsersForExamAndSaveExam(Exam exam, String userPrefix, int numberOfStudents) {
        return registerUsersForExamAndSaveExam(exam, userPrefix, 1, numberOfStudents);
    }

    /**
     * Saves and updates the Exam after registering new Users.
     *
     * @param exam       The exam for which the users should be registered
     * @param userPrefix The prefix of the users
     * @param from       The index of the first student to be registered
     * @param to         The index of the last student to be registered
     * @return The updated Exam
     */
    public Exam registerUsersForExamAndSaveExam(Exam exam, String userPrefix, int from, int to) {

        for (int i = from; i <= to; i++) {
            ExamUser registeredExamUser = new ExamUser();
            registeredExamUser.setUser(userUtilService.getUserByLogin(userPrefix + "student" + i));
            registeredExamUser.setExam(exam);
            exam.addExamUser(registeredExamUser);
            examUserRepository.save(registeredExamUser);
        }

        return examRepository.save(exam);
    }

    /**
     * Creates and saves an Exam without ExerciseGroups and Exercises.
     *
     * @param course The Course to which the Exam should be added
     * @return The newly created Exam
     */
    public Exam addExam(Course course) {
        Exam exam = ExamFactory.generateExam(course);
        return examRepository.save(exam);
    }

    /**
     * Creates and saves a test Exam without ExerciseGroups and Exercises.
     *
     * @param course The Course to which the Exam should be added
     * @return The newly created Exam
     */
    public Exam addTestExam(Course course) {
        Exam exam = ExamFactory.generateTestExam(course);
        return examRepository.save(exam);
    }

    /**
     * Creates and saves a test Exam without Exercises and registers the given user to the Exam.
     *
     * @param course The Course to which the Exam should be added
     * @param user   The User who should be registered for the Exam
     * @return The newly created Exam
     */
    public Exam addTestExamWithRegisteredUser(Course course, User user) {
        Exam exam = ExamFactory.generateTestExam(course);
        exam = examRepository.save(exam);
        var registeredExamUser = new ExamUser();
        registeredExamUser.setUser(user);
        registeredExamUser.setExam(exam);
        registeredExamUser = examUserRepository.save(registeredExamUser);
        exam.addExamUser(registeredExamUser);
        examRepository.save(exam);
        return exam;
    }

    /**
     * Creates and saves an Exam with a registered User and an optional ExerciseGroup.
     *
     * @param course        The Course to which the Exam should be added
     * @param user          The User who should be registered for the Exam
     * @param exerciseGroup True, if an empty ExerciseGroup should be added to the Exam
     * @param visibleDate   The visible date of the Exam
     * @param startDate     The start date of the Exam
     * @param endDate       The end date of the Exam
     * @return The newly created Exam
     */
    public Exam addExamWithUser(Course course, User user, boolean exerciseGroup, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate) {
        Exam exam = ExamFactory.generateExam(course);
        if (exerciseGroup) {
            ExamFactory.generateExerciseGroup(true, exam);
        }
        exam = examRepository.save(exam);
        var registeredExamUser = new ExamUser();
        registeredExamUser.setUser(user);
        registeredExamUser.setExam(exam);
        registeredExamUser = examUserRepository.save(registeredExamUser);
        exam.addExamUser(registeredExamUser);
        exam.setVisibleDate(visibleDate);
        exam.setStartDate(startDate);
        exam.setEndDate(endDate);
        exam.setWorkingTime(exam.getDuration());
        exam.setNumberOfCorrectionRoundsInExam(1);
        examRepository.save(exam);
        return exam;
    }

    /**
     * Creates and saves an Exam with a registered User, with a results publication date and without an ExerciseGroup.
     *
     * @param course             The Course to which the Exam should be added
     * @param user               The User who should be registered for the Exam
     * @param visibleDate        The visible date of the Exam
     * @param startDate          The start date of the Exam
     * @param endDate            The end date of the Exam
     * @param publishResultsDate The results publication date of the Exam
     * @return The newly created Exam
     */
    public Exam addExam(Course course, User user, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate, ZonedDateTime publishResultsDate) {
        Exam exam = addExamWithUser(course, user, false, visibleDate, startDate, endDate);
        exam.setPublishResultsDate(publishResultsDate);
        return examRepository.save(exam);
    }

    /**
     * Creates and saves an Exam with a registered User and an ExerciseGroup. The Exam is visible, starts in 10 minutes, and ends in 60 minutes.
     *
     * @param course    The Course to which the Exam should be added
     * @param mandatory True, if the ExerciseGroup should be mandatory
     * @return The newly created Exam
     */
    public Exam addExamWithExerciseGroup(Course course, boolean mandatory) {
        Exam exam = ExamFactory.generateExam(course);
        ExamFactory.generateExerciseGroup(mandatory, exam);
        exam = examRepository.save(exam);
        return exam;
    }

    /**
     * Creates and saves a test Exam with a registered User and an ExerciseGroup. The Exam is visible, starts in 10 minutes, and ends in 80 minutes.
     *
     * @param course    The Course to which the Exam should be added
     * @param mandatory True, if the ExerciseGroup should be mandatory
     * @return The newly created Exam
     */
    public Exam addTestExamWithExerciseGroup(Course course, boolean mandatory) {
        Exam exam = ExamFactory.generateTestExam(course);
        ExamFactory.generateExerciseGroup(mandatory, exam);
        exam = examRepository.save(exam);
        return exam;
    }

    /**
     * Creates and saves an Exam without ExerciseGroups and Exercises.
     *
     * @param course      The Course to which the Exam should be added
     * @param visibleDate The visible date of the Exam
     * @param startDate   The start date of the Exam
     * @param endDate     The end date of the Exam
     * @return The newly created Exam
     */
    public Exam addExam(Course course, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate) {
        Exam exam = ExamFactory.generateExam(course);
        exam.setVisibleDate(visibleDate);
        exam.setStartDate(startDate);
        exam.setEndDate(endDate);
        exam.setWorkingTime(exam.getDuration());
        exam.setGracePeriod(180);
        exam = examRepository.save(exam);
        return exam;
    }

    /**
     * Creates and saves an Exam without ExerciseGroups and Exercises.
     *
     * @param course             The Course to which the Exam should be added
     * @param visibleDate        The visible date of the Exam
     * @param startDate          The start date of the Exam
     * @param endDate            The end date of the Exam
     * @param publishResultsDate The results publication date of the Exam
     * @return The newly created Exam
     */
    public Exam addExam(Course course, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate, ZonedDateTime publishResultsDate) {
        Exam exam = ExamFactory.generateExam(course);
        exam.setVisibleDate(visibleDate);
        exam.setStartDate(startDate);
        exam.setEndDate(endDate);
        exam.setPublishResultsDate(publishResultsDate);
        exam.setWorkingTime(exam.getDuration());
        exam.setGracePeriod(180);
        exam = examRepository.save(exam);
        return exam;
    }

    /**
     * Creates and saves an Exam without ExerciseGroups and Exercises.
     *
     * @param course                 The Course to which the Exam should be added
     * @param visibleDate            The visible date of the Exam
     * @param startDate              The start date of the Exam
     * @param endDate                The end date of the Exam
     * @param publishResultsDate     The results publication date of the Exam
     * @param studentReviewStartDate The date on which the student review starts
     * @param studentReviewEndDate   The date on which the student review ends
     * @return The newly created Exam
     */
    public Exam addExam(Course course, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate, ZonedDateTime publishResultsDate,
            ZonedDateTime studentReviewStartDate, ZonedDateTime studentReviewEndDate, String examiner) {
        Exam exam = ExamFactory.generateExam(course);
        exam.setVisibleDate(visibleDate);
        exam.setStartDate(startDate);
        exam.setEndDate(endDate);
        exam.setPublishResultsDate(publishResultsDate);
        exam.setExamStudentReviewStart(studentReviewStartDate);
        exam.setExamStudentReviewEnd(studentReviewEndDate);
        exam.setWorkingTime(exam.getDuration());
        exam.setGracePeriod(180);
        exam.setExaminer(examiner);
        exam = examRepository.save(exam);
        return exam;
    }

    /**
     * Creates and saves a Channel for the given Exam.
     *
     * @param exam        The Exam for which the Channel should be created
     * @param channelName The channel name
     * @return The newly created Channel
     */
    public Channel addExamChannel(Exam exam, String channelName) {
        Channel channel = ConversationFactory.generatePublicChannel(exam.getCourse(), channelName, true);
        channel.setExam(exam);
        return conversationRepository.save(channel);
    }

    /**
     * Creates and saves a Post for the given Channel.
     *
     * @param channel The Channel for which the Post should be created
     * @param title   The title of the Post
     * @param author  The author of the Post
     * @return The newly created Post
     */
    public Post createPost(Channel channel, String title, User author) {
        Post post = new Post();
        post.setTitle(title);
        post.setConversation(channel);
        post.setAuthor(author);
        return postRepository.save(post);
    }

    /**
     * Creates and saves an AnswerPost for the given Post.
     *
     * @param post    The Post for which the AnswerPost should be created
     * @param content The content of the AnswerPost
     * @param author  The author of the AnswerPost
     */
    public void createAnswerPost(Post post, String content, User author) {
        AnswerPost answerPost = new AnswerPost();
        answerPost.setContent(content);
        answerPost.setPost(post);
        answerPost.setAuthor(author);
        answerPostRepository.save(answerPost);
    }

    /**
     * Creates and saves an Exam and corresponding StudentExam with a registered User. The Exam started 1 hour ago and ends in 1 hour.
     *
     * @param course The Course to which the Exam should be added
     * @param user   The User who should be registered for the Exam
     * @return The newly created Exam
     */
    public Exam addActiveExamWithRegisteredUser(Course course, User user) {
        Exam exam = ExamFactory.generateExam(course);
        exam.setStartDate(ZonedDateTime.now().minusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(1));
        exam.setWorkingTime(2 * 60 * 60);
        exam = examRepository.save(exam);
        var registeredExamUser = new ExamUser();
        registeredExamUser.setUser(user);
        registeredExamUser.setExam(exam);
        registeredExamUser = examUserRepository.save(registeredExamUser);
        exam.addExamUser(registeredExamUser);
        exam.setTestExam(false);
        examRepository.save(exam);
        var studentExam = new StudentExam();
        studentExam.setExam(exam);
        studentExam.setTestRun(false);
        studentExam.setUser(user);
        studentExam.setWorkingTime(exam.getDuration());
        studentExamRepository.save(studentExam);
        return exam;
    }

    /**
     * Creates and saves an Exam with a registered User and without a StudentExam. The Exam started 1 hour ago and ends in 1 hour.
     *
     * @param course The Course to which the Exam should be added
     * @param user   The User who should be registered for the Exam
     * @return The newly created Exam
     */
    public Exam addActiveTestExamWithRegisteredUserWithoutStudentExam(Course course, User user) {
        Exam exam = ExamFactory.generateTestExam(course);
        exam.setStartDate(ZonedDateTime.now().minusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(1));
        exam.setWorkingTime(2 * 60 * 60);
        exam = examRepository.save(exam);
        var registeredExamUser = new ExamUser();
        registeredExamUser.setUser(user);
        registeredExamUser.setExam(exam);
        registeredExamUser = examUserRepository.save(registeredExamUser);
        exam.addExamUser(registeredExamUser);
        examRepository.save(exam);
        return exam;
    }

    /**
     * Creates and saves an Exam with five ExerciseGroups (0: modelling, 1: text, 2: file upload, 3: quiz, 4: empty)
     *
     * @param course The Course to which the Exam should be added
     * @return The newly created Exam
     */
    public Exam addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(Course course) {
        Exam exam = addExam(course);
        for (int i = 0; i <= 4; i++) {
            ExamFactory.generateExerciseGroupWithTitle(true, exam, "Group " + i);
        }
        exam.setNumberOfExercisesInExam(5);
        exam.setExamMaxPoints(5 * 5);
        exam = examRepository.save(exam);

        ExerciseGroup modellingGroup = exam.getExerciseGroups().getFirst();
        Exercise modelling = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, modellingGroup);
        modellingGroup.addExercise(modelling);
        exerciseRepository.save(modelling);

        ExerciseGroup textGroup = exam.getExerciseGroups().get(1);
        Exercise text = TextExerciseFactory.generateTextExerciseForExam(textGroup);
        textGroup.addExercise(text);
        exerciseRepository.save(text);

        ExerciseGroup fileUploadGroup = exam.getExerciseGroups().get(2);
        Exercise fileUpload = FileUploadExerciseFactory.generateFileUploadExerciseForExam("png", fileUploadGroup);
        fileUploadGroup.addExercise(fileUpload);
        exerciseRepository.save(fileUpload);

        ExerciseGroup quizGroup = exam.getExerciseGroups().get(3);
        Exercise quiz = QuizExerciseFactory.generateQuizExerciseForExam(quizGroup);
        quizGroup.addExercise(quiz);
        exerciseRepository.save(quiz);

        return exam;
    }

    /**
     * Creates and saves an exam with five exercise groups (0: modelling, 1: text, 2: file upload, 3: quiz, 4: programming)
     *
     * @param course The Course to which the Exam should be added
     * @return The newly created Exam
     */
    public Exam addExamWithModellingAndTextAndFileUploadAndQuizAndProgramming(Course course) {
        Exam exam = addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course);
        ExerciseGroup programmingGroup = exam.getExerciseGroups().get(4);
        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(programmingGroup);
        Set<GradingCriterion> gradingCriteria = ProgrammingExerciseFactory.generateGradingCriteria(programmingExercise);
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        exerciseRepository.save(programmingExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        programmingGroup.addExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        return exam;
    }

    /**
     * Creates and saves a StudentExam for the given Exam.
     *
     * @param exam The Exam for which the StudentExam should be created
     * @return The newly created StudentExam
     */
    public StudentExam addStudentExam(Exam exam) {
        StudentExam studentExam = ExamFactory.generateStudentExam(exam);
        studentExam.setWorkingTime(exam.getDuration());
        studentExam = studentExamRepository.save(studentExam);
        return studentExam;
    }

    /**
     * Creates and saves a StudentExam for the given Exam and the user's login.
     *
     * @param exam      The Exam for which the StudentExam should be created
     * @param userLogin The login of the User for which the StudentExam should be created
     * @return The newly created StudentExam
     */
    public StudentExam addStudentExamWithUser(Exam exam, String userLogin) {
        return addStudentExamWithUser(exam, userRepo.findOneByLogin(userLogin).orElseThrow());
    }

    public StudentExam addStudentExamWithUserAndWorkingTime(Exam exam, String userLogin, int workingTime) {
        return addStudentExamWithUserAndWorkingTime(exam, userRepo.findOneByLogin(userLogin).orElseThrow(), workingTime);
    }

    /**
     * Creates and saves a StudentExam for the given Exam and User.
     *
     * @param exam The Exam for which the StudentExam should be created
     * @param user The User for which the StudentExam should be created
     * @return The newly created StudentExam
     */
    public StudentExam addStudentExamWithUser(Exam exam, User user) {
        return addStudentExamWithUserAndWorkingTime(exam, user, exam.getDuration());
    }

    /**
     * Creates and saves a StudentExam for the given Exam and User with the given working time.
     *
     * @param exam        The Exam for which the StudentExam should be created
     * @param user        The User for which the StudentExam should be created
     * @param workingTime The working time for the StudentExam
     * @return The newly created StudentExam
     */
    public StudentExam addStudentExamWithUserAndWorkingTime(Exam exam, User user, int workingTime) {
        StudentExam studentExam = ExamFactory.generateStudentExam(exam);
        studentExam.setUser(user);
        studentExam.setWorkingTime(workingTime);
        studentExam = studentExamRepository.save(studentExam);
        return studentExam;
    }

    /**
     * Creates and saves an ExamSession with the given parameters and adds it to the given student exam.
     *
     * @param studentExam        The StudentExam to which the ExamSession should be added
     * @param sessionToken       The session token of the ExamSession
     * @param ipAddress          The IP address of the ExamSession
     * @param browserFingerprint The browser fingerprint hash of the ExamSession
     * @param instanceId         The instance id of the ExamSession
     * @param userAgent          The user agent of the ExamSession
     */
    public void addExamSessionToStudentExam(StudentExam studentExam, String sessionToken, String ipAddress, String browserFingerprint, String instanceId, String userAgent) {
        ExamSession examSession = new ExamSession();
        examSession.setSessionToken(sessionToken);
        examSession.setIpAddress(ipAddress);
        examSession.setBrowserFingerprintHash(browserFingerprint);
        examSession.setInstanceId(instanceId);
        examSession.setStudentExam(studentExam);
        examSession.setUserAgent(userAgent);
        examSession.setStudentExam(studentExam);
        examSession = examSessionRepository.save(examSession);
        studentExam = studentExam.addExamSession(examSession);
        studentExamRepository.save(studentExam);
    }

    /**
     * Creates and saves a StudentExam with a registered User. Creates and saves an empty Course first. Then, given the new Course and User, creates and saves an Exam that
     * started 1 hour ago and ends in 1 hour. The Exam is then used to create the StudentExam.
     *
     * @param userLogin The login of the User for which the StudentExam should be created
     * @return The newly created StudentExam
     */
    public StudentExam addStudentExamForActiveExamWithUser(Course course, String userLogin) {
        User studentUser = userUtilService.getUserByLogin(userLogin);
        Exam exam = addActiveTestExamWithRegisteredUserWithoutStudentExam(course, studentUser);
        return addStudentExamWithUser(exam, studentUser, 0);
    }

    /**
     * Creates and saves a StudentExam with a registered User.
     *
     * @param exam      The Exam for which the StudentExam should be created
     * @param userLogin The login of the User for which the StudentExam should be created
     * @return The newly created StudentExam
     */
    public StudentExam addStudentExamForTestExam(Exam exam, String userLogin) {
        return addStudentExamForTestExam(exam, userUtilService.getUserByLogin(userLogin));
    }

    /**
     * Creates and saves a StudentExam with a registered User.
     *
     * @param exam The Exam for which the StudentExam should be created
     * @param user The User for which the StudentExam should be created
     * @return The newly created StudentExam
     */
    public StudentExam addStudentExamForTestExam(Exam exam, User user) {
        StudentExam studentExam = ExamFactory.generateStudentExamForTestExam(exam);
        studentExam.setUser(user);
        studentExam = studentExamRepository.save(studentExam);
        return studentExam;
    }

    /**
     * Creates and saves a StudentExam with a registered User and additional working time.
     *
     * @param exam                  The Exam for which the StudentExam should be created
     * @param user                  The User for which the StudentExam should be created
     * @param additionalWorkingTime The additional working time for the StudentExam in seconds
     * @return The newly created StudentExam
     */
    public StudentExam addStudentExamWithUser(Exam exam, User user, int additionalWorkingTime) {
        StudentExam studentExam = ExamFactory.generateStudentExam(exam);
        studentExam.setUser(user);
        studentExam.setWorkingTime(exam.getDuration() + additionalWorkingTime);
        studentExam = studentExamRepository.save(studentExam);
        return studentExam;
    }

    /**
     * Creates and saves ExerciseGroups and Exercises for the given Exam.
     * (Groups 0: text, 1: quiz, 2: file upload, 3: modeling, 4: bonus text, 5: not included text, 7 (optional): programming).
     *
     * @param exam                    The Exam for which the ExerciseGroups and Exercises should be created
     * @param withProgrammingExercise True, if a ProgrammingExercise should be added
     * @return The updated Exam
     */
    public Exam addExerciseGroupsAndExercisesToExam(Exam exam, boolean withProgrammingExercise) {
        return addExerciseGroupsAndExercisesToExam(exam, withProgrammingExercise, false);
    }

    /**
     * Creates and saves ExerciseGroups and Exercises for the given Exam
     * (Groups 0: text, 1: quiz, 2: file upload, 3: modeling, 4: bonus text, 5: not included text, 7 (optional): programming).
     *
     * @param exam                     The Exam for which the ExerciseGroups and Exercises should be created
     * @param withProgrammingExercise  True, if a ProgrammingExercise should be added
     * @param withAllQuizQuestionTypes True, if all QuizQuestionTypes should be added to the QuizExercise
     * @return The updated Exam
     */
    public Exam addExerciseGroupsAndExercisesToExam(Exam exam, boolean withProgrammingExercise, boolean withAllQuizQuestionTypes) {
        ExamFactory.generateExerciseGroup(true, exam); // text
        ExamFactory.generateExerciseGroup(true, exam); // quiz
        ExamFactory.generateExerciseGroup(true, exam); // file upload
        ExamFactory.generateExerciseGroup(true, exam); // modeling
        ExamFactory.generateExerciseGroup(true, exam); // bonus text
        ExamFactory.generateExerciseGroup(true, exam); // not included text
        exam.setNumberOfExercisesInExam(6);
        exam.setExamMaxPoints(24);
        exam = examRepository.save(exam);
        // NOTE: we have to reassign, otherwise we get problems, because the objects have changed
        var exerciseGroup0 = exam.getExerciseGroups().getFirst();
        var exerciseGroup1 = exam.getExerciseGroups().get(1);
        var exerciseGroup2 = exam.getExerciseGroups().get(2);
        var exerciseGroup3 = exam.getExerciseGroups().get(3);
        var exerciseGroup4 = exam.getExerciseGroups().get(4);
        var exerciseGroup5 = exam.getExerciseGroups().get(5);

        TextExercise textExercise1 = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup0, "Text");
        TextExercise textExercise2 = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup0, "Text");
        exerciseGroup0.setExercises(Set.of(textExercise1, textExercise2));
        exerciseRepository.save(textExercise1);
        exerciseRepository.save(textExercise2);
        QuizExercise quizExercise1;
        if (withAllQuizQuestionTypes) {
            quizExercise1 = QuizExerciseFactory.createQuizWithAllQuestionTypesForExam(exerciseGroup1, "Quiz");
        }
        else {
            quizExercise1 = QuizExerciseFactory.createQuizForExam(exerciseGroup1);
        }

        QuizExercise quizExercise2 = QuizExerciseFactory.createQuizForExam(exerciseGroup1);
        exerciseGroup1.setExercises(Set.of(quizExercise1, quizExercise2));
        exerciseRepository.save(quizExercise1);
        exerciseRepository.save(quizExercise2);

        FileUploadExercise fileUploadExercise1 = FileUploadExerciseFactory.generateFileUploadExerciseForExam("pdf", exerciseGroup2, "FileUpload");
        FileUploadExercise fileUploadExercise2 = FileUploadExerciseFactory.generateFileUploadExerciseForExam("pdf", exerciseGroup2, "FileUpload");
        exerciseGroup2.setExercises(Set.of(fileUploadExercise1, fileUploadExercise2));
        exerciseRepository.save(fileUploadExercise1);
        exerciseRepository.save(fileUploadExercise2);

        ModelingExercise modelingExercise1 = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup3, "Modeling");
        ModelingExercise modelingExercise2 = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup3, "Modeling");
        exerciseGroup3.setExercises(Set.of(modelingExercise1, modelingExercise2));
        exerciseRepository.save(modelingExercise1);
        exerciseRepository.save(modelingExercise2);

        TextExercise bonusTextExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup4);
        bonusTextExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        exerciseGroup4.setExercises(Set.of(bonusTextExercise));
        exerciseRepository.save(bonusTextExercise);

        TextExercise notIncludedTextExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup5);
        notIncludedTextExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        exerciseGroup5.setExercises(Set.of(notIncludedTextExercise));
        exerciseRepository.save(notIncludedTextExercise);

        if (withProgrammingExercise) {
            ExamFactory.generateExerciseGroup(true, exam); // programming
            exam.setNumberOfExercisesInExam(7);
            exam.setExamMaxPoints(29);
            exam = examRepository.save(exam);
            var exerciseGroup6 = exam.getExerciseGroups().get(6);
            // Programming exercises need a proper setup for 'prepare exam start' to work
            ProgrammingExercise programmingExercise1 = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup6, "Programming");
            programmingExerciseBuildConfigRepository.save(programmingExercise1.getBuildConfig());
            exerciseRepository.save(programmingExercise1);
            programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise1);
            programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise1);

            exerciseGroup6.setExercises(Set.of(programmingExercise1));
        }

        return exam;
    }

    /**
     * Creates and saves ExerciseGroups and Exercises for the given Exam.
     * (Groups 0: text, 1: modeling, 2 (optional): programming, 3 (optional): quiz).
     *
     * @param initialExam             The Exam for which the ExerciseGroups and Exercises should be created
     * @param withProgrammingExercise True, if a ProgrammingExercise should be added
     * @param withQuizExercise        True, if a QuizExercise should be added
     * @return The updated Exam
     */
    public Exam addTextModelingProgrammingExercisesToExam(Exam initialExam, boolean withProgrammingExercise, boolean withQuizExercise) {
        ExamFactory.generateExerciseGroup(true, initialExam); // text
        ExamFactory.generateExerciseGroup(true, initialExam); // modeling
        initialExam.setNumberOfExercisesInExam(2);
        var exam = examRepository.save(initialExam);
        // NOTE: we have to reassign, otherwise we get problems, because the objects have changed
        var exerciseGroup0 = exam.getExerciseGroups().getFirst();
        var exerciseGroup1 = exam.getExerciseGroups().get(1);

        TextExercise textExercise1 = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup0);
        TextExercise textExercise2 = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup0);
        exerciseGroup0.setExercises(Set.of(textExercise1, textExercise2));
        exerciseRepository.save(textExercise1);
        exerciseRepository.save(textExercise2);

        ModelingExercise modelingExercise1 = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup1);
        ModelingExercise modelingExercise2 = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup1);
        exerciseGroup1.setExercises(Set.of(modelingExercise1, modelingExercise2));
        exerciseRepository.save(modelingExercise1);
        exerciseRepository.save(modelingExercise2);

        if (withProgrammingExercise) {
            ExamFactory.generateExerciseGroup(true, exam); // programming
            exam.setNumberOfExercisesInExam(3);
            exam = examRepository.save(exam);
            var exerciseGroup2 = exam.getExerciseGroups().get(2);
            // Programming exercises need a proper setup for 'prepare exam start' to work
            ProgrammingExercise programmingExercise1 = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup2);
            programmingExerciseBuildConfigRepository.save(programmingExercise1.getBuildConfig());
            exerciseRepository.save(programmingExercise1);
            programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise1);
            programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise1);
            exerciseGroup2.setExercises(Set.of(programmingExercise1));
        }

        if (withQuizExercise) {
            ExamFactory.generateExerciseGroup(true, exam); // modeling
            exam.setNumberOfExercisesInExam(3 + (withProgrammingExercise ? 1 : 0));
            exam = examRepository.save(exam);
            var exerciseGroup3 = exam.getExerciseGroups().get(2 + (withProgrammingExercise ? 1 : 0));
            // Programming exercises need a proper setup for 'prepare exam start' to work
            QuizExercise quizExercise = QuizExerciseFactory.createQuizForExam(exerciseGroup3);
            exerciseRepository.save(quizExercise);
            exerciseGroup3.setExercises(Set.of(quizExercise));
        }
        return exam;
    }

    /**
     * Creates and saves an Exam with an ExerciseGroup for a newly created, active course with default group names.
     *
     * @param mandatory True, if the ExerciseGroup should be mandatory
     * @return The newly created ExerciseGroup
     */
    public ExerciseGroup addExerciseGroupWithExamAndCourse(boolean mandatory) {
        Course course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        Exam exam = ExamFactory.generateExam(course);
        ExerciseGroup exerciseGroup = ExamFactory.generateExerciseGroup(mandatory, exam);

        course = courseRepo.save(course);
        exam = examRepository.save(exam);

        Optional<Course> optionalCourse = courseRepo.findById(course.getId());
        assertThat(optionalCourse).as("course can be retrieved").isPresent();
        Course courseDB = optionalCourse.orElseThrow();

        Optional<Exam> optionalExam = examRepository.findById(exam.getId());
        assertThat(optionalCourse).as("exam can be retrieved").isPresent();
        Exam examDB = optionalExam.orElseThrow();

        Optional<ExerciseGroup> optionalExerciseGroup = exerciseGroupRepository.findById(exerciseGroup.getId());
        assertThat(optionalExerciseGroup).as("exerciseGroup can be retrieved").isPresent();
        ExerciseGroup exerciseGroupDB = optionalExerciseGroup.orElseThrow();

        assertThat(examDB.getCourse().getId()).as("exam and course are linked correctly").isEqualTo(courseDB.getId());
        assertThat(exerciseGroupDB.getExam().getId()).as("exerciseGroup and exam are linked correctly").isEqualTo(examDB.getId());

        return exerciseGroup;
    }

    /**
     * Creates and saves an Exam with an ExerciseGroup for a newly created, active course with default group names.
     *
     * @param mandatory                  True, if the ExerciseGroup should be mandatory
     * @param startDateBeforeCurrentTime True, if the start date of the created Exam should be before the current time, needed for examLiveEvent tests for already started exams
     * @return The newly created ExerciseGroup
     */
    public ExerciseGroup addExerciseGroupWithExamAndCourse(boolean mandatory, boolean startDateBeforeCurrentTime) {
        Course course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        Exam exam;
        if (startDateBeforeCurrentTime) {
            // Create an exam that is already started
            ZonedDateTime currentTime = now();
            exam = ExamFactory.generateExam(course, currentTime.minusMinutes(10), currentTime.minusMinutes(5), currentTime.plusMinutes(60), false);
        }
        else {
            exam = ExamFactory.generateExam(course);
        }
        ExerciseGroup exerciseGroup = ExamFactory.generateExerciseGroup(mandatory, exam);

        course = courseRepo.save(course);
        exam = examRepository.save(exam);

        Optional<Course> optionalCourse = courseRepo.findById(course.getId());
        assertThat(optionalCourse).as("course can be retrieved").isPresent();
        Course courseDB = optionalCourse.orElseThrow();

        Optional<Exam> optionalExam = examRepository.findById(exam.getId());
        assertThat(optionalCourse).as("exam can be retrieved").isPresent();
        Exam examDB = optionalExam.orElseThrow();

        Optional<ExerciseGroup> optionalExerciseGroup = exerciseGroupRepository.findById(exerciseGroup.getId());
        assertThat(optionalExerciseGroup).as("exerciseGroup can be retrieved").isPresent();
        ExerciseGroup exerciseGroupDB = optionalExerciseGroup.orElseThrow();

        assertThat(examDB.getCourse().getId()).as("exam and course are linked correctly").isEqualTo(courseDB.getId());
        assertThat(exerciseGroupDB.getExam().getId()).as("exerciseGroup and exam are linked correctly").isEqualTo(examDB.getId());

        return exerciseGroup;
    }

    /**
     * Creates and saves an Exam with an ExerciseGroup for a newly created, active course. The exam has a review date [now; now + 60min].
     *
     * @param mandatory True, if the ExerciseGroup should be mandatory
     * @return The newly created ExerciseGroup
     */
    public ExerciseGroup addExerciseGroupWithExamWithReviewDatesAndCourse(boolean mandatory) {
        Course course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        Exam exam = ExamFactory.generateExamWithStudentReviewDates(course);
        ExerciseGroup exerciseGroup = ExamFactory.generateExerciseGroup(mandatory, exam);

        course = courseRepo.save(course);
        exam = examRepository.save(exam);

        Optional<Course> optionalCourse = courseRepo.findById(course.getId());
        assertThat(optionalCourse).as("course can be retrieved").isPresent();
        Course courseDB = optionalCourse.orElseThrow();

        Optional<Exam> optionalExam = examRepository.findById(exam.getId());
        assertThat(optionalCourse).as("exam can be retrieved").isPresent();
        Exam examDB = optionalExam.orElseThrow();

        Optional<ExerciseGroup> optionalExerciseGroup = exerciseGroupRepository.findById(exerciseGroup.getId());
        assertThat(optionalExerciseGroup).as("exerciseGroup can be retrieved").isPresent();
        ExerciseGroup exerciseGroupDB = optionalExerciseGroup.orElseThrow();

        assertThat(examDB.getCourse().getId()).as("exam and course are linked correctly").isEqualTo(courseDB.getId());
        assertThat(exerciseGroupDB.getExam().getId()).as("exerciseGroup and exam are linked correctly").isEqualTo(examDB.getId());

        return exerciseGroup;
    }

    /**
     * Creates and saves an Exam with an ExerciseGroup for a newly created, active course.
     *
     * @param mandatory True, if the ExerciseGroup should be mandatory
     * @return The newly created ExerciseGroup
     */
    public ExerciseGroup createAndSaveActiveExerciseGroup(Course course, boolean mandatory) {
        Exam exam = ExamFactory.generateExam(course);
        ExerciseGroup exerciseGroup = ExamFactory.generateExerciseGroup(mandatory, exam);
        examRepository.save(exam);

        return exerciseGroup;
    }

    /**
     * Sets the visible, start and end date of the Exam. The working time gets set accordingly. Does not save the changes to the database.
     *
     * @param exam        The Exam that gets the dates set
     * @param visibleDate The new visible date of the Exam
     * @param startDate   The new start date of the Exam
     * @param endDate     The new end date of the Exam
     */
    public void setVisibleStartAndEndDateOfExam(Exam exam, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate) {
        exam.setVisibleDate(visibleDate);
        exam.setStartDate(startDate);
        exam.setEndDate(endDate);
        exam.setWorkingTime(exam.getDuration());
    }

    /**
     * Creates and saves StudentParticipations and Submissions for the given Exam. Expects the exam to have six ExerciseGroups
     * (Expected Groups 0: text, 1: quiz, 2: file upload, 3: modeling, 4: any, 5: any, 6: programming).
     *
     * @param exam          The Exam for which the StudentParticipations and Submissions should be created
     * @param studentExam   The StudentExam for which the StudentParticipations and Submissions should be created
     * @param validModel    The valid model for the modeling exercise
     * @param localRepoPath The local repository path for the programming exercise
     */
    public void addExercisesWithParticipationsAndSubmissionsToStudentExam(Exam exam, StudentExam studentExam, String validModel, URI localRepoPath) {
        var exerciseGroups = exam.getExerciseGroups();
        // text exercise
        var exercise = exerciseGroups.getFirst().getExercises().iterator().next();
        var user = studentExam.getUser();
        var participation = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, exercise, user);
        Submission submission = ParticipationFactory.generateTextSubmission("Test Submission", Language.ENGLISH, true);
        studentExam.addExercise(exercise);
        exercise.addParticipation(participation);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        studentParticipationRepo.save(participation);
        submissionRepository.save(submission);
        Result result = participationUtilService.generateResultWithScore(submission, studentExam.getUser(), 3.0);
        submission.addResult(result);
        studentParticipationRepo.save(participation);
        submissionRepository.save(submission);

        exerciseRepository.save(exercise);
        // quiz exercise
        exercise = exerciseGroups.get(1).getExercises().iterator().next();
        participation = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, exercise, user);
        submission = ParticipationFactory.generateQuizSubmission(true);
        exercise.addParticipation(participation);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        studentExam.addExercise(exercise);
        studentParticipationRepo.save(participation);
        submissionRepository.save(submission);
        result = participationUtilService.generateResultWithScore(submission, studentExam.getUser(), 3.0);
        submission.addResult(result);
        studentParticipationRepo.save(participation);
        submissionRepository.save(submission);
        exerciseRepository.save(exercise);
        // file upload
        exercise = exerciseGroups.get(2).getExercises().iterator().next();
        participation = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, exercise, user);
        submission = ParticipationFactory.generateFileUploadSubmission(true);
        exercise.addParticipation(participation);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        studentExam.addExercise(exercise);
        studentParticipationRepo.save(participation);
        submissionRepository.save(submission);
        result = participationUtilService.generateResultWithScore(submission, studentExam.getUser(), 3.0);
        submission.addResult(result);
        studentParticipationRepo.save(participation);
        submissionRepository.save(submission);
        exerciseRepository.save(exercise);
        // modeling
        exercise = exerciseGroups.get(3).getExercises().iterator().next();
        participation = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, exercise, user);
        submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        exercise.addParticipation(participation);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        studentExam.addExercise(exercise);
        studentParticipationRepo.save(participation);
        submissionRepository.save(submission);
        exerciseRepository.save(exercise);
        result = participationUtilService.generateResultWithScore(submission, studentExam.getUser(), 3.0);
        submission.addResult(result);
        studentParticipationRepo.save(participation);
        submissionRepository.save(submission);
        // programming
        exercise = exerciseGroups.get(6).getExercises().iterator().next();
        participation = participationUtilService.addStudentParticipationForProgrammingExerciseForLocalRepo((ProgrammingExercise) exercise, user.getLogin(), localRepoPath);
        submission = ParticipationFactory.generateProgrammingSubmission(true, "abc123", SubmissionType.MANUAL);
        exercise.addParticipation(participation);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        studentExam.addExercise(exercise);

        studentParticipationRepo.save(participation);
        submissionRepository.save(submission);
        result = participationUtilService.generateResultWithScore(submission, studentExam.getUser(), 3.0);
        submission.addResult(result);
        studentParticipationRepo.save(participation);
        submissionRepository.save(submission);
        exerciseRepository.save(exercise);

        studentExamRepository.save(studentExam);
    }

    /**
     * Gets the number of programming exercises in the Exam
     *
     * @param examId The id of the Exam to be searched for programming exercises
     * @return The number of programming exercises in the Exam
     */
    public int getNumberOfProgrammingExercises(Long examId) {
        Exam exam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examId);
        int count = 0;
        for (var exerciseGroup : exam.getExerciseGroups()) {
            for (var exercise : exerciseGroup.getExercises()) {
                if (exercise instanceof ProgrammingExercise) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Adds exercise to student exam
     *
     * @param studentExam student exam to which exercise should be added
     * @param exercise    exercise which should be added
     */
    public void addExerciseToStudentExam(StudentExam studentExam, Exercise exercise) {
        studentExam.addExercise(exercise);
        studentExamRepository.save(studentExam);
    }

    /**
     * Creates and saves a Course with an Exam with one mandatory ExerciseGroup with one TextExercise. The exam has a review date [now; now + 60min].
     *
     * @return The created TextExercise
     */
    public TextExercise addCourseExamWithReviewDatesExerciseGroupWithOneTextExercise() {
        ExerciseGroup exerciseGroup = addExerciseGroupWithExamWithReviewDatesAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        return exerciseRepository.save(textExercise);
    }

    /**
     * Creates and saves a Course with an Exam with one mandatory ExerciseGroup with one TextExercise.
     *
     * @param title The title of the created TextExercise
     * @return The created TextExercise
     */
    public TextExercise addCourseExamExerciseGroupWithOneTextExercise(String title) {
        ExerciseGroup exerciseGroup = addExerciseGroupWithExamAndCourse(true);
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        if (title != null) {
            textExercise.setTitle(title);
        }
        return exerciseRepository.save(textExercise);
    }

    /**
     * Creates and saves a Course with an Exam with one mandatory ExerciseGroup with one TextExercise.
     *
     * @return The created TextExercise
     */
    public TextExercise addCourseExamExerciseGroupWithOneTextExercise() {
        return addCourseExamExerciseGroupWithOneTextExercise(null);
    }

    /**
     * Creates and saves an Exam with two correction rounds configured for testing scenarios.
     *
     * @return The newly created Exam with two correction rounds
     */
    public Exam setupExamWithTwoCorrectionRounds() {
        Course course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        Exam exam = addExam(course);
        exam.setNumberOfCorrectionRoundsInExam(2);
        exam.setStartDate(ZonedDateTime.now().minusHours(2));
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        exam.setPublishResultsDate(ZonedDateTime.now().minusMinutes(30));
        exam.setExamStudentReviewStart(ZonedDateTime.now().minusMinutes(20));
        exam.setExamStudentReviewEnd(ZonedDateTime.now().plusDays(1));
        exam.setVisibleDate(ZonedDateTime.now().minusHours(3));
        exam.setWorkingTime(3600); // 1 hour
        return examRepository.save(exam);
    }

    /**
     * Sets up a programming exercise with second correction enabled for the given exam.
     *
     * @param exam The exam to add the programming exercise to
     * @return The configured programming exercise
     */
    public ProgrammingExercise setupProgrammingExerciseWithSecondCorrection(Exam exam) {
        exam = addExerciseGroupsAndExercisesToExam(exam, true);
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().get(6);
        ProgrammingExercise programmingExercise = (ProgrammingExercise) exerciseGroup.getExercises().iterator().next();
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExercise.setSecondCorrectionEnabled(true);
        programmingExercise.setMaxPoints(10.0);
        return exerciseRepository.save(programmingExercise);
    }

    /**
     * Sets up a student exam with submission state for the given exam, programming exercise, and student.
     *
     * @param exam                The exam
     * @param programmingExercise The programming exercise
     * @param student             The student
     * @return The configured student exam
     */
    public StudentExam setupStudentExamWithSubmission(Exam exam, ProgrammingExercise programmingExercise, User student) {
        StudentExam studentExam = addStudentExamWithUser(exam, student);
        studentExam.setSubmitted(true);
        studentExam.setWorkingTime(exam.getWorkingTime());
        studentExam.setStartedAndStartDate(ZonedDateTime.now().minusHours(2));
        studentExam.setSubmissionDate(ZonedDateTime.now().minusHours(1).minusMinutes(30));
        studentExam.addExercise(programmingExercise);
        return studentExamRepository.save(studentExam);
    }

}
