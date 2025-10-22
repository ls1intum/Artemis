package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.within;
import static org.springframework.http.HttpStatus.OK;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionTrainingDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerAfterEvaluationDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionProgressRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.quiz.service.QuizQuestionProgressService;
import de.tum.cit.aet.artemis.quiz.service.QuizTrainingLeaderboardService;
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

    @Autowired
    private QuizTrainingLeaderboardService quizTrainingLeaderboardService;

    private QuizQuestionProgress quizQuestionProgress;

    private QuizQuestion quizQuestion;

    private Long userId;

    private Long quizQuestionId;

    private static final String TEST_PREFIX = "quizprogress";

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);

        Course course = new Course();
        courseTestRepository.save(course);
        User user = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        userId = user.getId();

        quizQuestion = new MultipleChoiceQuestion();
        quizQuestion = quizQuestionRepository.save(quizQuestion);
        quizQuestionId = quizQuestion.getId();

        quizQuestionProgress = new QuizQuestionProgress();
        quizQuestionProgress.setUserId(userId);
        quizQuestionProgress.setQuizQuestionId(quizQuestionId);
        quizQuestionProgress.setCourseId(course.getId());

        QuizQuestionProgressData progressData = new QuizQuestionProgressData();
        progressData.setEasinessFactor(2.5);
        progressData.setInterval(1);
        progressData.setSessionCount(0);
        progressData.setPriority(1);
        progressData.setBox(1);
        progressData.setLastScore(0);

        quizQuestionProgress.setProgressJson(progressData);
        quizQuestionProgress.setLastAnsweredAt(ZonedDateTime.now());
        quizQuestionProgress.setDueDate(ZonedDateTime.now());
        quizQuestionProgressRepository.save(quizQuestionProgress);
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
        courseTestRepository.save(course);

        QuizExercise quizExercise = new QuizExercise();
        quizExercise.setCourse(course);
        quizExercise.setIsOpenForPractice(true);
        quizExerciseTestRepository.save(quizExercise);

        List<QuizQuestion> questions = new ArrayList<>();

        for (int i = 0; i < 12; i++) {
            QuizQuestion question = quizQuestionRepository.save(new MultipleChoiceQuestion());
            questions.add(question);

            QuizQuestionProgress progress = new QuizQuestionProgress();
            progress.setUserId(userId);
            progress.setCourseId(course.getId());
            progress.setQuizQuestionId(question.getId());
            QuizQuestionProgressData data = new QuizQuestionProgressData();
            progress.setDueDate(ZonedDateTime.now().minusDays(i));
            progress.setProgressJson(data);
            progress.setLastAnsweredAt(ZonedDateTime.now());
            quizQuestionProgressRepository.save(progress);
        }

        quizExercise.setQuizQuestions(questions);
        quizExerciseTestRepository.save(quizExercise);

        Pageable pageable = Pageable.ofSize(10);

        Slice<QuizQuestionTrainingDTO> result = quizQuestionProgressService.getQuestionsForSession(course.getId(), userId, pageable, Set.of(questions.getFirst().getId()), false);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.getSize()).isEqualTo(10);
        for (QuizQuestionTrainingDTO dto : result.getContent()) {
            assertThat(dto.isRated()).isTrue();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetQuestionsForSessionNoDueDate() {
        Course course = new Course();
        courseTestRepository.save(course);

        QuizExercise quizExercise = new QuizExercise();
        quizExercise.setCourse(course);
        quizExercise.setIsOpenForPractice(true);
        quizExerciseTestRepository.save(quizExercise);

        List<QuizQuestion> questions = new ArrayList<>();

        for (int i = 0; i < 12; i++) {
            QuizQuestion question = quizQuestionRepository.save(new MultipleChoiceQuestion());
            questions.add(question);

            QuizQuestionProgress progress = new QuizQuestionProgress();
            progress.setUserId(userId);
            progress.setCourseId(course.getId());
            progress.setQuizQuestionId(question.getId());
            QuizQuestionProgressData data = new QuizQuestionProgressData();
            progress.setDueDate(ZonedDateTime.now().plusDays(i + 1));
            progress.setProgressJson(data);
            progress.setLastAnsweredAt(ZonedDateTime.now());
            quizQuestionProgressRepository.save(progress);
        }

        quizExercise.setQuizQuestions(questions);
        quizExerciseTestRepository.save(quizExercise);

        Pageable pageable = Pageable.ofSize(10);

        Slice<QuizQuestionTrainingDTO> result = quizQuestionProgressService.getQuestionsForSession(course.getId(), userId, pageable, Set.of(), true);

        assertThat(result.hasNext()).isTrue();
        assertThat(result.getSize()).isEqualTo(10);
        for (QuizQuestionTrainingDTO dto : result.getContent()) {
            assertThat(dto.isRated()).isFalse();
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
        assertThat(quizQuestionProgressService.calculateInterval(1.3, 2, 0)).isEqualTo(0);
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
        QuizQuestionProgress progress = new QuizQuestionProgress();
        QuizQuestionProgressData dataExisting = new QuizQuestionProgressData();
        dataExisting.setEasinessFactor(2.5);
        dataExisting.setInterval(1);
        dataExisting.setSessionCount(0);
        progress.setProgressJson(dataExisting);
        progress.setQuizQuestionId(mcQuestion.getId());
        progress.setUserId(userId);
        progress.setCourseId(course.getId());
        progress.setDueDate(ZonedDateTime.now());
        quizQuestionProgressRepository.save(progress);

        MultipleChoiceSubmittedAnswer submittedAnswer = new MultipleChoiceSubmittedAnswer();
        submittedAnswer.setQuizQuestion(mcQuestion);
        submittedAnswer.setSelectedOptions(Set.of());

        quizTrainingLeaderboardService.setInitialLeaderboardEntry(userId, course.getId(), true);

        SubmittedAnswerAfterEvaluationDTO result = request.postWithResponseBody(
                "/api/quiz/courses/" + course.getId() + "/training-questions/" + mcQuestion.getId() + "/submit?isRated=true", submittedAnswer,
                SubmittedAnswerAfterEvaluationDTO.class, HttpStatus.OK);

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
        ZonedDateTime expectedUtc = ZonedDateTime.now(ZoneOffset.UTC).plusDays(1).truncatedTo(ChronoUnit.MINUTES);
        assertThat(savedProgress.get().getDueDate().withZoneSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES)).isEqualTo(expectedUtc);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetQuizQuestionsForPractice() throws Exception {

        Course course = quizExerciseUtilService.addCourseWithOneQuizExercise();
        QuizExercise quizExercise = (QuizExercise) course.getExercises().stream().findFirst().get();
        quizExercise.setIsOpenForPractice(true);
        quizExerciseService.save(quizExercise);

        List<QuizQuestionTrainingDTO> quizQuestions = Arrays.asList(
                request.postWithResponseBody("/api/quiz/courses/" + course.getId() + "/training-questions?isNewSession=true", Set.of(), QuizQuestionTrainingDTO[].class, OK));

        Assertions.assertThat(quizQuestions).isNotNull();
        Assertions.assertThat(quizQuestions).hasSameSizeAs(quizExercise.getQuizQuestions());
        Assertions.assertThat(quizQuestions.stream().map(QuizQuestionTrainingDTO::getId).toList())
                .containsAll(quizExercise.getQuizQuestions().stream().map(QuizQuestion::getId).toList());
    }

    @Test
    void testUpdateExistingProgress() {
        ZonedDateTime newAnsweredTime = ZonedDateTime.now();
        QuizQuestionProgressData newProgressData = new QuizQuestionProgressData();
        newProgressData.setEasinessFactor(3.0);
        newProgressData.setInterval(2);
        newProgressData.setSessionCount(1);
        newProgressData.setPriority(2);
        newProgressData.setBox(2);
        newProgressData.setLastScore(0.5);

        quizQuestionProgressService.updateExistingProgress(userId, quizQuestion, newProgressData, newAnsweredTime);

        Optional<QuizQuestionProgress> updatedProgressOptional = quizQuestionProgressRepository.findByUserIdAndQuizQuestionId(userId, quizQuestionId);
        assertThat(updatedProgressOptional).isPresent();

        QuizQuestionProgress updatedProgress = updatedProgressOptional.get();
        assertThat(updatedProgress.getLastAnsweredAt()).isNotNull();
        assertThat(updatedProgress.getProgressJson().getEasinessFactor()).isEqualTo(3.0);
        assertThat(updatedProgress.getProgressJson().getInterval()).isEqualTo(2);
        assertThat(updatedProgress.getProgressJson().getSessionCount()).isEqualTo(1);
        assertThat(updatedProgress.getProgressJson().getPriority()).isEqualTo(2);
        assertThat(updatedProgress.getProgressJson().getBox()).isEqualTo(2);
        assertThat(updatedProgress.getProgressJson().getLastScore()).isEqualTo(0.5);
    }

    @Test
    void testUpdateExistingProgress_ProgressNotFound() {
        Long nonExistentUserId = 999L;
        ZonedDateTime newAnsweredTime = ZonedDateTime.now();
        QuizQuestionProgressData newProgressData = new QuizQuestionProgressData();

        assertThatThrownBy(() -> quizQuestionProgressService.updateExistingProgress(nonExistentUserId, quizQuestion, newProgressData, newAnsweredTime))
                .isInstanceOf(IllegalStateException.class).hasMessage("Progress entry should exist but was not found.");
    }

    @Test
    void firstAttempt_whenExistingProgressIsNull_shouldCalculateSessionCountRepetitionEasinessIntervalAndDueDateNormally() {
        QuizQuestionProgress progress = new QuizQuestionProgress();
        QuizQuestionProgressData data = new QuizQuestionProgressData();
        double score = 1.0;
        ZonedDateTime answeredAt = ZonedDateTime.of(2024, 6, 1, 10, 0, 0, 0, ZoneId.of("UTC"));

        quizQuestionProgressService.updateProgressWithNewAttempt(data, score, answeredAt);
        quizQuestionProgressService.updateProgressCalculations(data, score, progress, answeredAt);

        assertThat(data.getSessionCount()).isEqualTo(1);
        assertThat(data.getRepetition()).isEqualTo(1);
        assertThat(data.getEasinessFactor()).isEqualTo(2.6);
        assertThat(data.getInterval()).isEqualTo(1);
        assertThat(progress.getDueDate()).isEqualTo(answeredAt.plusDays(1));
        assertThat(data.getBox()).isEqualTo(1);
        assertThat(data.getPriority()).isEqualTo(2);
    }

    @Test
    void afterDueDateAttempt_shouldAdvanceAllValues() {
        ZonedDateTime baseDate = ZonedDateTime.of(2024, 6, 1, 9, 0, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime dueDate = baseDate.plusDays(1); // 2.6.2024 09:00
        ZonedDateTime afterDue = dueDate.plusHours(2); // 2.6.2024 11:00

        QuizQuestionProgress existing = buildProgress(2.5, 1, 1, 1, dueDate, 1, 2, 1.0);
        QuizQuestionProgressData data = new QuizQuestionProgressData();

        quizQuestionProgressService.updateProgressWithNewAttempt(data, 1.0, afterDue);
        quizQuestionProgressService.updateProgressCalculations(data, 1.0, existing, afterDue);

        assertThat(data.getSessionCount()).isEqualTo(2);
        assertThat(data.getRepetition()).isEqualTo(1);
        assertThat(data.getEasinessFactor()).isEqualTo(2.6);
        assertThat(data.getInterval()).isEqualTo(1);
        assertThat(existing.getDueDate()).isEqualTo(afterDue.plusDays(1));
        assertThat(data.getBox()).isEqualTo(1);
        assertThat(data.getPriority()).isEqualTo(3);
    }

    @Test
    void testQuestionsAvailableForPracticeFalse() {
        Course course = new Course();
        courseTestRepository.save(course);

        boolean questionsAvailable = quizQuestionProgressService.questionsAvailableForTraining(course.getId());
        assertThat(questionsAvailable).isFalse();
    }

    @Test
    void testQuestionsAvailableForPracticeTrue() {
        Course course = new Course();
        courseTestRepository.save(course);

        QuizExercise quizExercise = new QuizExercise();
        quizExercise.setCourse(course);
        quizExercise.setIsOpenForPractice(true);
        quizExerciseTestRepository.save(quizExercise);

        QuizQuestion question = new MultipleChoiceQuestion();
        question.setExercise(quizExercise);
        quizQuestionRepository.save(question);

        boolean questionsAvailable = quizQuestionProgressService.questionsAvailableForTraining(course.getId());
        assertThat(questionsAvailable).isTrue();
    }

    private QuizQuestionProgress buildProgress(double easiness, int interval, int sessionCount, int repetition, ZonedDateTime dueDate, int box, int priority, double lastScore) {
        QuizQuestionProgressData data = new QuizQuestionProgressData();
        data.setEasinessFactor(easiness);
        data.setInterval(interval);
        data.setSessionCount(sessionCount);
        data.setRepetition(repetition);
        data.setBox(box);
        data.setPriority(priority);
        data.setLastScore(lastScore);

        QuizQuestionProgress progress = new QuizQuestionProgress();
        progress.setProgressJson(data);
        progress.setDueDate(dueDate);
        return progress;
    }
}
