package de.tum.in.www1.artemis.service.listeners;

import javax.persistence.PostPersist;
import javax.persistence.PreRemove;
import javax.persistence.PreUpdate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.service.StudentScoreService;
import de.tum.in.www1.artemis.service.TutorScoreService;

@Configurable
public class ResultListener {

    private final Logger log = LoggerFactory.getLogger(ResultListener.class);

    private @Nullable ObjectFactory<StudentScoreService> studentScoreService;

    private @Nullable ObjectFactory<TutorScoreService> tutorScoreService;

    @Autowired
    public void setStudentScoreService(ObjectFactory<StudentScoreService> studentScoreService) {
        Assert.notNull(studentScoreService, "StudentScoreService must not be null!");
        this.studentScoreService = studentScoreService;
    }

    @Autowired
    public void setTutorScoreService(ObjectFactory<TutorScoreService> tutorScoreService) {
        Assert.notNull(tutorScoreService, "TutorScoreService must not be null!");
        this.tutorScoreService = tutorScoreService;
    }

    /**
     * Before result gets deleted, delete all StudentScores/TutorScores with this result.
     *
     * @param deletedResult deleted result
     */
    @PreRemove
    public void postRemove(Result deletedResult) {
        log.info("Result " + deletedResult + " was deleted");

        // remove from Student Scores and Tutor Scores
        studentScoreService.getObject().removeResult(deletedResult);
        tutorScoreService.getObject().removeResult(deletedResult);
    }

    /**
     * Before result gets updated, remove result from all TutorScores.
     *
     * @param updatedResult updated result
     */
    @PreUpdate
    public void preUpdate(Result updatedResult) {
        log.info("Result " + updatedResult + " will be updated");

        // update scores
        studentScoreService.getObject().updateResult(updatedResult);

        if (updatedResult.getAssessor() != null) {
            tutorScoreService.getObject().updateResult(updatedResult);
        }
    }

    /**
     * After new result gets persisted, update all StudentScores/TutorScores with this result.
     *
     * @param newResult new result
     */
    @PostPersist
    public void postPersist(Result newResult) {
        log.info("Result " + newResult + " was persisted");

        // update score
        studentScoreService.getObject().updateResult(newResult);
    }
}
