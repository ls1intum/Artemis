package de.tum.in.www1.artemis.service;

import static java.time.temporal.ChronoUnit.DAYS;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AchievementRank;
import de.tum.in.www1.artemis.domain.enumeration.AchievementType;
import de.tum.in.www1.artemis.repository.AchievementRepository;

@Service
public class TimeBasedAchievementService {

    private final AchievementRepository achievementRepository;

    private final long DAYS_GOLD = 1L;

    private final long DAYS_SILVER = 2L;

    private final long DAYS_BRONZE = 3L;

    private final long DAYS_UNRANKED = 4L;

    private final long MIN_SCORE_TO_QUALIFY = 50L;

    public TimeBasedAchievementService(AchievementRepository achievementRepository) {
        this.achievementRepository = achievementRepository;
    }

    public AchievementRank checkForAchievement(Result result) {
        if (result.getScore() == null || result.getScore() < MIN_SCORE_TO_QUALIFY) {
            return null;
        }
        var submission = result.getSubmission();
        if (submission == null) {
            return null;
        }

        var submissionDay = submission.getSubmissionDate().truncatedTo(DAYS);
        var exerciseReleaseDay = result.getParticipation().getExercise().getReleaseDate().truncatedTo(DAYS);

        if (submissionDay.minusDays(DAYS_GOLD).isEqual(exerciseReleaseDay)) {
            return AchievementRank.GOLD;
        }
        else if (submissionDay.minusDays(DAYS_SILVER).isEqual(exerciseReleaseDay)) {
            return AchievementRank.SILVER;
        }
        else if (submissionDay.minusDays(DAYS_BRONZE).isEqual(exerciseReleaseDay)) {
            return AchievementRank.BRONZE;
        }
        else if (submissionDay.minusDays(DAYS_UNRANKED).isEqual(exerciseReleaseDay)) {
            return AchievementRank.UNRANKED;
        }
        return null;
    }

    public void generateAchievements(Exercise exercise) {
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        Set<Achievement> achievementsToSave = new HashSet<>();
        achievementsToSave.add(new Achievement("Time Master", "Hand in your submission within " + DAYS_GOLD + " day after the release for exercise: " + exercise.getTitle(),
                "calendar-check", AchievementRank.GOLD, AchievementType.TIME, course, exercise));
        achievementsToSave
                .add(new Achievement("Time Intermediate", "Hand in your submission within " + DAYS_SILVER + " days after the release for exercise: " + exercise.getTitle(),
                        "calendar-check", AchievementRank.SILVER, AchievementType.TIME, course, exercise));
        achievementsToSave.add(new Achievement("Time Beginner", "Hand in your submission within " + DAYS_BRONZE + " days after the release for exercise: " + exercise.getTitle(),
                "calendar-check", AchievementRank.BRONZE, AchievementType.TIME, course, exercise));
        achievementsToSave.add(new Achievement("Time Amateur", "Hand in your submission within " + DAYS_UNRANKED + " days after the release for exercise: " + exercise.getTitle(),
                "calendar-check", AchievementRank.UNRANKED, AchievementType.TIME, course, exercise));

        achievementRepository.saveAll(achievementsToSave);
    }
}
