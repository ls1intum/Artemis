package de.tum.in.www1.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.exam.*;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
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
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationRepository;
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

    public Course createCourseWithExamAndExerciseGroupAndExercises(User user, ZonedDateTime visible, ZonedDateTime start, ZonedDateTime end) {
        Course course = courseUtilService.createCourse();
        Exam exam = addExam(course, user, visible, start, end);
        course.addExam(exam);
        addExerciseGroupsAndExercisesToExam(exam, false);
        return courseRepo.save(course);
    }

    public Course createCourseWithExamAndExerciseGroupAndExercises(User user) {
        Course course = courseUtilService.createCourse();
        Exam exam = addExam(course, user, ZonedDateTime.now().minusMinutes(1), ZonedDateTime.now(), ZonedDateTime.now().plusMinutes(1));
        course.addExam(exam);
        addExerciseGroupsAndExercisesToExam(exam, false);
        return courseRepo.save(course);
    }

    public StudentExam setupTestRunForExamWithExerciseGroupsForInstructor(Exam exam, User instructor, List<ExerciseGroup> exerciseGroupsWithExercises) {
        List<Exercise> exercises = new ArrayList<>();
        exerciseGroupsWithExercises.forEach(exerciseGroup -> exercises.add(exerciseGroup.getExercises().iterator().next()));
        var testRun = generateTestRunForInstructor(exam, instructor, exercises);
        return studentExamRepository.save(testRun);
    }

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

    public Exam registerUsersForExamAndSaveExam(Exam exam, String userPrefix, int numberOfStudents) {
        return registerUsersForExamAndSaveExam(exam, userPrefix, 1, numberOfStudents);
    }

    /**
     * registers students for exam and saves the exam in the repository
     *
     * @param exam       exam to which students should be registered to
     * @param userPrefix prefix of the users
     * @param from       index of the first student to be registered
     * @param to         index of the last student to be registered
     * @return exam that was saved in the repository
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

    public Exam addExam(Course course) {
        Exam exam = ExamFactory.generateExam(course);
        return examRepository.save(exam);
    }

    public Exam addTestExam(Course course) {
        Exam exam = ExamFactory.generateTestExam(course);
        return examRepository.save(exam);
    }

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

    public Exam addExam(Course course, User user, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate) {
        Exam exam = ExamFactory.generateExam(course);
        exam = examRepository.save(exam);
        var registeredExamUser = new ExamUser();
        registeredExamUser.setUser(user);
        registeredExamUser.setExam(exam);
        registeredExamUser = examUserRepository.save(registeredExamUser);
        exam.addExamUser(registeredExamUser);
        exam.setVisibleDate(visibleDate);
        exam.setStartDate(startDate);
        exam.setEndDate(endDate);
        exam.setNumberOfCorrectionRoundsInExam(1);
        examRepository.save(exam);
        return exam;
    }

    public Exam addExamWithExerciseGroup(Course course, boolean mandatory) {
        Exam exam = ExamFactory.generateExam(course);
        ExamFactory.generateExerciseGroup(mandatory, exam);
        exam = examRepository.save(exam);
        return exam;
    }

    public Exam addTestExamWithExerciseGroup(Course course, boolean mandatory) {
        Exam exam = ExamFactory.generateTestExam(course);
        ExamFactory.generateExerciseGroup(mandatory, exam);
        exam = examRepository.save(exam);
        return exam;
    }

    public Exam addExam(Course course, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate) {
        Exam exam = ExamFactory.generateExam(course);
        exam.setVisibleDate(visibleDate);
        exam.setStartDate(startDate);
        exam.setEndDate(endDate);
        exam.setWorkingTime((int) Duration.between(startDate, endDate).toSeconds());
        exam.setGracePeriod(180);
        exam = examRepository.save(exam);
        return exam;
    }

    public Exam addExam(Course course, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate, ZonedDateTime publishResultDate) {
        Exam exam = ExamFactory.generateExam(course);
        exam.setVisibleDate(visibleDate);
        exam.setStartDate(startDate);
        exam.setEndDate(endDate);
        exam.setPublishResultsDate(publishResultDate);
        exam.setWorkingTime((int) Duration.between(startDate, endDate).toSeconds());
        exam.setGracePeriod(180);
        exam = examRepository.save(exam);
        return exam;
    }

    public Channel addExamChannel(Exam exam, String channelName) {
        Channel channel = new Channel();
        channel.setCourse(exam.getCourse());
        channel.setName(channelName);
        channel.setIsPublic(true);
        channel.setIsAnnouncementChannel(false);
        channel.setIsArchived(false);
        channel.setDescription("Test channel");
        channel.setExam(exam);
        channel = conversationRepository.save(channel);
        exam.setChannelName(channelName);
        return channel;
    }

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
        studentExam.setWorkingTime((int) Duration.between(exam.getStartDate(), exam.getEndDate()).toSeconds());
        studentExamRepository.save(studentExam);
        return exam;
    }

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

    public Exam addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(Course course) {
        Exam exam = addExam(course);
        for (int i = 0; i <= 4; i++) {
            ExamFactory.generateExerciseGroup(true, exam);
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

    public StudentExam addStudentExam(Exam exam) {
        StudentExam studentExam = ExamFactory.generateStudentExam(exam);
        studentExam = studentExamRepository.save(studentExam);
        return studentExam;
    }

    public StudentExam addStudentExamWithUser(Exam exam, String user) {
        return addStudentExamWithUser(exam, userRepo.findOneByLogin(user).orElseThrow());
    }

    public StudentExam addStudentExamWithUser(Exam exam, User user) {
        StudentExam studentExam = ExamFactory.generateStudentExam(exam);
        studentExam.setUser(user);
        studentExam = studentExamRepository.save(studentExam);
        return studentExam;
    }

    public StudentExam addStudentExamForActiveExamWithUser(String user) {
        Course course = courseUtilService.addEmptyCourse();
        User studentUser = userUtilService.getUserByLogin(user);
        Exam exam = addActiveTestExamWithRegisteredUserWithoutStudentExam(course, studentUser);
        return addStudentExamWithUser(exam, studentUser, 0);
    }

    public StudentExam addStudentExamForTestExam(Exam exam, User user) {
        StudentExam studentExam = ExamFactory.generateStudentExamForTestExam(exam);
        studentExam.setUser(user);
        studentExam = studentExamRepository.save(studentExam);
        return studentExam;
    }

    public StudentExam addStudentExamWithUser(Exam exam, User user, int additionalWorkingTime) {
        StudentExam studentExam = ExamFactory.generateStudentExam(exam);
        studentExam.setUser(user);
        studentExam.setWorkingTime((int) Duration.between(exam.getStartDate(), exam.getEndDate()).toSeconds() + additionalWorkingTime);
        studentExam = studentExamRepository.save(studentExam);
        return studentExam;
    }

    public Exam addExerciseGroupsAndExercisesToExam(Exam exam, boolean withProgrammingExercise) {
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

        TextExercise textExercise1 = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup0);
        TextExercise textExercise2 = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup0);
        exerciseGroup0.setExercises(Set.of(textExercise1, textExercise2));
        exerciseRepo.save(textExercise1);
        exerciseRepo.save(textExercise2);

        QuizExercise quizExercise1 = quizExerciseUtilService.createQuizForExam(exerciseGroup1);
        QuizExercise quizExercise2 = quizExerciseUtilService.createQuizForExam(exerciseGroup1);
        exerciseGroup1.setExercises(Set.of(quizExercise1, quizExercise2));
        exerciseRepo.save(quizExercise1);
        exerciseRepo.save(quizExercise2);

        FileUploadExercise fileUploadExercise1 = FileUploadExerciseFactory.generateFileUploadExerciseForExam("pdf", exerciseGroup2);
        FileUploadExercise fileUploadExercise2 = FileUploadExerciseFactory.generateFileUploadExerciseForExam("pdf", exerciseGroup2);
        exerciseGroup2.setExercises(Set.of(fileUploadExercise1, fileUploadExercise2));
        exerciseRepo.save(fileUploadExercise1);
        exerciseRepo.save(fileUploadExercise2);

        ModelingExercise modelingExercise1 = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup3);
        ModelingExercise modelingExercise2 = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup3);
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
            ProgrammingExercise programmingExercise1 = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup6);
            exerciseRepo.save(programmingExercise1);
            programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise1);
            programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise1);

            exerciseGroup6.setExercises(Set.of(programmingExercise1));
        }

        return exam;
    }

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
            QuizExercise quizExercise = quizExerciseUtilService.createQuizForExam(exerciseGroup3);
            exerciseRepo.save(quizExercise);
            exerciseGroup3.setExercises(Set.of(quizExercise));
        }
        return exam;
    }

    public ExerciseGroup addExerciseGroupWithExamAndCourse(boolean mandatory) {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        Exam exam = ExamFactory.generateExam(course);
        ExerciseGroup exerciseGroup = ExamFactory.generateExerciseGroup(mandatory, exam);

        course = courseRepo.save(course);
        exam = examRepository.save(exam);

        Optional<Course> optionalCourse = courseRepo.findById(course.getId());
        assertThat(optionalCourse).as("course can be retrieved").isPresent();
        Course courseDB = optionalCourse.get();

        Optional<Exam> optionalExam = examRepository.findById(exam.getId());
        assertThat(optionalCourse).as("exam can be retrieved").isPresent();
        Exam examDB = optionalExam.get();

        Optional<ExerciseGroup> optionalExerciseGroup = exerciseGroupRepository.findById(exerciseGroup.getId());
        assertThat(optionalExerciseGroup).as("exerciseGroup can be retrieved").isPresent();
        ExerciseGroup exerciseGroupDB = optionalExerciseGroup.get();

        assertThat(examDB.getCourse().getId()).as("exam and course are linked correctly").isEqualTo(courseDB.getId());
        assertThat(exerciseGroupDB.getExam().getId()).as("exerciseGroup and exam are linked correctly").isEqualTo(examDB.getId());

        return exerciseGroup;
    }

    public ExerciseGroup addExerciseGroupWithExamWithReviewDatesAndCourse(boolean mandatory) {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        Exam exam = ExamFactory.generateExamWithStudentReviewDates(course);
        ExerciseGroup exerciseGroup = ExamFactory.generateExerciseGroup(mandatory, exam);

        course = courseRepo.save(course);
        exam = examRepository.save(exam);

        Optional<Course> optionalCourse = courseRepo.findById(course.getId());
        assertThat(optionalCourse).as("course can be retrieved").isPresent();
        Course courseDB = optionalCourse.get();

        Optional<Exam> optionalExam = examRepository.findById(exam.getId());
        assertThat(optionalCourse).as("exam can be retrieved").isPresent();
        Exam examDB = optionalExam.get();

        Optional<ExerciseGroup> optionalExerciseGroup = exerciseGroupRepository.findById(exerciseGroup.getId());
        assertThat(optionalExerciseGroup).as("exerciseGroup can be retrieved").isPresent();
        ExerciseGroup exerciseGroupDB = optionalExerciseGroup.get();

        assertThat(examDB.getCourse().getId()).as("exam and course are linked correctly").isEqualTo(courseDB.getId());
        assertThat(exerciseGroupDB.getExam().getId()).as("exerciseGroup and exam are linked correctly").isEqualTo(examDB.getId());

        return exerciseGroup;
    }

    /**
     * creates and saves an exam exercise group in a course that is currently active.
     *
     * @param mandatory if the exerciseGroup is mandatory
     * @return exercise group created
     */
    public ExerciseGroup createAndSaveActiveExerciseGroup(boolean mandatory) {
        Course course = courseUtilService.createAndSaveCourse(1L, pastTimestamp, futureFutureTimestamp, Set.of());
        Exam exam = ExamFactory.generateExam(course);
        ExerciseGroup exerciseGroup = ExamFactory.generateExerciseGroup(mandatory, exam);
        examRepository.save(exam);

        return exerciseGroup;
    }
}
