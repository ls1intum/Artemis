package de.tum.in.www1.artemis.service.listeners;

import javax.persistence.PostPersist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.service.TutorScoreService;

@Component
public class ComplaintListener {

    private final Logger log = LoggerFactory.getLogger(ComplaintListener.class);

    private static TutorScoreService tutorScoreService;

    @Autowired
    // we use lazy injection here, because a EntityListener needs an empty constructor
    public void setTutorScoresService(TutorScoreService tutorScoreService) {
        ComplaintListener.tutorScoreService = tutorScoreService;
    }

    /**
     * After new complaint gets persisted, update all StudentScores/TutorScores with this complaint.
     *
     * @param newComplaint new complaint
     */
    @PostPersist
    public void postPersist(Complaint newComplaint) {
        log.info("Complaint " + newComplaint + " got persisted");

        // update tutor scores
        log.info("TutorScores for Complaint " + newComplaint + " will be updated");
        tutorScoreService.addComplaintOrFeedbackRequest(newComplaint);
    }
}
