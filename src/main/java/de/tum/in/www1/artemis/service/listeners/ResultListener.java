package de.tum.in.www1.artemis.service.listeners;

import javax.persistence.PostPersist;
import javax.persistence.PreRemove;
import javax.persistence.PreUpdate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.service.StudentScoreService;

@Component
public class ResultListener {

    private final Logger log = LoggerFactory.getLogger(ResultListener.class);

    private StudentScoreService studentScoreService;

    public ResultListener(@Lazy StudentScoreService studentScoreService) {
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
        studentScoreService.removeResult(deletedResult);
    }

    /**
     * After new result gets persisted, update all Scores with this result.
     *
     * @param result new or updated result result
     */
    @PreUpdate
    @PostPersist
    public void postPersist(Result result) {
        log.info("Result " + result + " was persisted");

        // Update StudentScore
        studentScoreService.updateResult(result);
    }
}
