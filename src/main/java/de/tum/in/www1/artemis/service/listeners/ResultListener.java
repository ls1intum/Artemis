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

@Configurable
public class ResultListener {

    private final Logger log = LoggerFactory.getLogger(ResultListener.class);

    private @Nullable ObjectFactory<StudentScoreService> studentScoreService;

    @Autowired
    public void setStudentScoreService(ObjectFactory<StudentScoreService> studentScoreService) {
        Assert.notNull(studentScoreService, "StudentScoreService must not be null!");
        this.studentScoreService = studentScoreService;
    }

    /**
     * Before result gets deleted, delete all Scores with this result.
     *
     * @param deletedResult deleted result
     */
    @PreRemove
    public void preRemove(Result deletedResult) {
        log.info("Result " + deletedResult + " was removed");

        // Remove StudentScore
        studentScoreService.getObject().removeResult(deletedResult);
    }

    /**
     * Before result gets updated, update all Scores.
     *
     * @param updatedResult updated result
     */
    @PreUpdate
    public void preUpdate(Result updatedResult) {
        log.info("Result " + updatedResult + " will be persisted");

        // Update StudentScore
        studentScoreService.getObject().updateResult(updatedResult);
    }

    /**
     * After new result gets persisted, update all Scores with this result.
     *
     * @param newResult new result
     */
    @PostPersist
    public void postPersist(Result newResult) {
        log.info("Result " + newResult + " was persisted");

        // Update StudentScore
        studentScoreService.getObject().updateResult(newResult);
    }
}
