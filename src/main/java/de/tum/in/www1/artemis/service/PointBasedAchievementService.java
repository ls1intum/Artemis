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

    private final static long percentGold = 100L;

    private final static long percentSilver = 80L;

    private final static long percentBronze = 60L;

    private final static long percentUnranked = 50L;

    public PointBasedAchievementService(AchievementRepository achievementRepository) {
        this.achievementRepository = achievementRepository;
    }

    /**
     * Generates all point based achievements for an exercise
     * @param exercise
     */
    public void generateAchievements(Exercise exercise) {
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        Set<Achievement> achievementsToSave = new HashSet<>();
        achievementsToSave.add(new Achievement("Point Master", "Score " + percentGold + " percent of the points in exercise " + exercise.getTitle(), "award", AchievementRank.GOLD,
                AchievementType.POINT, course, exercise));
        achievementsToSave.add(new Achievement("Point Intermediate", "Score at least" + percentSilver + " percent of the points in exercise " + exercise.getTitle(), "award",
                AchievementRank.SILVER, AchievementType.POINT, course, exercise));
        achievementsToSave.add(new Achievement("Point Beginner", "Score at least" + percentBronze + " percent of the points in exercise " + exercise.getTitle(), "award",
                AchievementRank.BRONZE, AchievementType.POINT, course, exercise));
        achievementsToSave.add(new Achievement("Point Amateur", "Score at least" + percentUnranked + " percent of the points in exercise " + exercise.getTitle(), "award",
                AchievementRank.UNRANKED, AchievementType.POINT, course, exercise));

        achievementRepository.saveAll(achievementsToSave);
    }
}
