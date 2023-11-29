package de.tum.in.www1.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.*;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizPool;
import de.tum.in.www1.artemis.exercise.fileuploadexercise.FileUploadExerciseFactory;
import de.tum.in.www1.artemis.exercise.fileuploadexercise.FileUploadExerciseUtilService;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseFactory;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseFactory;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseFactory;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.post.ConversationFactory;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationRepository;
import de.tum.in.www1.artemis.service.QuizPoolService;
import de.tum.in.www1.artemis.user.UserUtilService;

/**
 * Service responsible for initializing the database with specific testdata related to exams for use in integration tests.
 */
@Service
public class ExamUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepo;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ExamSessionRepository examSessionRepository;

    @Autowired
    private QuizPoolService quizPoolService;

    /**
     * Creates and saves a course with an exam and an exercise group with all exercise types excluding programming exercises.
     *
     * @param user    The User who should be registered for the Exam
     * @param visible The visible date of the Exam
     * @param start   The start date of the Exam
     * @param end     The end date of the Exam
     * @return The newly created course
     */
    public Course createCourseWithExamAndExerciseGroupAndExercises(User user, ZonedDateTime visible, ZonedDateTime start, ZonedDateTime end) {
        Course course = courseUtilService.createCourse();
        Exam exam = addExamWithUser(course, user, false, visible, start, end);
        course.addExam(exam);
        addExerciseGroupsAndExercisesToExam(exam, false);
        return courseRepo.save(course);
    }

    /**
     * Creates and saves a course with an exam and an exercise group with all exercise types excluding programming exercises. Sets the visible (now - 1 min), start (now) and end
     * (now + 1 min) date with default values.
     *
     * @param user The User who should be registered for the Exam
     * @return The newly created course
     */
    public Course createCourseWithExamAndExerciseGroupAndExercises(User user) {
        Course course = courseUtilService.createCourse();
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
            if (exercise instanceof ModelingExercise modelingExercise) {
                submission = modelingExerciseUtilService.addModelingSubmission(modelingExercise, ParticipationFactory.generateModelingSubmission("", false), instructor.getLogin());
            }
            else if (exercise instanceof TextExercise textExercise) {
                submission = textExerciseUtilService.saveTextSubmission(textExercise, ParticipationFactory.generateTextSubmission("", null, false), instructor.getLogin());
            }
            else if (exercise instanceof QuizExercise quizExercise) {
                submission = quizExerciseUtilService.saveQuizSubmission(quizExercise, ParticipationFactory.generateQuizSubmission(false), instructor.getLogin());
            }
            else if (exercise instanceof ProgrammingExercise programmingExercise) {
                submission = new ProgrammingSubmission().submitted(true);
                programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, (ProgrammingSubmission) submission, instructor.getLogin());
                submission = submissionRepository.save(submission);
            }
            else if (exercise instanceof FileUploadExercise fileUploadExercise) {
                submission = fileUploadExerciseUtilService.saveFileUploadSubmission(fileUploadExercise, ParticipationFactory.generateFileUploadSubmission(false),
                        instructor.getLogin());
            }
            var studentParticipation = (StudentParticipation) submission.getParticipation();
            studentParticipation.setTestRun(true);
            studentParticipationRepo.save(studentParticipation);
        }
        return testRun;
    }

    /**
     * Creates and saves an Exam with one mandatory ExerciseGroup with three TextExercises.
     *
     * @param course The Course to which the Exam should be added
     * @return The newly created Exam
     */
    public Exam setupSimpleExamWithExerciseGroupExercise(Course course) {
        var exam = ExamFactory.generateExam(course);
        exam.setNumberOfExercisesInExam(1);
        exam.setRandomizeExerciseOrder(true);
        exam.setStartDate(ZonedDateTime.now().plusHours(2));
        exam.setEndDate(ZonedDateTime.now().plusHours(4));
        exam.setExamMaxPoints(20);
        exam = examRepository.save(exam);

        // add exercise group: 1 mandatory
        ExamFactory.generateExerciseGroup(true, exam);
        exam = examRepository.save(exam);

        // add exercises
        var exercise1a = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        var exercise1b = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        var exercise1c = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        exerciseRepo.saveAll(List.of(exercise1a, exercise1b, exercise1c));

        return examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(exam.getId());
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
        var exercise1a = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        var exercise1b = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        var exercise1c = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(0));
        exerciseRepo.saveAll(List.of(exercise1a, exercise1b, exercise1c));

        var exercise2a = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(1));
        var exercise2b = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(1));
        var exercise2c = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(1));
        exerciseRepo.saveAll(List.of(exercise2a, exercise2b, exercise2c));

        var exercise3a = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(2));
        var exercise3b = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(2));
        var exercise3c = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(2));
        exerciseRepo.saveAll(List.of(exercise3a, exercise3b, exercise3c));

        var exercise4a = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(3));
        var exercise4b = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(3));
        var exercise4c = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(3));
        exerciseRepo.saveAll(List.of(exercise4a, exercise4b, exercise4c));

        var exercise5a = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(4));
        var exercise5b = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(4));
        var exercise5c = TextExerciseFactory.generateTextExerciseForExam(exam.getExerciseGroups().get(4));
        exerciseRepo.saveAll(List.of(exercise5a, exercise5b, exercise5c));

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

        ExerciseGroup modellingGroup = exam.getExerciseGroups().get(0);
        Exercise modelling = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, modellingGroup);
        modellingGroup.addExercise(modelling);
        exerciseRepo.save(modelling);

        ExerciseGroup textGroup = exam.getExerciseGroups().get(1);
        Exercise text = TextExerciseFactory.generateTextExerciseForExam(textGroup);
        textGroup.addExercise(text);
        exerciseRepo.save(text);

        ExerciseGroup fileUploadGroup = exam.getExerciseGroups().get(2);
        Exercise fileUpload = FileUploadExerciseFactory.generateFileUploadExerciseForExam("png", fileUploadGroup);
        fileUploadGroup.addExercise(fileUpload);
        exerciseRepo.save(fileUpload);

        ExerciseGroup quizGroup = exam.getExerciseGroups().get(3);
        Exercise quiz = QuizExerciseFactory.generateQuizExerciseForExam(quizGroup);
        quizGroup.addExercise(quiz);
        exerciseRepo.save(quiz);

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

    /**
     * Creates and saves a StudentExam for the given Exam and User.
     *
     * @param exam The Exam for which the StudentExam should be created
     * @param user The User for which the StudentExam should be created
     * @return The newly created StudentExam
     */
    public StudentExam addStudentExamWithUser(Exam exam, User user) {
        StudentExam studentExam = ExamFactory.generateStudentExam(exam);
        studentExam.setUser(user);
        studentExam.setWorkingTime(exam.getDuration());
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
     * @return The ExamSession that was added to the student exam
     */
    public ExamSession addExamSessionToStudentExam(StudentExam studentExam, String sessionToken, String ipAddress, String browserFingerprint, String instanceId, String userAgent) {
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
        return examSession;
    }

    /**
     * Creates and saves a StudentExam with a registered User. Creates and saves an empty Course first. Then, given the new Course and User, creates and saves an Exam that
     * started 1 hour ago and ends in 1 hour. The Exam is then used to create the StudentExam.
     *
     * @param userLogin The login of the User for which the StudentExam should be created
     * @return The newly created StudentExam
     */
    public StudentExam addStudentExamForActiveExamWithUser(String userLogin) {
        Course course = courseUtilService.addEmptyCourse();
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
        var exerciseGroup0 = exam.getExerciseGroups().get(0);
        var exerciseGroup1 = exam.getExerciseGroups().get(1);
        var exerciseGroup2 = exam.getExerciseGroups().get(2);
        var exerciseGroup3 = exam.getExerciseGroups().get(3);
        var exerciseGroup4 = exam.getExerciseGroups().get(4);
        var exerciseGroup5 = exam.getExerciseGroups().get(5);

        TextExercise textExercise1 = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup0, "Text");
        TextExercise textExercise2 = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup0, "Text");
        exerciseGroup0.setExercises(Set.of(textExercise1, textExercise2));
        exerciseRepo.save(textExercise1);
        exerciseRepo.save(textExercise2);
        QuizExercise quizExercise1;
        if (withAllQuizQuestionTypes) {
            quizExercise1 = QuizExerciseFactory.createQuizWithAllQuestionTypesForExam(exerciseGroup1, "Quiz");
        }
        else {
            quizExercise1 = QuizExerciseFactory.createQuizForExam(exerciseGroup1);
        }

        QuizExercise quizExercise2 = QuizExerciseFactory.createQuizForExam(exerciseGroup1);
        exerciseGroup1.setExercises(Set.of(quizExercise1, quizExercise2));
        exerciseRepo.save(quizExercise1);
        exerciseRepo.save(quizExercise2);

        FileUploadExercise fileUploadExercise1 = FileUploadExerciseFactory.generateFileUploadExerciseForExam("pdf", exerciseGroup2, "FileUpload");
        FileUploadExercise fileUploadExercise2 = FileUploadExerciseFactory.generateFileUploadExerciseForExam("pdf", exerciseGroup2, "FileUpload");
        exerciseGroup2.setExercises(Set.of(fileUploadExercise1, fileUploadExercise2));
        exerciseRepo.save(fileUploadExercise1);
        exerciseRepo.save(fileUploadExercise2);

        ModelingExercise modelingExercise1 = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup3, "Modeling");
        ModelingExercise modelingExercise2 = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup3, "Modeling");
        exerciseGroup3.setExercises(Set.of(modelingExercise1, modelingExercise2));
        exerciseRepo.save(modelingExercise1);
        exerciseRepo.save(modelingExercise2);

        TextExercise bonusTextExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup4);
        bonusTextExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        exerciseGroup4.setExercises(Set.of(bonusTextExercise));
        exerciseRepo.save(bonusTextExercise);

        TextExercise notIncludedTextExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup5);
        notIncludedTextExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        exerciseGroup5.setExercises(Set.of(notIncludedTextExercise));
        exerciseRepo.save(notIncludedTextExercise);

        if (withProgrammingExercise) {
            ExamFactory.generateExerciseGroup(true, exam); // programming
            exam.setNumberOfExercisesInExam(7);
            exam.setExamMaxPoints(29);
            exam = examRepository.save(exam);
            var exerciseGroup6 = exam.getExerciseGroups().get(6);
            // Programming exercises need a proper setup for 'prepare exam start' to work
            ProgrammingExercise programmingExercise1 = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup6, "Programming");
            exerciseRepo.save(programmingExercise1);
            programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise1);
            programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise1);

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
        var exerciseGroup0 = exam.getExerciseGroups().get(0);
        var exerciseGroup1 = exam.getExerciseGroups().get(1);

        TextExercise textExercise1 = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup0);
        TextExercise textExercise2 = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup0);
        exerciseGroup0.setExercises(Set.of(textExercise1, textExercise2));
        exerciseRepo.save(textExercise1);
        exerciseRepo.save(textExercise2);

        ModelingExercise modelingExercise1 = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup1);
        ModelingExercise modelingExercise2 = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup1);
        exerciseGroup1.setExercises(Set.of(modelingExercise1, modelingExercise2));
        exerciseRepo.save(modelingExercise1);
        exerciseRepo.save(modelingExercise2);

        if (withProgrammingExercise) {
            ExamFactory.generateExerciseGroup(true, exam); // programming
            exam.setNumberOfExercisesInExam(3);
            exam = examRepository.save(exam);
            var exerciseGroup2 = exam.getExerciseGroups().get(2);
            // Programming exercises need a proper setup for 'prepare exam start' to work
            ProgrammingExercise programmingExercise1 = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup2);
            exerciseRepo.save(programmingExercise1);
            programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise1);
            programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise1);
            exerciseGroup2.setExercises(Set.of(programmingExercise1));
        }

        if (withQuizExercise) {
            ExamFactory.generateExerciseGroup(true, exam); // modeling
            exam.setNumberOfExercisesInExam(3 + (withProgrammingExercise ? 1 : 0));
            exam = examRepository.save(exam);
            var exerciseGroup3 = exam.getExerciseGroups().get(2 + (withProgrammingExercise ? 1 : 0));
            // Programming exercises need a proper setup for 'prepare exam start' to work
            QuizExercise quizExercise = QuizExerciseFactory.createQuizForExam(exerciseGroup3);
            exerciseRepo.save(quizExercise);
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
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
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
     * Creates and saves an Exam with an ExerciseGroup for a newly created, active course. The exam has a review date [now; now + 60min].
     *
     * @param mandatory True, if the ExerciseGroup should be mandatory
     * @return The newly created ExerciseGroup
     */
    public ExerciseGroup addExerciseGroupWithExamWithReviewDatesAndCourse(boolean mandatory) {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
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
    public ExerciseGroup createAndSaveActiveExerciseGroup(boolean mandatory) {
        Course course = courseUtilService.createAndSaveCourse(1L, pastTimestamp, futureFutureTimestamp, Set.of());
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
     * @return The updated StudentExam
     */
    public StudentExam addExercisesWithParticipationsAndSubmissionsToStudentExam(Exam exam, StudentExam studentExam, String validModel, URI localRepoPath) {
        var exerciseGroups = exam.getExerciseGroups();
        // text exercise
        var exercise = exerciseGroups.get(0).getExercises().iterator().next();
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
        participation.addResult(result);
        studentParticipationRepo.save(participation);
        submissionRepository.save(submission);

        exerciseRepo.save(exercise);
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
        participation.addResult(result);
        studentParticipationRepo.save(participation);
        submissionRepository.save(submission);
        exerciseRepo.save(exercise);
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
        participation.addResult(result);
        studentParticipationRepo.save(participation);
        submissionRepository.save(submission);
        exerciseRepo.save(exercise);
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
        exerciseRepo.save(exercise);
        result = participationUtilService.generateResultWithScore(submission, studentExam.getUser(), 3.0);
        submission.addResult(result);
        participation.addResult(result);
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
        participation.addResult(result);
        studentParticipationRepo.save(participation);
        submissionRepository.save(submission);
        exerciseRepo.save(exercise);

        return studentExamRepository.save(studentExam);
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
     * Creates and saves an Exam with a quiz pool
     *
     * @param course course in which the exam belongs to
     * @return Exam with a quiz pool
     */
    public Exam addExamWithQuizPool(Course course) {
        Exam exam = addExam(course);
        QuizPool quizPool = new QuizPool();
        quizPool.setExam(exam);
        quizPoolService.save(quizPool);
        return exam;
    }
}
