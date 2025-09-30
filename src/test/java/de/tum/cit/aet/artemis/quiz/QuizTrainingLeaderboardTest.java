package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.http.HttpStatus.OK;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgressData;
import de.tum.cit.aet.artemis.quiz.domain.QuizTrainingLeaderboard;
import de.tum.cit.aet.artemis.quiz.dto.LeaderboardEntryDTO;
import de.tum.cit.aet.artemis.quiz.dto.LeaderboardSettingDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizTrainingLeaderboardRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizTrainingLeaderboardService;
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

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
    }

    private QuizTrainingLeaderboard getQuizTrainingLeaderboard(Course course, User user) {
        QuizTrainingLeaderboard quizTrainingLeaderboard = new QuizTrainingLeaderboard();
        quizTrainingLeaderboard.setCourse(course);
        quizTrainingLeaderboard.setUser(user);
        quizTrainingLeaderboard.setScore(10);
        quizTrainingLeaderboard.setLeague(5);
        quizTrainingLeaderboard.setAnsweredCorrectly(10);
        quizTrainingLeaderboard.setAnsweredWrong(10);
        quizTrainingLeaderboard.setDueDate(ZonedDateTime.now());
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
        assertThat(leaderboardEntry.getScore()).isEqualTo(14);
        assertThat(leaderboardEntry.getLeague()).isEqualTo(5);
        assertThat(leaderboardEntry.getAnsweredCorrectly()).isEqualTo(11);
        assertThat(leaderboardEntry.getAnsweredWrong()).isEqualTo(10);
        assertThat(leaderboardEntry.getCourse().getId()).isEqualTo(course.getId());
        assertThat(leaderboardEntry.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLeaderboardEntry() throws Exception {
        User user = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1");
        Course course = courseUtilService.createCourse();
        userTestRepository.save(user);
        courseTestRepository.save(course);
        QuizTrainingLeaderboard quizTrainingLeaderboard = getQuizTrainingLeaderboard(course, user);
        quizTrainingLeaderboardRepository.save(quizTrainingLeaderboard);
        List<LeaderboardEntryDTO> leaderboardEntryDTO = request.getList("/api/quiz/courses/" + course.getId() + "/training/leaderboard", OK, LeaderboardEntryDTO.class);
        assertThat(leaderboardEntryDTO.size()).isEqualTo(1);
        assertThat(leaderboardEntryDTO.getFirst().answeredCorrectly()).isEqualTo(10);
        assertThat(leaderboardEntryDTO.getFirst().answeredWrong()).isEqualTo(10);
        assertThat(leaderboardEntryDTO.getFirst().score()).isEqualTo(10);
        assertThat(leaderboardEntryDTO.getFirst().rank()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initializeLeaderboardEntry() throws Exception {
        User user = userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + "student1");
        Course course = courseUtilService.createCourse();
        userTestRepository.save(user);
        courseTestRepository.save(course);
        LeaderboardSettingDTO settingDTO = new LeaderboardSettingDTO(true);
        request.postWithResponseBody("/api/quiz/courses/" + course.getId() + "/leaderboard-entry", settingDTO, Void.class, OK);
        QuizTrainingLeaderboard leaderboardEntry = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(user.getId(), course.getId()).orElseThrow();
        assertThat(leaderboardEntry.getScore()).isEqualTo(0);
        assertThat(leaderboardEntry.getLeague()).isEqualTo(5);
        assertThat(leaderboardEntry.getAnsweredCorrectly()).isEqualTo(0);
        assertThat(leaderboardEntry.getAnsweredWrong()).isEqualTo(0);
        assertThat(leaderboardEntry.getCourse().getId()).isEqualTo(course.getId());
        assertThat(leaderboardEntry.getUser().getId()).isEqualTo(user.getId());
        assertThat(leaderboardEntry.getStreak()).isEqualTo(0);
        assertThat(leaderboardEntry.isShowInLeaderboard()).isTrue();
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
}
