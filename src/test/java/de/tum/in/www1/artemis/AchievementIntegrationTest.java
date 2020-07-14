package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.domain.Achievement;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.AchievementRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;

public class AchievementIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    AchievementRepository achievementRepository;

    private User user;

    private Set<Achievement> achievements;

    private Achievement achievement;

    @BeforeEach
    public void initTestCase() {
        user = database.addUsers(1, 0, 0).get(0);

        achievement = new Achievement();
        achievement.setId(1L);
        achievement.setTitle("Test Achievement");
        achievement.setDescription("Create a correct many-to-many relation");
        achievement.setIcon("");
        achievement.setRank(1);

        achievement = achievementRepository.save(achievement);

        achievements = new HashSet<>();
        achievements.add(achievement);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    public void testManyToManyRelation() {
        assertThat(user.getAchievements().size()).isEqualTo(0);
        user.setAchievements(achievements);
        var id = achievement.getId();
        achievement = achievementRepository.getOne(id);
        var as = achievementRepository.findAll();
        assertThat(user.getAchievements().size()).isEqualTo(1);
        assertThat(achievement.getUsers().size()).isEqualTo(1);
    }
}
