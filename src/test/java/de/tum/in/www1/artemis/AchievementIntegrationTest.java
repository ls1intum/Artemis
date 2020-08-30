package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
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

    private User student;

    private User instructor;

    private Course course;

    private Achievement achievement;

    @BeforeEach
    public void initTestCase() {
        var users = database.addUsers(1, 0, 1);
        student = users.get(0);
        instructor = users.get(1);
        instructor.setGroups(new HashSet<>(Arrays.asList("instructor")));
        course = database.addEmptyCourse();

        achievement = new Achievement();
        achievement.setId(1L);
        achievement.setTitle("Test Achievement");
        achievement.setDescription("Create correct relations");
        achievement.setIcon("test-icon");
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
        assertThat(student.getAchievements().size()).as("Number of achievements for user should be 0").isEqualTo(0);

        student.addAchievement(achievement);
        assertThat(student.getAchievements().size()).as("Number of achievements for user should be 1").isEqualTo(1);
        assertThat(student.getAchievements().contains(achievement)).as("User has correct achievement").isTrue();
        assertThat(achievement.getUsers().size()).as("Number of users for achievement should be 1").isEqualTo(1);
        assertThat(achievement.getUsers().contains(student)).as("Achievement has correct user").isTrue();

        student.removeAchievement(achievement);
        assertThat(student.getAchievements().size()).as("Number of achievements for user should be 0").isEqualTo(0);
        assertThat(student.getAchievements().contains(achievement)).as("User does not have removed achievement").isFalse();
        assertThat(achievement.getUsers().size()).as("Number of users for achievement should be 0").isEqualTo(0);
        assertThat(achievement.getUsers().contains(student)).as("Achievement does not have incorrect user").isFalse();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testDeleteUser() throws Exception {
        student.addAchievement(achievement);
        student = userRepository.save(student);

        var achievements = request.get("/api/achievements", HttpStatus.OK, Set.class);
        assertThat(achievements.size()).as("Number of achievements for user should be 1").isEqualTo(1);

        userRepository.delete(student);
        assertThat(achievementRepository.findAll().contains(achievement)).as("Achievement does not get deleted if user does").isTrue();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testDeleteCourse() throws Exception {
        var achievements = request.get("/api/courses/" + course.getId() + "/achievements", HttpStatus.OK, Set.class);
        assertThat(achievements.size()).as("Number of achievements for course should be 1").isEqualTo(1);

        courseRepository.delete(course);
        assertThat(achievementRepository.findAll().contains(achievement)).as("Achievement gets deleted if course does").isFalse();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateAchievement() throws Exception {
        instructor.addAchievement(achievement);
        userRepository.save(instructor);
        var achievementToUpdate = achievementRepository.findById(achievement.getId()).get();
        achievementToUpdate.setDescription("Updated achievement");
        request.put("/api/achievements", achievementToUpdate, HttpStatus.OK);
        assertThat(achievementRepository.findById(achievement.getId()).get()).as("Achievement is updated correctly").isEqualTo(achievementToUpdate);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteAchievement() throws Exception {
        instructor.addAchievement(achievement);
        instructor = userRepository.save(instructor);
        achievement = achievementRepository.save(achievement);
        request.delete("/api/achievements/" + achievement.getId(), HttpStatus.OK);

        assertThat(achievementRepository.findAll().size()).as("Achievement is deleted").isEqualTo(0);
        assertThat(userRepository.findById(instructor.getId()).isPresent()).as("User is not deleted").isTrue();
        assertThat(userRepository.findById(instructor.getId()).get().getAchievements().size()).as("User has no achievements").isEqualTo(0);
        assertThat(courseRepository.findById(course.getId()).isPresent()).as("Course is not deleted").isTrue();
        var achievements = request.get("/api/courses/" + course.getId() + "/achievements", HttpStatus.OK, Set.class);
        assertThat(achievements.size()).as("Course has no achievements").isEqualTo(0);
    }
}
