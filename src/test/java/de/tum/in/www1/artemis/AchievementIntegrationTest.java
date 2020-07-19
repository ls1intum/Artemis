package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Achievement;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.AchievementRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;

public class AchievementIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    AchievementRepository achievementRepository;

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
        achievement.setDescription("Create a correct many-to-many relation");
        achievement.setIcon("");
        achievement.setRank(1);

        achievement = achievementRepository.save(achievement);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @Transactional
    public void testManyToManyRelation() {
        assertThat(user.getAchievements().size()).isEqualTo(0);

        user.addAchievement(achievement);
        assertThat(user.getAchievements().size()).isEqualTo(1).as("Number of achievements for user should be 1");
        assertThat(achievement.getUsers().size()).isEqualTo(1).as("Number of users for achievement should be 1");

        course.addAchievement(achievement);
        assertThat(course.getAchievements().size()).isEqualTo(1).as("Number of achievements for course should be 1");
        assertThat(achievement.getCourses().size()).isEqualTo(1).as("Number of courses for achievement should be 1");
    }
}
