package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Achievement;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.AchievementRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AchievementService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class AchievementIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    AchievementService achievementService;

    @Autowired
    AchievementRepository achievementRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CourseRepository courseRepository;

    private User user;

    private Course course;

    private Achievement achievement;

    @BeforeEach
    public void initTestCase() {
        user = database.addUsers(1, 0, 0).get(0);
        course = database.addEmptyCourse();

        achievement = new Achievement();
        achievement.setId(1L);
        achievement.setTitle("Test Achievement");
        achievement.setDescription("Create correct relations");
        achievement.setIcon("");
        achievement.setRank(1);
        achievement.setCourse(course);

        achievement = achievementRepository.save(achievement);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    public void testManyToManyRelationToUser() {
        assertThat(user.getAchievements().size()).isEqualTo(0).as("Number of achievements for user should be 0");

        user.addAchievement(achievement);
        assertThat(user.getAchievements().size()).isEqualTo(1).as("Number of achievements for user should be 1");
        assertThat(user.getAchievements().contains(achievement)).isTrue().as("User has correct achievement");
        assertThat(achievement.getUsers().size()).isEqualTo(1).as("Number of users for achievement should be 1");
        assertThat(achievement.getUsers().contains(user)).isTrue().as("Achievement has correct user");

        user.removeAchievement(achievement);
        assertThat(user.getAchievements().size()).isEqualTo(0).as("Number of achievements for user should be 0");
        assertThat(user.getAchievements().contains(achievement)).isFalse().as("User does not have removed achievement");
        assertThat(achievement.getUsers().size()).isEqualTo(0).as("Number of users for achievement should be 0");
        assertThat(achievement.getUsers().contains(user)).isFalse().as("Achievement does not have incorrect user");
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testManyToManyRelationToUserRepository() throws Exception {
        user.addAchievement(achievement);
        userRepository.save(user);

        var achievements = request.get("/api/achievements", HttpStatus.OK, Set.class);
        assertThat(achievements.size()).isEqualTo(1).as("Number of achievements for user should be 1");

        userRepository.delete(user);
        assertThat(achievementRepository.findAll().contains(achievement)).as("Achievement does not get deleted if user does");
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testManyToOneRelationToCourseRepository() throws Exception {
        var achievements = request.get("/api/courses/" + course.getId() + "/achievements", HttpStatus.OK, Set.class);
        assertThat(achievements.size()).isEqualTo(1).as("Number of achievements for course should be 1");

        courseRepository.delete(course);
        assertThat(achievementRepository.findAll().contains(achievement)).isFalse().as("Achievement gets deleted if course does");
    }

    @Test
    public void testUpdateAchievement() {
        user.addAchievement(achievement);
        userRepository.save(user);
        var achievementToUpdate = achievementRepository.findById(achievement.getId());
        if (achievementToUpdate.isPresent()) {
            achievementToUpdate.get().setDescription("Updated achievement");
            achievementRepository.save(achievementToUpdate.get());
            assertThat(achievementRepository.findById(achievement.getId()).get()).isEqualTo(achievementToUpdate.get()).as("Achievement is updated correctly");
        }
    }

    @Test
    public void testDeleteAchievement() {
        user.addAchievement(achievement);
        userRepository.save(user);
        achievementRepository.delete(achievement);

        assertThat(achievementRepository.findAll().size()).isEqualTo(0).as("Achievement is deleted");
        assertThat(userRepository.getOne(user.getId())).isNotNull().as("User is not deleted");
        assertThat(userRepository.getOne(user.getId()).getAchievements().size()).isEqualTo(0).as("User has no achievements");
        assertThat(courseRepository.getOne(course.getId())).isNotNull().as("Course is not deleted");
        assertThat(achievementService.findAllForCourse(course.getId()).size()).isEqualTo(0).as("Course has no achievements");
    }
}
