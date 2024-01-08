package de.tum.in.www1.artemis.web.rest;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.in.www1.artemis.service.ComplaintResponseService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * REST controller for managing complaints.
 */
@RestController
@RequestMapping("/api")
public class ComplaintResponseResource {

    private final Logger log = LoggerFactory.getLogger(ComplaintResponseResource.class);

    public static final String ENTITY_NAME = "complaintResponse";

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseService complaintResponseService;

    private final UserRepository userRepository;

    public ComplaintResponseResource(ComplaintRepository complaintRepository, ComplaintResponseService complaintResponseService, UserRepository userRepository) {
        this.complaintRepository = complaintRepository;
        this.complaintResponseService = complaintResponseService;
        this.userRepository = userRepository;
    }

    /**
     * POST /complaint-responses/complaint/:complaintId/create-lock: locks the complaint by creating an empty complaint response
     *
     * @param complaintId - id of the complaint to lock
     * @return the ResponseEntity with status 201 (Created) and with body the empty complaint response
     */
    @PostMapping("/complaint-responses/complaint/{complaintId}/create-lock")
    @EnforceAtLeastTutor
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
    @EnforceAtLeastTutor
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
    @EnforceAtLeastTutor
    public ResponseEntity<Void> removeLockFromComplaint(@PathVariable long complaintId) {
        log.debug("REST request to remove the lock on the complaint with the id: {}", complaintId);
        Complaint complaint = getComplaintFromDatabaseAndCheckAccessRights(complaintId);
        complaintResponseService.removeComplaintResponseRepresentingLock(complaint);
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /complaint-responses/complaint/:complaintId/resolve: resolve a complaint by updating the complaint and the associated empty complaint response
     *
     * @param complaintId       - id of the complaint to resolve
     * @param complaintResponse the complaint response used for resolving the complaint
     * @return the ResponseEntity with status 200 (Ok) and with body the complaint response used for resolving the complaint
     */
    @PutMapping("/complaint-responses/complaint/{complaintId}/resolve")
    @EnforceAtLeastTutor
    public ResponseEntity<ComplaintResponse> resolveComplaint(@RequestBody ComplaintResponse complaintResponse, @PathVariable long complaintId) {
        log.debug("REST request to resolve the complaint with id: {}", complaintId);
        getComplaintFromDatabaseAndCheckAccessRights(complaintId);
        ComplaintResponse updatedComplaintResponse = complaintResponseService.resolveComplaint(complaintResponse);
        // always remove the student from the complaint as we don't need it in the corresponding client use case
        updatedComplaintResponse.getComplaint().filterSensitiveInformation();
        return ResponseEntity.ok().body(updatedComplaintResponse);
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
