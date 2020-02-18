package de.tum.in.www1.artemis.web.rest;

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
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

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
