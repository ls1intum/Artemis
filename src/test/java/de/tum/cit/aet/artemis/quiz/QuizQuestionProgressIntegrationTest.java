package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.within;
import static org.springframework.http.HttpStatus.OK;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgress;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgressData;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.dto.QuizTrainingAnswerDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerAfterEvaluationDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionProgressRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.quiz.service.QuizQuestionProgressService;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class QuizQuestionProgressIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private QuizQuestionProgressService quizQuestionProgressService;

    @Autowired
    private QuizQuestionProgressRepository quizQuestionProgressRepository;

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;

    @Autowired
    private UserTestRepository userTestRepository;

    @Autowired
    private QuizExerciseTestRepository quizExerciseTestRepository;

    @Autowired
    private CourseTestRepository courseTestRepository;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private QuizExerciseService quizExerciseService;

    private QuizQuestionProgress quizQuestionProgress;

    private QuizQuestion quizQuestion;

    private Long userId;

    private Long quizQuestionId;

    private static final String TEST_PREFIX = "quizprogress";

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);

        User user = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        userId = user.getId();

        quizQuestion = new MultipleChoiceQuestion();
        quizQuestion = quizQuestionRepository.save(quizQuestion);
        quizQuestionId = quizQuestion.getId();

        quizQuestionProgress = new QuizQuestionProgress();
        quizQuestionProgress.setUserId(userId);
        quizQuestionProgress.setQuizQuestionId(quizQuestionId);

        QuizQuestionProgressData progressData = new QuizQuestionProgressData();
        progressData.setEasinessFactor(2.5);
        progressData.setInterval(1);
        progressData.setSessionCount(0);
        progressData.setPriority(1);
        progressData.setBox(1);
        progressData.setLastScore(0);

        quizQuestionProgress.setProgressJson(progressData);
        quizQuestionProgress.setLastAnsweredAt(ZonedDateTime.now());
        quizQuestionProgressRepository.save(quizQuestionProgress);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRetrieveProgressFromResultAndSubmissionAndSave() {
        // Create and save a quiz exercise
        QuizExercise quizExercise = new QuizExercise();
        quizQuestion.setPoints(1.0);
        quizQuestion.setScoringType(ScoringType.ALL_OR_NOTHING);
        quizExercise.setQuizQuestions(List.of(quizQuestion));

        // Create a submitted answer
        MultipleChoiceSubmittedAnswer submittedAnswer = new MultipleChoiceSubmittedAnswer();
        submittedAnswer.setQuizQuestion(quizQuestion);
        submittedAnswer.setScoreInPoints(1.0);

        // Create a quiz submission
        QuizSubmission quizSubmission = new QuizSubmission();
        ZonedDateTime time = ZonedDateTime.now();
        quizSubmission.setSubmissionDate(time);
        quizSubmission.setSubmittedAnswers(Set.of(submittedAnswer));

        quizQuestionProgressService.retrieveProgressFromResultAndSubmission(quizExercise, quizSubmission, userId);

        // Progress exists in database
        Optional<QuizQuestionProgress> progress = quizQuestionProgressRepository.findByUserIdAndQuizQuestionId(userId, quizQuestionId);
        assertThat(progress).isNotNull();
        assertThat(progress.get().getUserId()).isEqualTo(userId);
        assertThat(progress.get().getQuizQuestionId()).isEqualTo(quizQuestionId);
        assertThat(progress.get().getLastAnsweredAt().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(time.truncatedTo(ChronoUnit.SECONDS));

        QuizQuestionProgressData data = progress.get().getProgressJson();
        assertThat(data.getEasinessFactor()).isEqualTo(2.6);
        assertThat(data.getRepetition()).isEqualTo(1);
        assertThat(data.getInterval()).isEqualTo(1);
        assertThat(data.getSessionCount()).isEqualTo(1);
        assertThat(data.getPriority()).isEqualTo(2);
        assertThat(data.getBox()).isEqualTo(1);
        assertThat(data.getLastScore()).isEqualTo(1.0);
        assertThat(data.getAttempts().size()).isEqualTo(1);
        assertThat(data.getAttempts().getFirst().getAnsweredAt().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(time.truncatedTo(ChronoUnit.SECONDS));
        assertThat(data.getAttempts().getFirst().getScore()).isEqualTo(1.0);

        // Progress does not exist in the database
        quizQuestionProgressRepository.deleteAll();
        quizQuestionProgressService.retrieveProgressFromResultAndSubmission(quizExercise, quizSubmission, userId);
        Optional<QuizQuestionProgress> progressEmpty = quizQuestionProgressRepository.findByUserIdAndQuizQuestionId(userId, quizQuestionId);
        assertThat(progressEmpty.get().getUserId()).isEqualTo(userId);
        assertThat(progressEmpty.get().getQuizQuestionId()).isEqualTo(quizQuestionId);
        assertThat(progressEmpty.get().getLastAnsweredAt().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(time.truncatedTo(ChronoUnit.SECONDS));

        QuizQuestionProgressData dataEmpty = progressEmpty.get().getProgressJson();
        assertThat(dataEmpty.getEasinessFactor()).isEqualTo(2.6);
        assertThat(dataEmpty.getRepetition()).isEqualTo(1);
        assertThat(dataEmpty.getInterval()).isEqualTo(1);
        assertThat(dataEmpty.getSessionCount()).isEqualTo(1);
        assertThat(dataEmpty.getPriority()).isEqualTo(2);
        assertThat(dataEmpty.getBox()).isEqualTo(1);
        assertThat(dataEmpty.getLastScore()).isEqualTo(1.0);
        assertThat(dataEmpty.getAttempts().size()).isEqualTo(1);
        assertThat(dataEmpty.getAttempts().getFirst().getAnsweredAt().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(time.truncatedTo(ChronoUnit.SECONDS));
        assertThat(dataEmpty.getAttempts().getFirst().getScore()).isEqualTo(1.0);

        quizExercise.setQuizQuestions(List.of());
        quizQuestionProgressService.retrieveProgressFromResultAndSubmission(quizExercise, quizSubmission, userId);
    }

    @Test
    void testSaveAndRetrieveProgress() {
        quizQuestionProgressRepository.save(quizQuestionProgress);
        Optional<QuizQuestionProgress> loaded = quizQuestionProgressRepository.findByUserIdAndQuizQuestionId(userId, quizQuestionId);
        assertThat(loaded).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetQuestionsForSession() {
        Course course = new Course();
        course.setId(1L);
        courseTestRepository.save(course);

        QuizExercise quizExercise = new QuizExercise();
        quizExercise.setCourse(course);
        quizExercise.setIsOpenForPractice(true);
        quizExerciseTestRepository.save(quizExercise);

        int[] priorities = { 8, 9, 10, 11, 12, 1, 2, 3, 4, 5, 6, 7 };
        List<QuizQuestion> questions = new ArrayList<>();
        List<Long> questionIdsWithPriority = new ArrayList<>();

        for (int i = 0; i < 12; i++) {
            QuizQuestion question = quizQuestionRepository.save(new MultipleChoiceQuestion());
            questions.add(question);

            QuizQuestionProgress progress = new QuizQuestionProgress();
            progress.setUserId(userId);
            progress.setQuizQuestionId(question.getId());
            QuizQuestionProgressData data = new QuizQuestionProgressData();
            data.setPriority(priorities[i]);
            progress.setProgressJson(data);
            progress.setLastAnsweredAt(ZonedDateTime.now());
            quizQuestionProgressRepository.save(progress);
            questionIdsWithPriority.add(question.getId());
        }

        quizExercise.setQuizQuestions(questions);
        quizExerciseTestRepository.save(quizExercise);

        List<QuizQuestion> result = quizQuestionProgressService.getQuestionsForSession(1L, userId);
        assertThat(result.size()).isEqualTo(10);

        List<Long> expectedOrder = new ArrayList<>(questionIdsWithPriority);
        expectedOrder.sort(Comparator.comparingInt(id -> priorities[questionIdsWithPriority.indexOf(id)]));

        for (int i = 0; i < 10; i++) {
            assertThat(result.get(i).getId()).isEqualTo(expectedOrder.get(i));
        }
    }

    @Test
    void testCalculateBox() {
        assertThat(quizQuestionProgressService.calculateBox(1)).isEqualTo(1);
        assertThat(quizQuestionProgressService.calculateBox(2)).isEqualTo(2);
        assertThat(quizQuestionProgressService.calculateBox(3)).isEqualTo(3);
        assertThat(quizQuestionProgressService.calculateBox(4)).isEqualTo(3);
        assertThat(quizQuestionProgressService.calculateBox(5)).isEqualTo(4);
        assertThat(quizQuestionProgressService.calculateBox(7)).isEqualTo(4);
        assertThat(quizQuestionProgressService.calculateBox(8)).isEqualTo(5);
        assertThat(quizQuestionProgressService.calculateBox(15)).isEqualTo(5);
        assertThat(quizQuestionProgressService.calculateBox(16)).isEqualTo(6);
        assertThat(quizQuestionProgressService.calculateBox(30)).isEqualTo(6);
    }

    @Test
    void testCalculatePriority() {
        assertThat(quizQuestionProgressService.calculatePriority(1, 3, 0)).isEqualTo(1);
        assertThat(quizQuestionProgressService.calculatePriority(1, 1, 1)).isEqualTo(2);
        assertThat(quizQuestionProgressService.calculatePriority(4, 3, 1)).isEqualTo(7);
    }

    @Test
    void testCalculateInterval() {
        assertThat(quizQuestionProgressService.calculateInterval(1.3, 1, 1)).isEqualTo(1);
        assertThat(quizQuestionProgressService.calculateInterval(1.3, 2, 0)).isEqualTo(1);
        assertThat(quizQuestionProgressService.calculateInterval(1.3, 3, 2)).isEqualTo(2);
        assertThat(quizQuestionProgressService.calculateInterval(1.5, 2, 3)).isEqualTo(3);
    }

    @Test
    void testCalculateEasinessFactor() {
        assertThat(quizQuestionProgressService.calculateEasinessFactor(0.4, 1.3)).isEqualTo(1.3);
        assertThat(quizQuestionProgressService.calculateEasinessFactor(1, 1.3)).isCloseTo(1.4, within(1e-5));
    }

    @Test
    void testCalculateRepetition() {
        QuizQuestionProgressData data = new QuizQuestionProgressData();
        data.setAttempts(null);
        assertThat(quizQuestionProgressService.calculateRepetition(1, data)).isEqualTo(0);
        QuizQuestionProgressData.Attempt attempt1 = new QuizQuestionProgressData.Attempt();
        QuizQuestionProgressData.Attempt attempt2 = new QuizQuestionProgressData.Attempt();
        QuizQuestionProgressData.Attempt attempt3 = new QuizQuestionProgressData.Attempt();
        attempt1.setScore(1);
        attempt2.setScore(1);
        quizQuestionProgress.getProgressJson().setAttempts(List.of(attempt1));
        assertThat(quizQuestionProgressService.calculateRepetition(0.4, quizQuestionProgress.getProgressJson())).isEqualTo(0);
        assertThat(quizQuestionProgressService.calculateRepetition(1, quizQuestionProgress.getProgressJson())).isEqualTo(1);
        quizQuestionProgress.getProgressJson().setAttempts(List.of(attempt1, attempt2));
        assertThat(quizQuestionProgressService.calculateRepetition(1, quizQuestionProgress.getProgressJson())).isEqualTo(2);
        attempt1.setScore(0);
        attempt3.setScore(1);
        quizQuestionProgress.getProgressJson().setAttempts(List.of(attempt1, attempt2, attempt3));
        assertThat(quizQuestionProgressService.calculateRepetition(1, quizQuestionProgress.getProgressJson())).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubmitForTraining() throws Exception {
        Course course = courseUtilService.createCourse();

        MultipleChoiceQuestion mcQuestion = new MultipleChoiceQuestion();
        mcQuestion.setTitle("Test Question");
        mcQuestion.setPoints(1.0);
        mcQuestion.setScoringType(ScoringType.ALL_OR_NOTHING);
        mcQuestion = quizQuestionRepository.save(mcQuestion);

        MultipleChoiceSubmittedAnswer submittedAnswer = new MultipleChoiceSubmittedAnswer();
        submittedAnswer.setQuizQuestion(mcQuestion);
        QuizTrainingAnswerDTO trainingAnswerDTO = new QuizTrainingAnswerDTO(submittedAnswer);

        SubmittedAnswerAfterEvaluationDTO result = request.postWithResponseBody("/api/quiz/courses/" + course.getId() + "/training/" + mcQuestion.getId() + "/quiz",
                trainingAnswerDTO, SubmittedAnswerAfterEvaluationDTO.class, HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.multipleChoiceSubmittedAnswer()).isNotNull();
        assertThat(result.scoreInPoints()).isNotNull();

        Optional<QuizQuestionProgress> savedProgress = quizQuestionProgressRepository.findByUserIdAndQuizQuestionId(userId, mcQuestion.getId());

        assertThat(savedProgress).isPresent();
        assertThat(savedProgress.get().getUserId()).isEqualTo(userId);
        assertThat(savedProgress.get().getQuizQuestionId()).isEqualTo(mcQuestion.getId());
        assertThat(savedProgress.get().getLastAnsweredAt()).isNotNull();

        QuizQuestionProgressData data = savedProgress.get().getProgressJson();
        assertThat(data.getLastScore()).isEqualTo(1.0);
        assertThat(data.getSessionCount()).isEqualTo(1);
        assertThat(data.getAttempts().size()).isEqualTo(1);
        assertThat(data.getAttempts().getFirst().getScore()).isEqualTo(1.0);
        assertThat(data.getRepetition()).isEqualTo(1);
        assertThat(data.getEasinessFactor()).isEqualTo(2.6);
        assertThat(data.getInterval()).isEqualTo(1);
        assertThat(data.getPriority()).isEqualTo(2);
        assertThat(data.getBox()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetQuizQuestionsForPractice() throws Exception {

        Course course = quizExerciseUtilService.addCourseWithOneQuizExercise();
        QuizExercise quizExercise = (QuizExercise) course.getExercises().stream().findFirst().get();
        quizExercise.setIsOpenForPractice(true);
        quizExerciseService.save(quizExercise);

        List<QuizQuestion> quizQuestions = request.getList("/api/quiz/courses/" + course.getId() + "/training/quiz", OK, QuizQuestion.class);

        Assertions.assertThat(quizQuestions).isNotNull();
        Assertions.assertThat(quizQuestions).hasSameSizeAs(quizExercise.getQuizQuestions());
        Assertions.assertThat(quizQuestions).containsAll(quizExercise.getQuizQuestions());
    }
}
