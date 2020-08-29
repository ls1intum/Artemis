package de.tum.in.www1.artemis.service.listeners;

import javax.persistence.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.service.StudentScoreService;
import de.tum.in.www1.artemis.service.TutorScoreService;

@Component
public class ResultListener {

    private final Logger log = LoggerFactory.getLogger(ResultListener.class);

    // Note: this solution is not ideal, but everything else does not work, because of dependency injection problems with EntityListeners
    private static StudentScoreService studentScoreService;

    private static TutorScoreService tutorScoreService;

    @Autowired
    // we use lazy injection here, because a EntityListener needs an empty constructor
    public void setResultService(StudentScoreService studentScoreService) {
        ResultListener.studentScoreService = studentScoreService;
    }

    @Autowired
    // we use lazy injection here, because a EntityListener needs an empty constructor
    public void setTutorScoresService(TutorScoreService tutorScoreService) {
        ResultListener.tutorScoreService = tutorScoreService;
    }

    /**
     * After result gets deleted, delete all StudentScores/TutorScores with this result.
     *
     * @param deletedResult deleted result
     */
    @PostRemove
    public void postRemove(Result deletedResult) {
        log.info("Result " + deletedResult + " was deleted");
        // remove from Student Scores and Tutor Scores
        // studentScoreService.removeResult(deletedResult);
        // tutorScoreService.removeResult(deletedResult);
    }

    /**
     * After result gets updated, update all StudentScores/TutorScores with this result.
     *
     * @param updatedResult updated result
     */
    @PostUpdate
    public void postUpdate(Result updatedResult) {
        log.info("Result " + updatedResult + " was updated");
        // update existing student score
        // studentScoreService.updateResult(updatedResult);

        if (updatedResult.getAssessor() != null) {
            // update existing tutor scores
            // tutorScoreService.updateResult(updatedResult);
        }
    }

    /**
     * After result gets created, add/update StudentScores/TutorScores with this result.
     *
     * @param newResult newly created result
     */
    @PostPersist
    public void postPersist(Result newResult) {
        log.info("Result " + newResult + " was created");
        // add to student scores (or update existing one)
        studentScoreService.addNewResult(newResult);

        if (newResult.getAssessor() != null) {
            // add to tutor scores (or update existing one)
            // tutorScoreService.addNewResult(newResult);
        }
    }
}
