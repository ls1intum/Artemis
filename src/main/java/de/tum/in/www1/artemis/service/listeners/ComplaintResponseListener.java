package de.tum.in.www1.artemis.service.listeners;

import javax.persistence.PostPersist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.service.TutorScoreService;

@Component
public class ComplaintResponseListener {

    private final Logger log = LoggerFactory.getLogger(ComplaintResponseListener.class);

    private @Nullable ObjectFactory<TutorScoreService> tutorScoreService;

    @Autowired
    public void setTutorScoreService(ObjectFactory<TutorScoreService> tutorScoreService) {
        Assert.notNull(tutorScoreService, "TutorScoreService must not be null!");
        this.tutorScoreService = tutorScoreService;
    }

    /**
     * After new complaint response gets persisted, update all TutorScores with this complaint response.
     *
     * @param newComplaintResponse new complaint response
     */
    @PostPersist
    public void postPersist(ComplaintResponse newComplaintResponse) {
        // update tutor scores
        tutorScoreService.getObject().addComplaintResponseOrAnsweredFeedbackRequest(newComplaintResponse);
    }
}
