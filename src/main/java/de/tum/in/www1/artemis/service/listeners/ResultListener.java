package de.tum.in.www1.artemis.service.listeners;

import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.service.AchievementService;

@Component
public class ResultListener {

    private static AchievementService achievementService;

    private final Logger log = LoggerFactory.getLogger(ResultListener.class);

    @Autowired
    public void setAchievementService(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @PreUpdate
    @PrePersist
    public void checkForAchievements(Result result) {
        log.debug("ResultListener was triggered with result : {}", result);
        if (result.getScore() != null && result.isRated() != null && result.isRated()) {
            achievementService.checkForAchievements(result);
        }
        log.debug("ResultListener was executed");
    }
}
