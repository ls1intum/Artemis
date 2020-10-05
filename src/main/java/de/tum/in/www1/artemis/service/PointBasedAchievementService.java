package de.tum.in.www1.artemis.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AchievementRank;
import de.tum.in.www1.artemis.domain.enumeration.AchievementType;
import de.tum.in.www1.artemis.repository.AchievementRepository;

@Service
public class PointBasedAchievementService {

    private final AchievementRepository achievementRepository;

    private final static long PERCENT_GOLD = 100L;

    private final static long PERCENT_SILVER = 80L;

    private final static long PERCENT_BRONZE = 60L;

    private final static long PERCENT_UNRANKED = 50L;

    public PointBasedAchievementService(AchievementRepository achievementRepository) {
        this.achievementRepository = achievementRepository;
    }

    public AchievementRank checkForAchievement(Result result) {
        var score = result.getScore();

        if (score == null) {
            return null;
        }

        if (score >= PERCENT_GOLD) {
            return AchievementRank.GOLD;
        }
        else if (score >= PERCENT_SILVER) {
            return AchievementRank.SILVER;
        }
        else if (score >= PERCENT_BRONZE) {
            return AchievementRank.BRONZE;
        }
        else if (score >= PERCENT_UNRANKED) {
            return AchievementRank.UNRANKED;
        }
        return null;
    }

    /**
     * Generates all point based achievements for an exercise
     * @param exercise
     */
    public void generateAchievements(Exercise exercise) {
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        Set<Achievement> achievementsToSave = new HashSet<>();
        achievementsToSave.add(new Achievement("Point Master", "Score " + PERCENT_GOLD + " percent of the points in exercise " + exercise.getTitle(), "award", AchievementRank.GOLD,
                AchievementType.POINT, course, exercise));
        achievementsToSave.add(new Achievement("Point Intermediate", "Score at least" + PERCENT_SILVER + " percent of the points in exercise " + exercise.getTitle(), "award",
                AchievementRank.SILVER, AchievementType.POINT, course, exercise));
        achievementsToSave.add(new Achievement("Point Beginner", "Score at least" + PERCENT_BRONZE + " percent of the points in exercise " + exercise.getTitle(), "award",
                AchievementRank.BRONZE, AchievementType.POINT, course, exercise));
        achievementsToSave.add(new Achievement("Point Amateur", "Score at least" + PERCENT_UNRANKED + " percent of the points in exercise " + exercise.getTitle(), "award",
                AchievementRank.UNRANKED, AchievementType.POINT, course, exercise));

        achievementRepository.saveAll(achievementsToSave);
    }
}
