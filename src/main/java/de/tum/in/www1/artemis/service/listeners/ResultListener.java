package de.tum.in.www1.artemis.service.listeners;

import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.service.AchievementService;

@Configurable
public class ResultListener {

    private @Nullable ObjectFactory<AchievementService> achievementService;

    @Autowired
    public void setAchievementService(ObjectFactory<AchievementService> achievementService) {
        Assert.notNull(achievementService, "AuditingHandler must not be null!");
        this.achievementService = achievementService;
    }

    @PreUpdate
    @PrePersist
    public void checkForAchievements(Result result) {
        Assert.notNull(result, "Entity must not be null!");

        if (achievementService != null) {
            if (achievementService.getObject() != null) {
                if (result.getScore() != null && result.isRated() != null && result.isRated()) {
                    achievementService.getObject().checkForAchievements(result);
                }
            }
        }
    }
}
