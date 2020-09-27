package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AchievementRank;
import de.tum.in.www1.artemis.domain.enumeration.AchievementType;

@Service
public class PointBasedAchievementService {

    private final AchievementService achievementService;

    private final ParticipationService participationService;

    private final long PERCENT_GOLD = 100L;

    private final long PERCENT_SILVER = 80L;

    private final long PERCENT_BRONZE = 60L;

    public PointBasedAchievementService(AchievementService achievementService, ParticipationService participationService) {
        this.achievementService = achievementService;
        this.participationService = participationService;
    }

    public void checkForAchievement(Result result) {
        var score = result.getScore();
        var optionalUser = participationService.findOneStudentParticipation(result.getParticipation().getId()).getStudent();
        var exercise = result.getParticipation().getExercise();
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();

        if (optionalUser.isPresent()) {
            var user = optionalUser.get();

            if (score >= PERCENT_GOLD) {
                achievementService.rewardAchievement(course, exercise, AchievementType.POINT, AchievementRank.GOLD, user);
            }
            else if (score >= PERCENT_SILVER) {
                achievementService.rewardAchievement(course, exercise, AchievementType.POINT, AchievementRank.SILVER, user);
            }
            else if (score >= PERCENT_BRONZE) {
                achievementService.rewardAchievement(course, exercise, AchievementType.POINT, AchievementRank.BRONZE, user);
            }
        }
    }

    public void generateAchievements(Exercise exercise) {
        achievementService.create("Point Master", "Score " + PERCENT_GOLD + "% of the points in exercise: " + exercise.getTitle(), "icon", AchievementRank.GOLD,
                AchievementType.POINT, exercise.getCourseViaExerciseGroupOrCourseMember(), exercise);
        achievementService.create("Point Intermediate", "Score at least" + PERCENT_SILVER + "% of the points in exercise: " + exercise.getTitle(), "icon", AchievementRank.SILVER,
                AchievementType.POINT, exercise.getCourseViaExerciseGroupOrCourseMember(), exercise);
        achievementService.create("Point Beginner", "Score at least" + PERCENT_BRONZE + "% of the points in exercise: " + exercise.getTitle(), "icon", AchievementRank.BRONZE,
                AchievementType.POINT, exercise.getCourseViaExerciseGroupOrCourseMember(), exercise);
    }
}
