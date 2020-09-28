package de.tum.in.www1.artemis.service;

import static java.time.temporal.ChronoUnit.DAYS;

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

    private final long MIN_SCORE_TO_QUALIFY = 50L;

    public TimeBasedAchievementService(AchievementRepository achievementRepository) {
        this.achievementRepository = achievementRepository;
    }

    public AchievementRank checkForAchievement(Result result) {
        if (result.getScore() < MIN_SCORE_TO_QUALIFY) {
            return null;
        }

        var submissionDay = result.getSubmission().getSubmissionDate().truncatedTo(DAYS);
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
        return null;
    }

    public void generateAchievements(Exercise exercise) {
        var course = exercise.getCourseViaExerciseGroupOrCourseMember();
        achievementRepository.save(new Achievement("Time Master", "Hand in your submission within " + DAYS_GOLD + " day after the release for exercise: " + exercise.getTitle(),
                "icon", AchievementRank.GOLD, AchievementType.TIME, course, exercise));
        achievementRepository
                .save(new Achievement("Time Intermediate", "Hand in your submission within " + DAYS_SILVER + " days after the release for exercise: " + exercise.getTitle(), "icon",
                        AchievementRank.SILVER, AchievementType.TIME, course, exercise));
        achievementRepository
                .save(new Achievement("Time Beginner", "Hand in your submission within " + DAYS_BRONZE + " days after the release for exercise: " + exercise.getTitle(), "icon",
                        AchievementRank.BRONZE, AchievementType.TIME, course, exercise));
    }
}
