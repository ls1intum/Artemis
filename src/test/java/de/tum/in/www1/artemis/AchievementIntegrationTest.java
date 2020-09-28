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
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AchievementRank;
import de.tum.in.www1.artemis.domain.enumeration.AchievementType;
import de.tum.in.www1.artemis.repository.AchievementRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AchievementService;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.ExerciseService;
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
    CourseService courseService;

    @Autowired
    ExerciseService exerciseService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AchievementRepository achievementRepository;

    private User student;

    private User instructor;

    private Course first_course;

    private Course second_course;

    private Exercise first_exercise;

    private Achievement first_achievement;

    private Achievement second_achievement;

    private Achievement third_achievement;

    @BeforeEach
    public void initTestCase() {
        var users = database.addUsers(1, 0, 1);
        student = users.get(0);
        instructor = users.get(1);
        instructor.setGroups(new HashSet<>(Arrays.asList("instructor")));
        first_course = database.addCourseWithModelingAndTextAndFileUploadExercise();
        second_course = database.addCourseWithModelingAndTextAndFileUploadExercise();
        first_exercise = first_course.getExercises().stream().findFirst().get();

        first_achievement = achievementRepository.save(
                new Achievement("Test Achievement", "Create correct relations", "test-icon", AchievementRank.UNRANKED, AchievementType.PROGRESS, first_course, first_exercise));
        second_achievement = achievementRepository
                .save(new Achievement("Test Achievement", "Get 100 percent test coverage", "test-icon", AchievementRank.GOLD, AchievementType.POINT, first_course, null));
        third_achievement = achievementRepository
                .save(new Achievement("Test Achievement", "Get PR ready to be merged", "test-icon", AchievementRank.SILVER, AchievementType.TIME, second_course, first_exercise));
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    public void testManyToManyRelationToUser() {
        assertThat(student.getAchievements().size()).as("Number of achievements for user should be 0").isEqualTo(0);

        student.addAchievement(first_achievement);
        assertThat(student.getAchievements().size()).as("Number of achievements for user should be 1").isEqualTo(1);
        assertThat(student.getAchievements().contains(first_achievement)).as("User has correct achievement").isTrue();
        assertThat(first_achievement.getUsers().size()).as("Number of users for achievement should be 1").isEqualTo(1);
        assertThat(first_achievement.getUsers().contains(student)).as("Achievement has correct user").isTrue();

        student.removeAchievement(first_achievement);
        assertThat(student.getAchievements().size()).as("Number of achievements for user should be 0").isEqualTo(0);
        assertThat(student.getAchievements().contains(first_achievement)).as("User does not have removed achievement").isFalse();
        assertThat(first_achievement.getUsers().size()).as("Number of users for achievement should be 0").isEqualTo(0);
        assertThat(first_achievement.getUsers().contains(student)).as("Achievement does not have incorrect user").isFalse();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testDeleteUser() throws Exception {
        student.addAchievement(first_achievement);
        student = userRepository.save(student);

        var achievements = request.get("/api/achievements", HttpStatus.OK, Set.class);
        assertThat(achievements.size()).as("Number of achievements for user should be 1").isEqualTo(1);

        userRepository.delete(student);
        assertThat(achievementService.findAll().contains(first_achievement)).as("Achievement does not get deleted if user does").isTrue();
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void testDeleteCourse() throws Exception {
        student.addAchievement(first_achievement);
        student = userRepository.save(student);

        var achievements = request.get("/api/courses/" + first_course.getId() + "/achievements", HttpStatus.OK, Set.class);
        assertThat(achievements.size()).as("Number of achievements for course should be 2").isEqualTo(2);

        request.delete("/api/courses/" + first_course.getId(), HttpStatus.OK);
        assertThat(achievementService.findAll().contains(first_achievement)).as("Achievement gets deleted if course does").isFalse();
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void testDeleteExercise() throws Exception {
        student.addAchievement(first_achievement);
        student = userRepository.save(student);

        var achievements = achievementService.findAllByExerciseId(first_exercise.getId());
        assertThat(achievements.size()).as("Number of achievements for course should be 2").isEqualTo(2);

        exerciseService.delete(first_exercise.getId(), false, false);
        assertThat(achievementService.findById(first_achievement.getId())).as("Achievement gets deleted if exercise does").isNotPresent();
        assertThat(achievementService.findById(third_achievement.getId())).as("Achievement gets deleted if exercise does").isNotPresent();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateAchievement() throws Exception {
        instructor.addAchievement(first_achievement);
        userRepository.save(instructor);
        var achievementToUpdate = achievementService.findById(first_achievement.getId()).get();
        achievementToUpdate.setDescription("Updated achievement");
        request.put("/api/achievements", achievementToUpdate, HttpStatus.OK);
        assertThat(achievementService.findById(first_achievement.getId()).get()).as("Achievement is updated correctly").isEqualTo(achievementToUpdate);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteAchievement() throws Exception {
        instructor.addAchievement(first_achievement);
        instructor = userRepository.save(instructor);
        first_achievement = achievementRepository.save(first_achievement);
        request.delete("/api/achievements/" + first_achievement.getId(), HttpStatus.OK);

        assertThat(achievementService.findById(first_achievement.getId())).as("Achievement is deleted").isNotPresent();
        assertThat(userRepository.findById(instructor.getId()).isPresent()).as("User is not deleted").isTrue();
        assertThat(request.get("/api/achievements", HttpStatus.OK, Set.class).size()).as("User has no achievements").isEqualTo(0);
        assertThat(courseService.findOne(first_course.getId())).as("Course is not deleted").isNotNull();
        var achievements = request.get("/api/courses/" + first_course.getId() + "/achievements", HttpStatus.OK, Set.class);
        assertThat(achievements.size()).as("Course has only one achievement").isEqualTo(1);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testBadRequests() throws Exception {
        var emptyAchievement = new Achievement("", "", "", null, null, null, null);
        request.put("/api/achievements", emptyAchievement, HttpStatus.BAD_REQUEST);
        request.delete("/api/achievements/" + 999L, HttpStatus.NOT_FOUND);
        instructor.setGroups(new HashSet<>());
        userRepository.save(instructor);
        emptyAchievement.setId(2L);
        emptyAchievement.setCourse(first_course);
        request.put("/api/achievements", emptyAchievement, HttpStatus.FORBIDDEN);
        request.delete("/api/achievements/" + first_achievement.getId(), HttpStatus.FORBIDDEN);
    }

}
