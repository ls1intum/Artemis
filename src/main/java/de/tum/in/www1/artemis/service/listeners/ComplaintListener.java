package de.tum.in.www1.artemis.service.listeners;

import javax.persistence.PostPersist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.service.TutorScoreService;

@Component
public class ComplaintListener {

    private final Logger log = LoggerFactory.getLogger(ComplaintListener.class);

    private @Nullable ObjectFactory<TutorScoreService> tutorScoreService;

    @Autowired
    public void setTutorScoreService(ObjectFactory<TutorScoreService> tutorScoreService) {
        Assert.notNull(tutorScoreService, "TutorScoreService must not be null!");
        this.tutorScoreService = tutorScoreService;
    }

    /**
     * After new complaint gets persisted, update all TutorScores with this complaint.
     *
     * @param newComplaint new complaint
     */
    @PostPersist
    public void postPersist(Complaint newComplaint) {
        log.info("Complaint " + newComplaint + " got persisted");

        // update tutor scores
        tutorScoreService.getObject().addComplaintOrFeedbackRequest(newComplaint);
    }
}
