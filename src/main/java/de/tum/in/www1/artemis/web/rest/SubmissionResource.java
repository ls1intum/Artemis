package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing Submission.
 */
@RestController
@RequestMapping("/api")
public class SubmissionResource {

    private final Logger log = LoggerFactory.getLogger(SubmissionResource.class);

    private static final String ENTITY_NAME = "submission";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final SubmissionRepository submissionRepository;

    private final ResultService resultService;

    private final ParticipationService participationService;

    private final AuthorizationCheckService authCheckService;

    private final UserService userService;

    public SubmissionResource(SubmissionRepository submissionRepository, ResultService resultService, ParticipationService participationService,
            AuthorizationCheckService authCheckService, UserService userService) {
        this.submissionRepository = submissionRepository;
        this.resultService = resultService;
        this.participationService = participationService;
        this.authCheckService = authCheckService;
        this.userService = userService;
    }

    /**
     * POST /submissions : Create a new submission.
     *
     * @param submission the submission to create
     * @return the ResponseEntity with status 201 (Created) and with body the new submission, or with status 400 (Bad Request) if the submission has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/submissions")
    public ResponseEntity<Submission> createSubmission(@RequestBody Submission submission) throws URISyntaxException {
        log.debug("REST request to save Submission : {}", submission);
        if (submission.getId() != null) {
            throw new BadRequestAlertException("A new submission cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Submission result = submissionRepository.save(submission);
        return ResponseEntity.created(new URI("/api/submissions/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * PUT /submissions : Updates an existing submission.
     *
     * @param submission the submission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated submission, or with status 400 (Bad Request) if the submission is not valid, or with status 500
     *         (Internal Server Error) if the submission couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/submissions")
    public ResponseEntity<Submission> updateSubmission(@RequestBody Submission submission) throws URISyntaxException {
        log.debug("REST request to update Submission : {}", submission);
        if (submission.getId() == null) {
            return createSubmission(submission);
        }
        Submission result = submissionRepository.save(submission);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, submission.getId().toString())).body(result);
    }

    /**
     * GET /submissions : get all the submissions.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of submissions in body
     */
    @GetMapping("/submissions")
    public List<Submission> getAllSubmissions() {
        log.debug("REST request to get all Submissions");
        return submissionRepository.findAll();
    }

    /**
     * GET /submissions/:id : get the "id" submission.
     *
     * @param id the id of the submission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the submission, or with status 404 (Not Found)
     */
    @GetMapping("/submissions/{id}")
    public ResponseEntity<Submission> getSubmission(@PathVariable Long id) {
        log.debug("REST request to get Submission : {}", id);
        Optional<Submission> submission = submissionRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(submission);
    }

    /**
     * DELETE /submissions/:id : delete the "id" submission.
     *
     * @param id the id of the submission to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/submissions/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteSubmission(@PathVariable Long id) {
        log.debug("REST request to delete Submission : {}", id);

        Optional<Submission> submission = submissionRepository.findById(id);

        if (submission.isEmpty()) {
            log.error("Submission with id: " + id + " cannot be deleted");
            return ResponseEntity.notFound().build();
        }

        checkAccessPermissionAtInstructor(submission.get());

        Result result = submission.get().getResult();
        if (result != null) {
            resultService.deleteResultWithComplaint(result.getId());
        }
        submissionRepository.deleteById(id);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
    }

    private void checkAccessPermissionAtInstructor(Submission submission) {
        Course course = findCourseFromSubmission(submission);
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }
    }

    private Course findCourseFromSubmission(Submission submission) {
        StudentParticipation studentParticipation = (StudentParticipation) submission.getParticipation();
        if (studentParticipation.getExercise() != null && studentParticipation.getExercise().getCourse() != null) {
            return studentParticipation.getExercise().getCourse();
        }

        return participationService.findOneWithEagerCourse(submission.getParticipation().getId()).getExercise().getCourse();
    }
}
