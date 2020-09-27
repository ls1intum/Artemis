package de.tum.in.www1.artemis.service;

import static java.time.temporal.ChronoUnit.DAYS;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AchievementRank;
import de.tum.in.www1.artemis.domain.enumeration.AchievementType;

@Service
public class TimeBasedAchievementService {

    private final AchievementService achievementService;

    private final ParticipationService participationService;

    private final long DAYS_GOLD = 1L;

    private final long DAYS_SILVER = 2L;

    private final long DAYS_BRONZE = 3L;

    private final long MIN_SCORE_TO_QUALIFY = 50L;

    public TimeBasedAchievementService(AchievementService achievementService, ParticipationService participationService) {
        this.achievementService = achievementService;
        this.participationService = participationService;
    }

    public void checkForAchievement(Result result) {
        if (result.getScore() < MIN_SCORE_TO_QUALIFY) {
            return;
        }

        var submissionDay = result.getSubmission().getSubmissionDate().truncatedTo(DAYS);
        var exerciseReleaseDay = result.getParticipation().getExercise().getReleaseDate().truncatedTo(DAYS);
        var optionalUser = participationService.findOneStudentParticipation(result.getParticipation().getId()).getStudent();
        var exercise = result.getParticipation().getExercise();
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();

        if (optionalUser.isPresent()) {
            var user = optionalUser.get();

            if (submissionDay.minusDays(DAYS_GOLD).isEqual(exerciseReleaseDay)) {
                achievementService.rewardAchievement(course, exercise, AchievementType.TIME, AchievementRank.GOLD, user);
            }
            else if (submissionDay.minusDays(DAYS_SILVER).isEqual(exerciseReleaseDay)) {
                achievementService.rewardAchievement(course, exercise, AchievementType.TIME, AchievementRank.SILVER, user);
            }
            else if (submissionDay.minusDays(DAYS_BRONZE).isEqual(exerciseReleaseDay)) {
                achievementService.rewardAchievement(course, exercise, AchievementType.TIME, AchievementRank.BRONZE, user);
            }
        }
    }

    public void generateAchievements(Exercise exercise) {
        achievementService.create("Time Master", "Hand in your submission within " + DAYS_GOLD + " day after the release for exercise: " + exercise.getTitle(), "icon",
                AchievementRank.GOLD, AchievementType.TIME, exercise.getCourseViaExerciseGroupOrCourseMember(), exercise);
        achievementService.create("Time Intermediate", "Hand in your submission within " + DAYS_SILVER + " days after the release for exercise: " + exercise.getTitle(), "icon",
                AchievementRank.SILVER, AchievementType.TIME, exercise.getCourseViaExerciseGroupOrCourseMember(), exercise);
        achievementService.create("Time Beginner", "Hand in your submission within " + DAYS_BRONZE + " days after the release for exercise: " + exercise.getTitle(), "icon",
                AchievementRank.BRONZE, AchievementType.TIME, exercise.getCourseViaExerciseGroupOrCourseMember(), exercise);
    }
}
