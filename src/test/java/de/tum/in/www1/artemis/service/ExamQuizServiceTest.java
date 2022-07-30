package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.exam.StudentExamService;
import de.tum.in.www1.artemis.util.ModelFactory;

class ExamQuizServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private StudentExamService studentExamService;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private QuizSubmissionRepository quizSubmissionRepository;

    @Autowired
    private ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    private QuizExerciseService quizExerciseService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    private QuizExercise quizExercise;

    private Course course;

    private Exam exam;

    private ExerciseGroup exerciseGroup;

    private List<User> users;

    private final int numberOfParticipants = 12;

    @BeforeEach
    void init() {
        users = database.addUsers(numberOfParticipants, 1, 0, 1);
        course = database.addEmptyCourse();
        exam = database.addExamWithExerciseGroup(course, true);
        exam.setStartDate(ZonedDateTime.now().minusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(1));
        exam.setWorkingTime(2 * 60 * 60);
        exam.setNumberOfExercisesInExam(1);
        exerciseGroup = exam.getExerciseGroups().get(0);

        quizExercise = database.createQuizForExam(exerciseGroup);
        exerciseGroup.addExercise(quizExercise);

        // Add an instructor who is not in the course
        userRepository.save(ModelFactory.generateActivatedUser("instructor6"));
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void evaluateQuiz_asTutor_PreAuth_forbidden() throws Exception {
        evaluateQuiz_authorization_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void evaluateQuiz_asStudent_PreAuth_forbidden() throws Exception {
        evaluateQuiz_authorization_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor6", roles = "INSTRUCTOR")
    void evaluateQuiz_asInstructorNotInCourse_forbidden() throws Exception {
        evaluateQuiz_authorization_forbidden();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void evaluateQuiz_authorization_forbidden() throws Exception {
        exam = examRepository.save(exam);
        exerciseGroup = exerciseGroupRepository.save(exerciseGroup);
        quizExercise = quizExerciseService.save(quizExercise);

        request.postWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises", Optional.empty(), Integer.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void evaluateQuiz_testExam_forbidden() throws Exception {
        exam.setTestExam(true);
        exam.setEndDate(ZonedDateTime.now().minusMinutes(30));
        exam = examRepository.save(exam);
        exerciseGroup = exerciseGroupRepository.save(exerciseGroup);
        quizExercise = quizExerciseService.save(quizExercise);

        request.postWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises", Optional.empty(), Integer.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void evaluateQuiz_notOver_badRequest() throws Exception {
        exam = examRepository.save(exam);
        exerciseGroup = exerciseGroupRepository.save(exerciseGroup);
        quizExercise = quizExerciseService.save(quizExercise);

        request.postWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises", Optional.empty(), Integer.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void evaluateQuiz() throws Exception {
        for (int i = 0; i < numberOfParticipants; i++) {
            exam.addRegisteredUser(users.get(i));
        }

        exam = examRepository.save(exam);
        exerciseGroup.setExam(exam);
        exerciseGroup = exerciseGroupRepository.save(exerciseGroup);
        exam.setExerciseGroups(List.of(exerciseGroup));
        quizExercise.setExerciseGroup(exerciseGroup);
        quizExercise = quizExerciseService.save(quizExercise);
        exerciseGroup.setExercises(Set.of(quizExercise));

        assertThat(studentExamRepository.generateStudentExams(exam)).hasSize(numberOfParticipants);
        assertThat(studentExamRepository.findByExamId(exam.getId())).hasSize(numberOfParticipants);
        assertThat(studentExamService.startExercises(exam.getId()).join()).isEqualTo(numberOfParticipants);

        for (int i = 0; i < numberOfParticipants; i++) {
            database.changeUser("student" + (i + 1));
            QuizSubmission quizSubmission = database.generateSubmissionForThreeQuestions(quizExercise, i + 1, true, ZonedDateTime.now());
            request.put("/api/exercises/" + quizExercise.getId() + "/submissions/exam", quizSubmission, HttpStatus.OK);
        }

        database.changeUser("instructor1");
        // All exams should be over before evaluation
        for (StudentExam studentExam : studentExamRepository.findByExamId(exam.getId())) {
            studentExam.setWorkingTime(0);
            studentExamRepository.save(studentExam);
        }

        Integer numberOfEvaluatedExercises = request.postWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises",
                Optional.empty(), Integer.class, HttpStatus.OK);

        assertThat(numberOfEvaluatedExercises).isEqualTo(1);

        checkStatistics(quizExercise);

        studentExamRepository.deleteAll();

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK);

        userRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void evaluateQuizWithNoSubmissions() throws Exception {
        for (int i = 0; i < numberOfParticipants; i++) {
            exam.addRegisteredUser(users.get(i));
        }

        exam = examRepository.save(exam);
        exerciseGroup.setExam(exam);
        exerciseGroup = exerciseGroupRepository.save(exerciseGroup);
        exam.setExerciseGroups(List.of(exerciseGroup));
        quizExercise.setExerciseGroup(exerciseGroup);
        quizExercise = quizExerciseService.save(quizExercise);
        exerciseGroup.setExercises(Set.of(quizExercise));

        assertThat(studentExamRepository.generateStudentExams(exam)).hasSize(numberOfParticipants);
        assertThat(studentExamRepository.findByExamId(exam.getId())).hasSize(numberOfParticipants);

        // add participations with no submissions
        for (int i = 0; i < numberOfParticipants; i++) {
            final var user = database.getUserByLogin("student" + (i + 1));
            var participation = new StudentParticipation();
            participation.setExercise(quizExercise);
            participation.setParticipant(user);
            participation.setInitializationDate(ZonedDateTime.now());
            participation.setInitializationState(InitializationState.INITIALIZED);
            studentParticipationRepository.save(participation);
        }

        database.changeUser("instructor1");
        // All exams should be over before evaluation
        for (StudentExam studentExam : studentExamRepository.findByExamId(exam.getId())) {
            studentExam.setWorkingTime(0);
            studentExamRepository.save(studentExam);
        }

        Integer numberOfEvaluatedExercises = request.postWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises",
                Optional.empty(), Integer.class, HttpStatus.OK);

        assertThat(numberOfEvaluatedExercises).isEqualTo(1);

        studentExamRepository.deleteAll();

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK);

        userRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void evaluateQuizWithMultipleSubmissions() throws Exception {
        for (int i = 0; i < numberOfParticipants; i++) {
            exam.addRegisteredUser(users.get(i));
        }

        exam = examRepository.save(exam);
        exerciseGroup.setExam(exam);
        exerciseGroup = exerciseGroupRepository.save(exerciseGroup);
        exam.setExerciseGroups(List.of(exerciseGroup));
        quizExercise.setExerciseGroup(exerciseGroup);
        quizExercise = quizExerciseService.save(quizExercise);
        exerciseGroup.setExercises(Set.of(quizExercise));

        assertThat(studentExamRepository.generateStudentExams(exam)).hasSize(numberOfParticipants);
        assertThat(studentExamRepository.findByExamId(exam.getId())).hasSize(numberOfParticipants);
        assertThat(studentExamService.startExercises(exam.getId()).join()).isEqualTo(numberOfParticipants);

        for (int i = 0; i < numberOfParticipants; i++) {
            final var user = database.getUserByLogin("student" + (i + 1));
            database.changeUser(user.getLogin());
            QuizSubmission quizSubmission = database.generateSubmissionForThreeQuestions(quizExercise, i + 1, true, ZonedDateTime.now());
            request.put("/api/exercises/" + quizExercise.getId() + "/submissions/exam", quizSubmission, HttpStatus.OK);

            // add another submission manually to trigger multiple submission branch of evaluateQuizSubmission
            final var studentParticipation = studentParticipationRepository.findWithEagerLegalSubmissionsByExerciseIdAndStudentLogin(quizExercise.getId(), user.getLogin()).get();
            QuizSubmission quizSubmission2 = database.generateSubmissionForThreeQuestions(quizExercise, i + 1, true, ZonedDateTime.now());
            quizSubmission2.setParticipation(studentParticipation);
            quizSubmissionRepository.save(quizSubmission2);
        }

        database.changeUser("instructor1");
        // All exams should be over before evaluation
        for (StudentExam studentExam : studentExamRepository.findByExamId(exam.getId())) {
            studentExam.setWorkingTime(0);
            studentExamRepository.save(studentExam);
        }

        Integer numberOfEvaluatedExercises = request.postWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises",
                Optional.empty(), Integer.class, HttpStatus.OK);

        assertThat(numberOfEvaluatedExercises).isEqualTo(1);

        checkStatistics(quizExercise);

        studentExamRepository.deleteAll();

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK);

        userRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void evaluateQuiz_twice() throws Exception {
        for (int i = 0; i < numberOfParticipants; i++) {
            exam.addRegisteredUser(users.get(i));
        }

        exam = examRepository.save(exam);
        exerciseGroup.setExam(exam);
        exerciseGroup = exerciseGroupRepository.save(exerciseGroup);
        exam.setExerciseGroups(List.of(exerciseGroup));
        quizExercise.setExerciseGroup(exerciseGroup);
        quizExercise = quizExerciseService.save(quizExercise);
        exerciseGroup.setExercises(Set.of(quizExercise));

        assertThat(studentExamRepository.generateStudentExams(exam)).hasSize(numberOfParticipants);
        assertThat(studentExamRepository.findByExamId(exam.getId())).hasSize(numberOfParticipants);
        assertThat(studentExamService.startExercises(exam.getId()).join()).isEqualTo(numberOfParticipants);

        for (int i = 0; i < numberOfParticipants; i++) {
            database.changeUser("student" + (i + 1));
            QuizSubmission quizSubmission = database.generateSubmissionForThreeQuestions(quizExercise, i + 1, true, ZonedDateTime.now());
            request.put("/api/exercises/" + quizExercise.getId() + "/submissions/exam", quizSubmission, HttpStatus.OK);
        }

        database.changeUser("instructor1");
        // All exams should be over before evaluation
        for (StudentExam studentExam : studentExamRepository.findByExamId(exam.getId())) {
            studentExam.setWorkingTime(0);
            studentExamRepository.save(studentExam);
        }

        Integer numberOfEvaluatedExercises = request.postWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises",
                Optional.empty(), Integer.class, HttpStatus.OK);

        assertThat(numberOfEvaluatedExercises).isEqualTo(1);

        // Evaluate quiz twice
        numberOfEvaluatedExercises = request.postWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises",
                Optional.empty(), Integer.class, HttpStatus.OK);

        assertThat(numberOfEvaluatedExercises).isEqualTo(1);

        checkStatistics(quizExercise);

        studentExamRepository.deleteAll();

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK);

        userRepository.deleteAll();
    }

    private void checkStatistics(QuizExercise quizExercise) {
        QuizExercise quizExerciseWithStatistic = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsUnrated()).isZero();
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsRated()).isEqualTo(numberOfParticipants);

        int questionScore = quizExerciseWithStatistic.getQuizQuestions().stream().map(QuizQuestion::getPoints).reduce(0, Integer::sum);
        assertThat(quizExerciseWithStatistic.getMaxPoints()).isEqualTo(questionScore);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()).hasSize(questionScore + 1);
        // check general statistics
        for (var pointCounter : quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()) {
            if (pointCounter.getPoints() == 0.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(Math.round(numberOfParticipants / 3.0));
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
            else if (pointCounter.getPoints() == 3.0 || pointCounter.getPoints() == 4.0 || pointCounter.getPoints() == 6.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(Math.round(numberOfParticipants / 6.0));
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
            else if (pointCounter.getPoints() == 7.0 || pointCounter.getPoints() == 9.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(Math.round(numberOfParticipants / 12.0));
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
            else {
                assertThat(pointCounter.getRatedCounter()).isZero();
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
        }
        // check statistic for each question
        for (var question : quizExerciseWithStatistic.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion) {
                assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(Math.round(numberOfParticipants / 2.0));
            }
            else if (question instanceof DragAndDropQuestion) {
                assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(Math.round(numberOfParticipants / 3.0));
            }
            else {
                assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(Math.round(numberOfParticipants / 4.0));
            }
            assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isZero();
            assertThat(question.getQuizQuestionStatistic().getParticipantsRated()).isEqualTo(numberOfParticipants);
            assertThat(question.getQuizQuestionStatistic().getParticipantsUnrated()).isZero();
        }
    }
}
