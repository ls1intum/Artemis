package de.tum.in.www1.artemis.service.listeners;

import javax.persistence.PostPersist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.service.TutorScoreService;

@Component
public class ComplaintResponseListener {

    private final Logger log = LoggerFactory.getLogger(ComplaintResponseListener.class);

    private static TutorScoreService tutorScoreService;

    @Autowired
    // we use lazy injection here, because a EntityListener needs an empty constructor
    public void setTutorScoresService(TutorScoreService tutorScoreService) {
        ComplaintResponseListener.tutorScoreService = tutorScoreService;
    }

    /**
     * After new complaint response gets persisted, update all StudentScores/TutorScores with this complaint response.
     *
     * @param newComplaintResponse new complaint response
     */
    @PostPersist
    public void postPersist(ComplaintResponse newComplaintResponse) {
        log.info("ComplaintResponse " + newComplaintResponse + " got persisted");

        // update tutor scores
        log.info("TutorScores for ComplaintResponse " + newComplaintResponse + " will be updated");
        // tutorScoreService.addComplaintResponseOrAnsweredFeedbackRequest(newComplaintResponse);
    }
}
