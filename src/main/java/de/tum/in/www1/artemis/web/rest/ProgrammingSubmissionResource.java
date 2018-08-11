package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.service.ProgrammingSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing ProgrammingSubmission.
 */
@RestController
@RequestMapping("/api")
public class ProgrammingSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingSubmissionResource.class);

    private static final String ENTITY_NAME= "programmingSubmission";

    private final ProgrammingSubmissionService programmingSubmissionService;

    public ProgrammingSubmissionResource(ProgrammingSubmissionService programmingSubmissionService) {
        this.programmingSubmissionService = programmingSubmissionService;
    }

    /**
     * POST  /programmingSubmissions/commit/:planKey : Notify the application about a new commit to the repository associated with the planKey
     * This API is invoked by the VCS Server at the push of a new commit
     *
     * @param planKey the plan key of the plan which is associated with the repository that is notifying about a new commit
     * @return the ResponseEntity with status 200 (OK), or with status 400 (Bad Request) if the latest commit was already notified about
     */
    @PostMapping(value = "/programmingSubmissions/commit/{planKey}")
    public ResponseEntity<?> notifyCommit(@PathVariable("planKey") String planKey) {
        log.debug("REST request to inform about new commit : {}", planKey);

        return ResponseEntity.status(HttpStatus.OK).build();
    }
}
