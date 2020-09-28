package de.tum.in.www1.artemis.service;

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

    private final int EXERCISES_AMOUNT_GOLD = 10;

    private final int EXERCISES_AMOUNT_SILVER = 8;

    private final int EXERCISES_AMOUNT_BRONZE = 6;

    private final long MIN_SCORE_TO_QUALIFY = 50L;

    public ProgressBasedAchievementService(StudentParticipationRepository studentParticipationRepository, AchievementRepository achievementRepository) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.achievementRepository = achievementRepository;
    }

    public AchievementRank checkForAchievement(Course course, User user) {
        var participations = studentParticipationRepository.findAllByCourseIdAndUserId(course.getId(), user.getId());
        var numberOfExercises = 0;
        for (var participation : participations) {
            if (participation.findLatestResult().getScore() >= MIN_SCORE_TO_QUALIFY) {
                numberOfExercises++;
            }
        }

        if (numberOfExercises >= EXERCISES_AMOUNT_GOLD) {
            return AchievementRank.GOLD;
        }
        else if (numberOfExercises >= EXERCISES_AMOUNT_SILVER) {
            return AchievementRank.SILVER;
        }
        else if (numberOfExercises >= EXERCISES_AMOUNT_BRONZE) {
            return AchievementRank.BRONZE;
        }
        return null;
    }

    public void generateAchievements(Course course) {
        achievementRepository.save(
                new Achievement("Course Master", "Solve at least " + EXERCISES_AMOUNT_GOLD + " exercises", "icon", AchievementRank.GOLD, AchievementType.PROGRESS, course, null));
        achievementRepository.save(new Achievement("Course Intermediate", "Solve at least " + EXERCISES_AMOUNT_SILVER + " exercises", "icon", AchievementRank.SILVER,
                AchievementType.PROGRESS, course, null));
        achievementRepository.save(new Achievement("Course Beginner", "Solve at least " + EXERCISES_AMOUNT_BRONZE + " exercises", "icon", AchievementRank.BRONZE,
                AchievementType.PROGRESS, course, null));
    }
}
