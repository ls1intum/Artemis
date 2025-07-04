package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.within;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgressDataDAO;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.service.QuizQuestionProgressService;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizQuestionProgressTestRepository;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizQuestionTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

public class QuizQuestionProgressTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private QuizQuestionProgressService quizQuestionProgressService;

    @Autowired
    private QuizQuestionProgressTestRepository quizQuestionProgressTestRepository;

    @Autowired
    private QuizQuestionTestRepository quizQuestionTestRepository;

    @Autowired
    private UserTestRepository userTestRepository;

    @Autowired
    private QuizExerciseTestRepository quizExerciseTestRepository;

    @Autowired
    private CourseTestRepository courseTestRepository;

    private QuizQuestionProgress quizQuestionProgress;

    private QuizQuestion quizQuestion;

    private Long userId;

    private Long quizQuestionId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setLogin("testuser");
        userTestRepository.save(user);
        userId = user.getId();

        quizQuestion = new MultipleChoiceQuestion();
        quizQuestion = quizQuestionTestRepository.save(quizQuestion);
        quizQuestionId = quizQuestion.getId();

        quizQuestionProgress = new QuizQuestionProgress();
        quizQuestionProgress.setUserId(userId);
        quizQuestionProgress.setQuizQuestionId(quizQuestionId);

        QuizQuestionProgressDataDAO progressData = new QuizQuestionProgressDataDAO();
        progressData.setEasinessFactor(2.5);
        progressData.setInterval(1);
        progressData.setSessionCount(0);
        progressData.setPriority(1);
        progressData.setBox(1);
        progressData.setLastScore(0);

        quizQuestionProgress.setProgressJson(progressData);
        quizQuestionProgress.setLastAnsweredAt(ZonedDateTime.now());
        quizQuestionProgressService.save(quizQuestionProgress);
        quizQuestionProgressTestRepository.save(quizQuestionProgress);
    }

    @AfterEach
    void cleanUp() {
        quizQuestionProgressTestRepository.deleteAll();
        quizQuestionTestRepository.deleteAll();
        userTestRepository.deleteAll();
    }

    @WithMockUser(username = "testuser")
    @Test
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

        quizQuestionProgressService.retrieveProgressFromResultAndSubmission(quizExercise, quizSubmission);

        // Progress exists in database
        Optional<QuizQuestionProgress> progress = quizQuestionProgressTestRepository.findByUserIdAndQuizQuestionId(userId, quizQuestionId);
        assertThat(progress).isNotNull();
        assertThat(progress.get().getUserId()).isEqualTo(userId);
        assertThat(progress.get().getQuizQuestionId()).isEqualTo(quizQuestionId);
        assertThat(progress.get().getLastAnsweredAt().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(time.truncatedTo(ChronoUnit.SECONDS));

        QuizQuestionProgressDataDAO data = progress.get().getProgressJson();
        assertThat(data.getEasinessFactor()).isEqualTo(2.6);
        assertThat(data.getRepetition()).isEqualTo(1);
        assertThat(data.getInterval()).isEqualTo(1);
        assertThat(data.getSessionCount()).isEqualTo(1);
        assertThat(data.getPriority()).isEqualTo(2);
        assertThat(data.getBox()).isEqualTo(1);
        assertThat(data.getLastScore()).isEqualTo(1.0);
        assertThat(data.getAttempts().size()).isEqualTo(1);
        assertThat(data.getAttempts().get(0).getAnsweredAt().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(time.truncatedTo(ChronoUnit.SECONDS));
        assertThat(data.getAttempts().get(0).getScore()).isEqualTo(1.0);

        // Progress does not exist in database
        quizQuestionProgressTestRepository.deleteAll();
        quizQuestionProgressService.retrieveProgressFromResultAndSubmission(quizExercise, quizSubmission);
        Optional<QuizQuestionProgress> progressEmpty = quizQuestionProgressTestRepository.findByUserIdAndQuizQuestionId(userId, quizQuestionId);
        assertThat(progressEmpty.get().getUserId()).isEqualTo(userId);
        assertThat(progressEmpty.get().getQuizQuestionId()).isEqualTo(quizQuestionId);
        assertThat(progressEmpty.get().getLastAnsweredAt().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(time.truncatedTo(ChronoUnit.SECONDS));

        QuizQuestionProgressDataDAO dataEmpty = progress.get().getProgressJson();
        assertThat(dataEmpty.getEasinessFactor()).isEqualTo(2.6);
        assertThat(dataEmpty.getRepetition()).isEqualTo(1);
        assertThat(dataEmpty.getInterval()).isEqualTo(1);
        assertThat(dataEmpty.getSessionCount()).isEqualTo(1);
        assertThat(dataEmpty.getPriority()).isEqualTo(2);
        assertThat(dataEmpty.getBox()).isEqualTo(1);
        assertThat(dataEmpty.getLastScore()).isEqualTo(1.0);
        assertThat(dataEmpty.getAttempts().size()).isEqualTo(1);
        assertThat(dataEmpty.getAttempts().get(0).getAnsweredAt().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(time.truncatedTo(ChronoUnit.SECONDS));
        assertThat(dataEmpty.getAttempts().get(0).getScore()).isEqualTo(1.0);
    }

    @Test
    void testSaveAndRetrieveProgress() {
        quizQuestionProgressService.save(quizQuestionProgress);
        Optional<QuizQuestionProgress> loaded = quizQuestionProgressTestRepository.findByUserIdAndQuizQuestionId(userId, quizQuestionId);
        assertThat(loaded).isPresent();
    }

    @WithMockUser(username = "testuser")
    @Test
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
            QuizQuestion question = quizQuestionTestRepository.save(new MultipleChoiceQuestion());
            questions.add(question);

            QuizQuestionProgress progress = new QuizQuestionProgress();
            progress.setUserId(userId);
            progress.setQuizQuestionId(question.getId());
            QuizQuestionProgressDataDAO data = new QuizQuestionProgressDataDAO();
            data.setPriority(priorities[i]);
            progress.setProgressJson(data);
            progress.setLastAnsweredAt(ZonedDateTime.now());
            quizQuestionProgressTestRepository.save(progress);
            questionIdsWithPriority.add(question.getId());
        }

        quizExercise.setQuizQuestions(questions);
        quizExerciseTestRepository.save(quizExercise);

        List<QuizQuestion> result = quizQuestionProgressService.getQuestionsForSession(1L);
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
        assertThat(quizQuestionProgressService.calculatePriority(0, 3)).isEqualTo(1);
        assertThat(quizQuestionProgressService.calculatePriority(1, 1)).isEqualTo(2);
        assertThat(quizQuestionProgressService.calculatePriority(4, 3)).isEqualTo(7);
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
        QuizQuestionProgressDataDAO data = new QuizQuestionProgressDataDAO();
        data.setAttempts(null);
        assertThat(quizQuestionProgressService.calculateRepetition(1, data)).isEqualTo(0);
        QuizQuestionProgressDataDAO.Attempt attempt1 = new QuizQuestionProgressDataDAO.Attempt();
        QuizQuestionProgressDataDAO.Attempt attempt2 = new QuizQuestionProgressDataDAO.Attempt();
        attempt1.setScore(1);
        attempt2.setScore(1);
        quizQuestionProgress.getProgressJson().setAttempts(List.of(attempt1));
        assertThat(quizQuestionProgressService.calculateRepetition(0.4, quizQuestionProgress.getProgressJson())).isEqualTo(0);
        assertThat(quizQuestionProgressService.calculateRepetition(1, quizQuestionProgress.getProgressJson())).isEqualTo(1);
        quizQuestionProgress.getProgressJson().setAttempts(List.of(attempt1, attempt2));
        assertThat(quizQuestionProgressService.calculateRepetition(1, quizQuestionProgress.getProgressJson())).isEqualTo(2);
        attempt1.setScore(0);
        assertThat(quizQuestionProgressService.calculateRepetition(1, quizQuestionProgress.getProgressJson())).isEqualTo(0);
    }

}
