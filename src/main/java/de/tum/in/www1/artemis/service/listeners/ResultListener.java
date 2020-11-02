package de.tum.in.www1.artemis.service.listeners;

import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.service.AchievementService;

@Component
public class ResultListener {

    private static AchievementService achievementService;

    @Autowired
    public void setAchievementService(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @PreUpdate
    @PrePersist
    public void checkForAchievements(Result result) {
        if (result.getScore() != null && result.isRated() != null && result.isRated()) {
            achievementService.checkForAchievements(result);
        }
    }
}
