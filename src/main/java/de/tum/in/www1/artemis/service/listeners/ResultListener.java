package de.tum.in.www1.artemis.service.listeners;

import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AchievementType;
import de.tum.in.www1.artemis.repository.AchievementRepository;
import de.tum.in.www1.artemis.service.AchievementService;

@Component
public class ResultListener {

    private static AchievementService achievementService;

    private static AchievementRepository achievementRepository;

    private final Logger log = LoggerFactory.getLogger(ResultListener.class);

    @Autowired
    public void init(AchievementService achievementService, AchievementRepository achievementRepository) {
        this.achievementService = achievementService;
        this.achievementRepository = achievementRepository;
    }

    @PreUpdate
    @PrePersist
    public void checkForAchievements(Result result) {
        var course = result.getParticipation().getExercise().getCourseViaExerciseGroupOrCourseMember();

        var pointBasedAchievements = achievementRepository.findAllForRewardedTypeInCourse(course.getId(), AchievementType.POINT);
        log.debug("achievements found in resultListener : {}", pointBasedAchievements.size());
        log.debug("ResultListener was triggered with result : {}", result);
        if (result.getScore() != null && result.isRated() != null && result.isRated()) {
            achievementService.checkForAchievements(result);
        }
        log.debug("ResultListener was executed");
    }
}
