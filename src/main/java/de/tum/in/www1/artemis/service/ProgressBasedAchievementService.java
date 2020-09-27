package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AchievementRank;
import de.tum.in.www1.artemis.domain.enumeration.AchievementType;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;

@Service
public class ProgressBasedAchievementService {

    private final AchievementService achievementService;

    private final ParticipationService participationService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final int EXERCISES_AMOUNT_GOLD = 10;

    private final int EXERCISES_AMOUNT_SILVER = 8;

    private final int EXERCISES_AMOUNT_BRONZE = 6;

    private final long MIN_SCORE_TO_QUALIFY = 50L;

    public ProgressBasedAchievementService(AchievementService achievementService, ParticipationService participationService,
            StudentParticipationRepository studentParticipationRepository) {
        this.achievementService = achievementService;
        this.participationService = participationService;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    public void checkForAchievement(Result result) {
        var optionalUser = participationService.findOneStudentParticipation(result.getParticipation().getId()).getStudent();

        if (optionalUser.isPresent()) {
            var user = optionalUser.get();
            var course = result.getParticipation().getExercise().getCourseViaExerciseGroupOrCourseMember();
            var participations = studentParticipationRepository.findAllByCourseIdAndUserId(course.getId(), user.getId());
            var numberOfExercises = 0;
            for (var participation : participations) {
                if (participation.findLatestResult().getScore() >= MIN_SCORE_TO_QUALIFY) {
                    numberOfExercises++;
                }
            }

            if (numberOfExercises >= EXERCISES_AMOUNT_GOLD) {
                achievementService.rewardAchievement(course, null, AchievementType.PROGRESS, AchievementRank.GOLD, user);
            }
            else if (numberOfExercises >= EXERCISES_AMOUNT_SILVER) {
                achievementService.rewardAchievement(course, null, AchievementType.PROGRESS, AchievementRank.SILVER, user);
            }
            else if (numberOfExercises >= EXERCISES_AMOUNT_BRONZE) {
                achievementService.rewardAchievement(course, null, AchievementType.PROGRESS, AchievementRank.BRONZE, user);
            }
        }
    }

    public void generateAchievements(Course course) {
        achievementService.create("Course Master", "Solve at least " + EXERCISES_AMOUNT_GOLD + " exercises", "icon", AchievementRank.GOLD, AchievementType.PROGRESS, course, null);
        achievementService.create("Course Intermediate", "Solve at least " + EXERCISES_AMOUNT_SILVER + " exercises", "icon", AchievementRank.SILVER, AchievementType.PROGRESS,
                course, null);
        achievementService.create("Course Beginner", "Solve at least " + EXERCISES_AMOUNT_BRONZE + " exercises", "icon", AchievementRank.BRONZE, AchievementType.PROGRESS, course,
                null);
    }
}
