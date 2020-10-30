package de.tum.in.www1.artemis.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AchievementRank;
import de.tum.in.www1.artemis.domain.enumeration.AchievementType;
import de.tum.in.www1.artemis.repository.AchievementRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;

@Service
public class ProgressBasedAchievementService {

    private final StudentParticipationRepository studentParticipationRepository;

    private final AchievementRepository achievementRepository;

    private final static long EXERCISES_AMOUNT_GOLD = 10L;

    private final static long EXERCISES_AMOUNT_SILVER = 8L;

    private final static long EXERCISES_AMOUNT_BRONZE = 5L;

    private final static long EXERCISES_AMOUNT_UNRANKED = 1L;

    private final static long MIN_SCORE_TO_QUALIFY = 50L;

    public ProgressBasedAchievementService(StudentParticipationRepository studentParticipationRepository, AchievementRepository achievementRepository) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.achievementRepository = achievementRepository;
    }

    public AchievementRank checkForAchievement(Course course, User user, Set<Achievement> achievements) {
        var participations = studentParticipationRepository.findAllByCourseIdAndUserId(course.getId(), user.getId());
        var numberOfExercises = 0;
        for (var participation : participations) {
            var latestResult = participation.findLatestResult();
            if (latestResult == null) {
                continue;
            }
            var score = latestResult.getScore();
            if (score != null && score >= achievements.iterator().next().getMinScoreToQualify()) {
                numberOfExercises++;
            }
        }

        Set<AchievementRank> ranks = new HashSet<>();

        for (Achievement achievement : achievements) {
            if (numberOfExercises >= achievement.getSuccessCriteria()) {
                ranks.add(achievement.getRank());
            }
        }

        var maxRank = ranks.stream().max(Comparator.comparing(AchievementRank::ordinal));

        return maxRank.isPresent() ? maxRank.get() : null;
    }

    /**
     * Generates all progress based achievements for a course
     * @param course
     */
    public void generateAchievements(Course course) {
        Set<Achievement> achievementsToSave = new HashSet<>();
        achievementsToSave.add(new Achievement("Course Master", "Solve at least " + EXERCISES_AMOUNT_GOLD + " exercises", "tasks", AchievementRank.GOLD, AchievementType.PROGRESS,
                EXERCISES_AMOUNT_GOLD, MIN_SCORE_TO_QUALIFY, course, null));
        achievementsToSave.add(new Achievement("Course Intermediate", "Solve at least " + EXERCISES_AMOUNT_SILVER + " exercises", "tasks", AchievementRank.SILVER,
                AchievementType.PROGRESS, EXERCISES_AMOUNT_SILVER, MIN_SCORE_TO_QUALIFY, course, null));
        achievementsToSave.add(new Achievement("Course Beginner", "Solve at least " + EXERCISES_AMOUNT_BRONZE + " exercises", "tasks", AchievementRank.BRONZE,
                AchievementType.PROGRESS, EXERCISES_AMOUNT_BRONZE, MIN_SCORE_TO_QUALIFY, course, null));
        achievementsToSave.add(new Achievement("Course Amateur", "Solve your first exercise", "tasks", AchievementRank.UNRANKED, AchievementType.PROGRESS,
                EXERCISES_AMOUNT_UNRANKED, MIN_SCORE_TO_QUALIFY, course, null));

        achievementRepository.saveAll(achievementsToSave);
    }
}
