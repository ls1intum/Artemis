package de.tum.in.www1.artemis.service.listeners;

import javax.persistence.PostPersist;
import javax.persistence.PostUpdate;
import javax.persistence.PreRemove;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.service.ScoreService;

/**
 * Important: As the ResultListener potentially will be called from a situation where no {@link org.springframework.security.core.Authentication}
 * is available (for example from a scheduled service or from a websocket service), you can NOT use anything that requires
 * authentication in the listener. Most importantly this means that you can not use any custom @Query Methods!
 * <p>
 * A workaround can be found in {@link ScoreService#removeOrUpdateAssociatedParticipantScore(Result)} where
 * we check if an authentication is available and if it is not, we set a dummy authentication.
 */
@Component
public class ResultListener {

    private ScoreService scoreService;

    /**
     * While {@link javax.persistence.EntityManager} is being initialized it instantiates {@link javax.persistence.EntityListeners} including
     * {@link ResultListener}. Now {@link ResultListener} requires the {@link ScoreService} which requires {@link de.tum.in.www1.artemis.repository.StudentScoreRepository}
     * which requires {@link javax.persistence.EntityManager}. To break this circular dependency we use lazy injection of the service here.
     *
     * @param scoreService the student score service that will be lazily injected by Spring
     */
    public ResultListener(@Lazy ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    /**
     * Remove or update associated participation scores before a result is removed
     * <p>
     * Will be called by Hibernate BEFORE a result is deleted from the database.
     *
     * @param resultToBeDeleted result about to be removed
     */
    @PreRemove
    public void removeOrUpdateAssociatedParticipantScore(Result resultToBeDeleted) {
        scoreService.removeOrUpdateAssociatedParticipantScore(resultToBeDeleted);
    }

    /**
     * Update or create a new participation score after a result is created or updated
     * <p>
     * Will be called by Hibernate AFTER a result is updated or created
     *
     * @param createdOrUpdatedResult created or updated result
     */
    @PostUpdate
    @PostPersist
    public void updateOrCreateParticipantScore(Result createdOrUpdatedResult) {
        scoreService.updateOrCreateParticipantScore(createdOrUpdatedResult);
    }
}
