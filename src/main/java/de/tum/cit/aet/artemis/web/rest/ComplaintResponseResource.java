package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.service.ComplaintResponseService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.service.dto.ComplaintAction;
import de.tum.cit.aet.artemis.service.dto.ComplaintResponseUpdateDTO;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;

/**
 * REST controller for managing complaints.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ComplaintResponseResource {

    private static final Logger log = LoggerFactory.getLogger(ComplaintResponseResource.class);

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
     * POST /complaints/{complaintId}/response: locks the complaint by creating an empty complaint response
     *
     * @param complaintId - id of the complaint to lock
     * @return the ResponseEntity with status 201 (Created) and with body the empty complaint response
     */
    @PostMapping("complaints/{complaintId}/response")
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
     * DELETE /complaints/{complaintId}/response: removes the lock on a complaint by removing the empty complaint response
     *
     * @param complaintId - id of the complaint to remove the lock for
     * @return the ResponseEntity with status 200 (Ok)
     */
    @DeleteMapping("complaints/{complaintId}/response")
    @EnforceAtLeastTutor
    public ResponseEntity<Void> removeLockFromComplaint(@PathVariable long complaintId) {
        log.debug("REST request to remove the lock on the complaint with the id: {}", complaintId);
        Complaint complaint = getComplaintFromDatabaseAndCheckAccessRights(complaintId);
        complaintResponseService.removeComplaintResponseRepresentingLock(complaint);
        return ResponseEntity.ok().build();
    }

    /**
     * PATCH /complaints/{complaintId}/response: resolve a complaint by updating the complaint and the associated empty complaint response
     *
     * @param complaintId             - id of the complaint to resolve
     * @param complaintResponseUpdate the complaint response used for resolving the complaint
     * @return if action is REFRESH_LOCK: status 201 (Created) and with body the empty complaint response
     *         if action is RESOLVE_COMPLAINT: the ResponseEntity with status 200 (Ok) and with body the complaint response used for resolving the complaint
     */
    @PatchMapping("complaints/{complaintId}/response")
    @EnforceAtLeastTutor
    public ResponseEntity<ComplaintResponse> refreshLockOrResolveComplaint(@RequestBody ComplaintResponseUpdateDTO complaintResponseUpdate, @PathVariable long complaintId) {
        if (complaintResponseUpdate.action() == null) {
            throw new BadRequestAlertException("A complaint response action can not be null.", ENTITY_NAME, "complaintResponseUpdateActionIsNull");
        }

        ComplaintAction action = complaintResponseUpdate.action();

        return switch (action) {
            case REFRESH_LOCK -> refreshLockOnComplaint(complaintId);
            case RESOLVE_COMPLAINT -> resolveComplaint(complaintResponseUpdate, complaintId);
        };
    }

    /**
     * Refreshes the complaint response for a specified complaint ID.
     *
     * This method retrieves the complaint from the database based on the provided ID, checks access rights, and then refreshes the complaint response representing
     * the updated complaint. It removes sensitive information from the refreshed complaint response before returning it as a ResponseEntity with HTTP status OK.
     *
     * @param complaintId The ID of the complaint to refresh.
     * @return A ResponseEntity containing the refreshed ComplaintResponse representing the updated complaint with HTTP status OK.
     */
    private ResponseEntity<ComplaintResponse> refreshLockOnComplaint(long complaintId) {
        log.debug("REST request to refresh empty complaint response for complaint with id: {}", complaintId);
        Complaint complaint = getComplaintFromDatabaseAndCheckAccessRights(complaintId);
        ComplaintResponse savedComplaintResponse = complaintResponseService.refreshComplaintResponseRepresentingLock(complaint);
        removeSensitiveInformation(savedComplaintResponse);
        return new ResponseEntity<>(savedComplaintResponse, HttpStatus.OK);
    }

    /**
     * Resolves the complaint response for a specified complaint ID with the provided update DTO.
     *
     * This method retrieves the complaint from the database based on the provided ID, checks access rights, and then resolves the complaint response using the provided
     * update DTO. It removes sensitive information from the resolved complaint response before returning it as a ResponseEntity with HTTP status OK.
     *
     * @param complaintResponseUpdate The DTO containing the update information for the complaint response.
     * @param complaintId             The ID of the complaint to resolve.
     * @return A ResponseEntity containing the updated ComplaintResponse representing the resolved complaint with HTTP status OK.
     */
    private ResponseEntity<ComplaintResponse> resolveComplaint(ComplaintResponseUpdateDTO complaintResponseUpdate, long complaintId) {
        log.debug("REST request to resolve the complaint with id: {}", complaintId);
        Complaint complaint = getComplaintFromDatabaseAndCheckAccessRights(complaintId);
        ComplaintResponse updatedComplaintResponse = complaintResponseService.resolveComplaint(complaintResponseUpdate, complaint.getComplaintResponse().getId());
        removeSensitiveInformation(updatedComplaintResponse);
        return ResponseEntity.ok().body(updatedComplaintResponse);
    }

    /**
     * Removes sensitive information from the provided ComplaintResponse.
     *
     * This method removes sensitive information, such as student details, from the provided ComplaintResponse. This information is considered
     * unnecessary for the corresponding client use case.
     *
     * @param complaintResponse The ComplaintResponse from which sensitive information needs to be removed.
     */
    private void removeSensitiveInformation(ComplaintResponse complaintResponse) {
        // Always remove the student from the complaint as we don't need it in the corresponding client use case
        complaintResponse.getComplaint().filterSensitiveInformation();
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
