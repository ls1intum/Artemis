package de.tum.in.www1.artemis.web.rest;

import java.security.Principal;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ComplaintResponseService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import io.github.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing complaints.
 */
@RestController
@RequestMapping("/api")
public class ComplaintResponseResource {

    private final Logger log = LoggerFactory.getLogger(SubmissionResource.class);

    public static final String ENTITY_NAME = "complaintResponse";

    private final ComplaintResponseRepository complaintResponseRepository;

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseService complaintResponseService;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    public ComplaintResponseResource(ComplaintResponseRepository complaintResponseRepository, ComplaintResponseService complaintResponseService,
            AuthorizationCheckService authorizationCheckService, UserRepository userRepository, ComplaintRepository complaintRepository) {
        this.complaintResponseRepository = complaintResponseRepository;
        this.complaintResponseService = complaintResponseService;
        this.complaintRepository = complaintRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
    }

    /**
     * POST /complaint-responses/complaint/:complaintId/create-lock: locks the complaint by creating an empty complaint response
     *
     * @param complaintId - id of the complaint to lock
     * @return the ResponseEntity with status 201 (Created) and with body the empty complaint response
     */
    @PostMapping("/complaint-responses/complaint/{complaintId}/create-lock")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ComplaintResponse> lockComplaint(@PathVariable long complaintId) {
        log.debug("REST request to create empty complaint response for complaint with id: {}", complaintId);
        Complaint complaint = getComplaintFromDatabaseAndCheckAccessRights(complaintId);
        ComplaintResponse savedComplaintResponse = complaintResponseService.createComplaintResponseRepresentingLock(complaint);
        // always remove the student from the complaint as we don't need it in the corresponding client use case
        savedComplaintResponse.getComplaint().filterSensitiveInformation();
        return new ResponseEntity<>(savedComplaintResponse, HttpStatus.CREATED);
    }

    /**
     * POST /complaint-responses/complaint/:complaintId/refresh-lock: locks the complaint again by replace the empty complaint response
     *
     * @param complaintId - id of the complaint to lock again
     * @return the ResponseEntity with status 201 (Created) and with body the empty complaint response
     */
    @PostMapping("/complaint-responses/complaint/{complaintId}/refresh-lock")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ComplaintResponse> refreshLockOnComplaint(@PathVariable long complaintId) {
        log.debug("REST request to refresh empty complaint response for complaint with id: {}", complaintId);
        Complaint complaint = getComplaintFromDatabaseAndCheckAccessRights(complaintId);
        ComplaintResponse savedComplaintResponse = complaintResponseService.refreshComplaintResponseRepresentingLock(complaint);
        // always remove the student from the complaint as we don't need it in the corresponding client use case
        savedComplaintResponse.getComplaint().filterSensitiveInformation();
        return new ResponseEntity<>(savedComplaintResponse, HttpStatus.CREATED);
    }

    /**
     * DELETE /complaint-responses/complaint/:complaintId/remove-lock: removes the lock on a complaint by removing the empty complaint response
     *
     * @param complaintId - id of the complaint to remove the lock for
     * @return the ResponseEntity with status 200 (Ok)
     */
    @DeleteMapping("/complaint-responses/complaint/{complaintId}/remove-lock")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> removeLockFromComplaint(@PathVariable long complaintId) {
        log.debug("REST request to remove the lock on the complaint with the id: {}", complaintId);
        Complaint complaint = getComplaintFromDatabaseAndCheckAccessRights(complaintId);
        complaintResponseService.removeComplaintResponseRepresentingLock(complaint);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /complaint-responses/complaint/:complaintId/resolve: resolve a complaint by updating the complaint and the associated empty complaint response
     *
     * @param complaintId - id of the complaint to resolve
     * @param complaintResponse the complaint response used for resolving the complaint
     * @return the ResponseEntity with status 200 (Ok) and with body the complaint response used for resolving the complaint
     */
    @PutMapping("/complaint-responses/complaint/{complaintId}/resolve")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ComplaintResponse> resolveComplaint(@RequestBody ComplaintResponse complaintResponse, @PathVariable long complaintId) {
        log.debug("REST request to resolve the complaint with id: {}", complaintId);
        getComplaintFromDatabaseAndCheckAccessRights(complaintId);
        ComplaintResponse updatedComplaintResponse = complaintResponseService.resolveComplaint(complaintResponse);
        // always remove the student from the complaint as we don't need it in the corresponding client use case
        updatedComplaintResponse.getComplaint().filterSensitiveInformation();
        return ResponseEntity.ok().body(updatedComplaintResponse);
    }

    /**
     * Get /complaint-responses/complaint/:id get a complaint response associated with the complaint "id"
     *
     * @param complaintId the id of the complaint for which we want to find a linked response
     * @param principal the user who called the method
     * @return the ResponseEntity with status 200 (OK) and with body the complaint response, or with status 404 (Not Found)
     */
    // TODO: change URL to /complaint-responses?complaintId={complaintId}
    @GetMapping("/complaint-responses/complaint/{complaintId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ComplaintResponse> getComplaintResponseByComplaintId(@PathVariable long complaintId, Principal principal) {
        log.debug("REST request to get ComplaintResponse associated to complaint : {}", complaintId);
        Optional<ComplaintResponse> complaintResponse = complaintResponseRepository.findByComplaint_Id(complaintId);
        return handleComplaintResponse(complaintId, principal, complaintResponse);
    }

    private ResponseEntity<ComplaintResponse> handleComplaintResponse(long complaintId, Principal principal, Optional<ComplaintResponse> optionalComplaintResponse) {
        if (optionalComplaintResponse.isEmpty()) {
            throw new EntityNotFoundException("ComplaintResponse with " + complaintId + " was not found!");
        }
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var complaintResponse = optionalComplaintResponse.get();
        // All tutors and higher can see this, and also the students who first open the complaint
        Participant originalAuthor = complaintResponse.getComplaint().getParticipant();
        StudentParticipation studentParticipation = (StudentParticipation) complaintResponse.getComplaint().getResult().getParticipation();
        Exercise exercise = studentParticipation.getExercise();
        var atLeastTA = authorizationCheckService.isAtLeastTeachingAssistantForExercise(exercise, user);
        if (!atLeastTA && !isOriginalAuthor(principal, originalAuthor)) {
            throw new AccessForbiddenException("Insufficient permission for this complaint response");
        }

        if (!authorizationCheckService.isAtLeastInstructorForExercise(exercise, user)) {
            complaintResponse.getComplaint().setParticipant(null);
        }

        if (!atLeastTA) {
            complaintResponse.setReviewer(null);
        }

        if (isOriginalAuthor(principal, originalAuthor)) {
            // hide complaint completely if the user is the student who created the complaint
            complaintResponse.setComplaint(null);
        }
        else {
            // hide unnecessary information
            complaintResponse.getComplaint().setResultBeforeComplaint(null);
            complaintResponse.getComplaint().getResult().setParticipation(null);
            complaintResponse.getComplaint().getResult().setSubmission(null);
        }
        return ResponseUtil.wrapOrNotFound(optionalComplaintResponse);
    }

    private boolean isOriginalAuthor(Principal principal, Participant originalAuthor) {
        if (originalAuthor instanceof User) {
            return Objects.equals(((User) originalAuthor).getLogin(), principal.getName());
        }
        else if (originalAuthor instanceof Team) {
            return ((Team) originalAuthor).hasStudentWithLogin(principal.getName());
        }
        else {
            throw new Error("Unknown Participant type");
        }
    }

    private Complaint getComplaintFromDatabaseAndCheckAccessRights(long complaintId) {
        Optional<Complaint> complaintFromDatabaseOptional = complaintRepository.findByIdWithEagerAssessor(complaintId);
        if (complaintFromDatabaseOptional.isEmpty()) {
            throw new IllegalArgumentException("Complaint was not found in database");
        }
        Complaint complaint = complaintFromDatabaseOptional.get();
        User user = this.userRepository.getUserWithGroupsAndAuthorities();
        if (!complaintResponseService.isUserAuthorizedToRespondToComplaint(complaint, user)) {
            throw new AccessForbiddenException("Insufficient permission for modifying the lock on the complaint");
        }
        return complaint;
    }
}
