package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.http.HttpStatus.OK;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgressData;
import de.tum.cit.aet.artemis.quiz.domain.QuizTrainingLeaderboard;
import de.tum.cit.aet.artemis.quiz.dto.LeaderboardEntryDTO;
import de.tum.cit.aet.artemis.quiz.dto.LeaderboardSettingDTO;
import de.tum.cit.aet.artemis.quiz.dto.LeaderboardWithCurrentUserEntryDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizTrainingLeaderboardRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizTrainingLeaderboardService;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class QuizTrainingLeaderboardTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "quizleaderboard";

    @Autowired
    private QuizTrainingLeaderboardRepository quizTrainingLeaderboardRepository;

    @Autowired
    private QuizTrainingLeaderboardService quizTrainingLeaderboardService;

    @Autowired
    private UserTestRepository userTestRepository;

    @Autowired
    private CourseTestRepository courseTestRepository;

    @Autowired
    private QuizExerciseTestRepository quizExerciseRepository;

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 0);
    }

    private QuizTrainingLeaderboard getQuizTrainingLeaderboard(Course course, User user) {
        QuizTrainingLeaderboard quizTrainingLeaderboard = new QuizTrainingLeaderboard();
        quizTrainingLeaderboard.setCourse(course);
        quizTrainingLeaderboard.setUser(user);
        quizTrainingLeaderboard.setScore(10);
        quizTrainingLeaderboard.setLeague(5);
        quizTrainingLeaderboard.setAnsweredCorrectly(10);
        quizTrainingLeaderboard.setAnsweredWrong(10);
        quizTrainingLeaderboard.setDueDate(ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES));
        quizTrainingLeaderboard.setStreak(0);
        quizTrainingLeaderboard.setShowInLeaderboard(true);
        return quizTrainingLeaderboard;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateQuizLeaderboardEntry() {
        User user = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1");
        Course course = courseUtilService.createCourse();
        QuizTrainingLeaderboard quizTrainingLeaderboard = getQuizTrainingLeaderboard(course, user);
        quizTrainingLeaderboardRepository.save(quizTrainingLeaderboard);
        QuizQuestionProgressData data = new QuizQuestionProgressData();
        data.setLastScore(1.0);
        data.setBox(2);
        quizTrainingLeaderboardService.updateLeaderboardScore(user.getId(), course.getId(), data);
        QuizTrainingLeaderboard leaderboardEntry = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(user.getId(), course.getId()).orElseThrow();
        assertThat(leaderboardEntry.getScore()).isEqualTo(12);
        assertThat(leaderboardEntry.getLeague()).isEqualTo(5);
        assertThat(leaderboardEntry.getAnsweredCorrectly()).isEqualTo(11);
        assertThat(leaderboardEntry.getAnsweredWrong()).isEqualTo(10);
        assertThat(leaderboardEntry.getCourse().getId()).isEqualTo(course.getId());
        assertThat(leaderboardEntry.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLeaderboardEntries() throws Exception {
        User user = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1");
        User user2 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + "student2");
        User user3 = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + "student3");
        Course course = courseUtilService.createCourse();
        userTestRepository.save(user);
        userTestRepository.save(user2);
        userTestRepository.save(user3);
        courseTestRepository.save(course);
        QuizTrainingLeaderboard quizTrainingLeaderboard = getQuizTrainingLeaderboard(course, user);
        QuizTrainingLeaderboard quizTrainingLeaderboard2 = getQuizTrainingLeaderboard(course, user2);
        QuizTrainingLeaderboard quizTrainingLeaderboard3 = getQuizTrainingLeaderboard(course, user3);
        quizTrainingLeaderboard2.setScore(50);
        quizTrainingLeaderboard3.setShowInLeaderboard(false);
        quizTrainingLeaderboardRepository.save(quizTrainingLeaderboard);
        quizTrainingLeaderboardRepository.save(quizTrainingLeaderboard2);
        quizTrainingLeaderboardRepository.save(quizTrainingLeaderboard3);
        LeaderboardEntryDTO testEntry1 = LeaderboardEntryDTO.of(quizTrainingLeaderboard, 2, 5, 0);
        LeaderboardEntryDTO testEntry2 = LeaderboardEntryDTO.of(quizTrainingLeaderboard2, 1, 5, 0);
        List<LeaderboardEntryDTO> testList = List.of(testEntry2, testEntry1);
        LeaderboardWithCurrentUserEntryDTO leaderboardDTO = request.get("/api/quiz/courses/" + course.getId() + "/training/leaderboard", OK,
                LeaderboardWithCurrentUserEntryDTO.class);
        List<LeaderboardEntryDTO> leaderboardEntryDTO = leaderboardDTO.leaderboardEntries();
        assertThat(leaderboardEntryDTO.size()).isEqualTo(2);
        assertThat(leaderboardEntryDTO).isEqualTo(testList);
        assertThat(leaderboardEntryDTO.getFirst().answeredCorrectly()).isEqualTo(10);
        assertThat(leaderboardEntryDTO.getFirst().answeredWrong()).isEqualTo(10);
        assertThat(leaderboardEntryDTO.getFirst().score()).isEqualTo(50);
        assertThat(leaderboardEntryDTO.getFirst().rank()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initializeLeaderboardEntry() throws Exception {
        User user = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1");
        Course course = courseUtilService.createCourse();
        userTestRepository.save(user);
        courseTestRepository.save(course);
        request.get("/api/quiz/courses/" + course.getId() + "/training/leaderboard", OK, LeaderboardWithCurrentUserEntryDTO.class);
        QuizTrainingLeaderboard leaderboardEntry = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(user.getId(), course.getId()).orElseThrow();
        assertThat(leaderboardEntry.getScore()).isEqualTo(0);
        assertThat(leaderboardEntry.getLeague()).isEqualTo(5);
        assertThat(leaderboardEntry.getAnsweredCorrectly()).isEqualTo(0);
        assertThat(leaderboardEntry.getAnsweredWrong()).isEqualTo(0);
        assertThat(leaderboardEntry.getCourse().getId()).isEqualTo(course.getId());
        assertThat(leaderboardEntry.getUser().getId()).isEqualTo(user.getId());
        assertThat(leaderboardEntry.getStreak()).isEqualTo(0);
        assertThat(leaderboardEntry.isShowInLeaderboard()).isFalse();
        assertThat(leaderboardEntry.getDueDate()).isCloseTo(ZonedDateTime.now(), Assertions.within(5, ChronoUnit.MINUTES));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLeaderboardSettings() throws Exception {
        User user = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1");
        Course course = courseUtilService.createCourse();
        userTestRepository.save(user);
        courseTestRepository.save(course);
        QuizTrainingLeaderboard quizTrainingLeaderboard = getQuizTrainingLeaderboard(course, user);
        quizTrainingLeaderboardRepository.save(quizTrainingLeaderboard);
        LeaderboardSettingDTO settingDTO = new LeaderboardSettingDTO(false);
        request.put("/api/quiz/leaderboard-settings", settingDTO, OK);
        QuizTrainingLeaderboard updatedEntry = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(user.getId(), course.getId()).orElseThrow();
        assertThat(updatedEntry.isShowInLeaderboard()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateQuizLeaderboardEntryWithFailedAttemptOnSameDay() {
        User user = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1");
        Course course = courseUtilService.createCourse();
        QuizTrainingLeaderboard quizTrainingLeaderboard = getQuizTrainingLeaderboard(course, user);
        quizTrainingLeaderboardRepository.save(quizTrainingLeaderboard);

        QuizQuestionProgressData data = new QuizQuestionProgressData();
        data.setLastScore(1.0);
        data.setBox(2);

        ZonedDateTime base = ZonedDateTime.now(ZoneOffset.UTC).withHour(12).withMinute(0).withSecond(0).withNano(0);

        List<QuizQuestionProgressData.Attempt> attempts = new ArrayList<>();

        QuizQuestionProgressData.Attempt failedAttempt = new QuizQuestionProgressData.Attempt();
        failedAttempt.setScore(0.5);
        failedAttempt.setAnsweredAt(base.minusMinutes(30));
        attempts.add(failedAttempt);

        QuizQuestionProgressData.Attempt successfulAttempt = new QuizQuestionProgressData.Attempt();
        successfulAttempt.setScore(1.0);
        successfulAttempt.setAnsweredAt(base);
        attempts.add(successfulAttempt);

        data.setAttempts(attempts);

        quizTrainingLeaderboardService.updateLeaderboardScore(user.getId(), course.getId(), data);

        QuizTrainingLeaderboard leaderboardEntry = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(user.getId(), course.getId()).orElseThrow();
        assertThat(leaderboardEntry.getScore()).isEqualTo(10);
        assertThat(leaderboardEntry.getAnsweredCorrectly()).isEqualTo(11);
        assertThat(leaderboardEntry.getAnsweredWrong()).isEqualTo(10);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateLeaderboardScoreWithNewQuestions() {
        User user = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1");
        Course course = courseUtilService.createCourse();
        QuizTrainingLeaderboard quizTrainingLeaderboard = getQuizTrainingLeaderboard(course, user);
        quizTrainingLeaderboard.setDueDate(ZonedDateTime.now(ZoneOffset.UTC).plusDays(1));
        quizTrainingLeaderboardRepository.save(quizTrainingLeaderboard);

        QuizExercise quizExercise = new QuizExercise();
        quizExercise.setCourse(course);
        quizExerciseRepository.save(quizExercise);
        QuizQuestion question1 = new MultipleChoiceQuestion();
        QuizQuestion question2 = new MultipleChoiceQuestion();
        QuizQuestion question3 = new MultipleChoiceQuestion();
        question1.setExercise(quizExercise);
        question2.setExercise(quizExercise);
        question3.setExercise(quizExercise);
        quizQuestionRepository.save(question1);
        quizQuestionRepository.save(question2);
        quizQuestionRepository.save(question3);
        QuizQuestionProgressData data = new QuizQuestionProgressData();
        data.setLastScore(1.0);
        data.setBox(1);

        quizTrainingLeaderboardService.updateLeaderboardScore(user.getId(), course.getId(), data);

        QuizTrainingLeaderboard updatedEntry = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(user.getId(), course.getId()).orElseThrow();

        ZonedDateTime expectedDueDate = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1).truncatedTo(ChronoUnit.MINUTES);
        ZonedDateTime actualDueDate = updatedEntry.getDueDate().truncatedTo(ChronoUnit.MINUTES);

        assertThat(actualDueDate).isCloseTo(expectedDueDate, Assertions.within(5, ChronoUnit.MINUTES));
    }
}
