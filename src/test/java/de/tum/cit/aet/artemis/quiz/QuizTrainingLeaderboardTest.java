package de.tum.cit.aet.artemis.quiz;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.course.CourseAccessService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgressData;
import de.tum.cit.aet.artemis.quiz.domain.QuizTrainingLeaderboard;
import de.tum.cit.aet.artemis.quiz.dto.LeaderboardEntryDTO;
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
    private CourseAccessService courseAccessService;

    @Autowired
    private CourseTestRepository courseTestRepository;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
    }

    private QuizTrainingLeaderboard getQuizTrainingLeaderboard(Course course, User user) {
        QuizTrainingLeaderboard quizTrainingLeaderboard = new QuizTrainingLeaderboard();
        quizTrainingLeaderboard.setCourse(course);
        quizTrainingLeaderboard.setUser(user);
        quizTrainingLeaderboard.setLeaderboardName("Timm");
        quizTrainingLeaderboard.setTotalScore(100);
        quizTrainingLeaderboard.setScore(10);
        quizTrainingLeaderboard.setLeagueId(2);
        quizTrainingLeaderboard.setAnsweredCorrectly(10);
        quizTrainingLeaderboard.setAnsweredWrong(10);
        quizTrainingLeaderboard.setTotalQuestions(30);
        return quizTrainingLeaderboard;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateQuizLeaderboardEntry() {
        User user = userTestRepository.findAll().get(0);
        Course course = courseUtilService.createCourse();
        QuizTrainingLeaderboard quizTrainingLeaderboard = getQuizTrainingLeaderboard(course, user);
        quizTrainingLeaderboardRepository.save(quizTrainingLeaderboard);
        QuizQuestionProgressData data = new QuizQuestionProgressData();
        data.setLastScore(1.0);
        data.setBox(2);
        QuizQuestionProgressData data2 = new QuizQuestionProgressData();
        data2.setLastScore(0.0);
        data2.setBox(1);
        quizTrainingLeaderboardService.updateLeaderboardScore(user.getId(), course.getId(), Set.of(data, data2));
        QuizTrainingLeaderboard leaderboardEntry = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(user.getId(), course.getId()).orElseThrow();
        assertThat(leaderboardEntry.getLeaderboardName()).isEqualTo("Timm");
        assertThat(leaderboardEntry.getTotalScore()).isEqualTo(100);
        assertThat(leaderboardEntry.getScore()).isEqualTo(14);
        assertThat(leaderboardEntry.getLeagueId()).isEqualTo(2);
        assertThat(leaderboardEntry.getAnsweredCorrectly()).isEqualTo(11);
        assertThat(leaderboardEntry.getAnsweredWrong()).isEqualTo(11);
        assertThat(leaderboardEntry.getCourse().getId()).isEqualTo(course.getId());
        assertThat(leaderboardEntry.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateQuizLeaderboardEntryNoExistingEntry() {
        User user = userTestRepository.findAll().get(0);
        Course course = courseUtilService.createCourse();
        QuizQuestionProgressData data1 = new QuizQuestionProgressData();
        data1.setLastScore(1.0);
        data1.setBox(2);
        QuizQuestionProgressData data2 = new QuizQuestionProgressData();
        data2.setLastScore(0.0);
        data2.setBox(1);
        quizTrainingLeaderboardService.updateLeaderboardScore(user.getId(), course.getId(), Set.of(data1, data2));
        QuizTrainingLeaderboard leaderboardEntry = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(user.getId(), course.getId()).orElseThrow();
        assertThat(leaderboardEntry.getLeaderboardName()).isEqualTo(user.getFirstName());
        assertThat(leaderboardEntry.getTotalScore()).isEqualTo(0);
        assertThat(leaderboardEntry.getScore()).isEqualTo(4);
        assertThat(leaderboardEntry.getLeagueId()).isEqualTo(3);
        assertThat(leaderboardEntry.getAnsweredCorrectly()).isEqualTo(1);
        assertThat(leaderboardEntry.getAnsweredWrong()).isEqualTo(1);
        assertThat(leaderboardEntry.getCourse().getId()).isEqualTo(course.getId());
        assertThat(leaderboardEntry.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLeaderboardEntry() {
        User user = userTestRepository.findAll().get(0);
        Course course = courseUtilService.createCourse();
        course.setEnrollmentEnabled(true);
        user.setGroups(Set.of("Test"));
        user.setAuthorities(Set.of(Authority.USER_AUTHORITY));
        course.setStudentGroupName("Test");
        userTestRepository.save(user);
        courseTestRepository.save(course);
        courseAccessService.enrollUserForCourseOrThrow(user, course);
        QuizTrainingLeaderboard quizTrainingLeaderboard = getQuizTrainingLeaderboard(course, user);
        quizTrainingLeaderboardRepository.save(quizTrainingLeaderboard);
        List<LeaderboardEntryDTO> leaderboardEntryDTO = quizTrainingLeaderboardService.getLeaderboard(user.getId(), course.getId());
        assertThat(leaderboardEntryDTO.size()).isEqualTo(1);
        assertThat(leaderboardEntryDTO.get(0).answeredCorrectly()).isEqualTo(10);
        assertThat(leaderboardEntryDTO.get(0).answeredWrong()).isEqualTo(10);
        assertThat(leaderboardEntryDTO.get(0).score()).isEqualTo(10);
        assertThat(leaderboardEntryDTO.get(0).rank()).isEqualTo(1);
        assertThat(leaderboardEntryDTO.get(0).totalScore()).isEqualTo(100);
        assertThat(leaderboardEntryDTO.get(0).totalQuestions()).isEqualTo(30);
        assertThat(leaderboardEntryDTO.get(0).student()).isEqualTo("Timm");
        assertThat(leaderboardEntryDTO.get(0).studentLeague()).isEqualTo(2);
    }
}
