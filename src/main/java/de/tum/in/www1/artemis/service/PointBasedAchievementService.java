package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AchievementRank;
import de.tum.in.www1.artemis.domain.enumeration.AchievementType;
import de.tum.in.www1.artemis.repository.AchievementRepository;

@Service
public class PointBasedAchievementService {

    private final AchievementRepository achievementRepository;

    private final long PERCENT_GOLD = 100L;

    private final long PERCENT_SILVER = 80L;

    private final long PERCENT_BRONZE = 60L;

    public PointBasedAchievementService(AchievementRepository achievementRepository) {
        this.achievementRepository = achievementRepository;
    }

    public AchievementRank checkForAchievement(Result result) {
        var score = result.getScore();

        if (score >= PERCENT_GOLD) {
            return AchievementRank.GOLD;
        }
        else if (score >= PERCENT_SILVER) {
            return AchievementRank.SILVER;
        }
        else if (score >= PERCENT_BRONZE) {
            return AchievementRank.BRONZE;
        }
        return null;
    }

    public void generateAchievements(Exercise exercise) {
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        achievementRepository.save(new Achievement("Point Master", "Score " + PERCENT_GOLD + "% of the points in exercise: " + exercise.getTitle(), "icon", AchievementRank.GOLD,
                AchievementType.POINT, course, exercise));
        achievementRepository.save(new Achievement("Point Intermediate", "Score at least" + PERCENT_SILVER + "% of the points in exercise: " + exercise.getTitle(), "icon",
                AchievementRank.SILVER, AchievementType.POINT, course, exercise));
        achievementRepository.save(new Achievement("Point Beginner", "Score at least" + PERCENT_BRONZE + "% of the points in exercise: " + exercise.getTitle(), "icon",
                AchievementRank.BRONZE, AchievementType.POINT, course, exercise));
    }
}
