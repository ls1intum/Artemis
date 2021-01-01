package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ComplaintResponseRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ComplaintResponseService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing complaints.
 */
@RestController
@RequestMapping("/api")
public class ComplaintResponseResource {

    private final Logger log = LoggerFactory.getLogger(SubmissionResource.class);

    private static final String ENTITY_NAME = "complaintResponse";

    private static final String MORE_FEEDBACK_RESPONSE_ENITY_NAME = "moreFeedbackResponse";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final ComplaintResponseService complaintResponseService;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserService userService;

    public ComplaintResponseResource(ComplaintResponseRepository complaintResponseRepository, ComplaintResponseService complaintResponseService,
            AuthorizationCheckService authorizationCheckService, UserService userService) {
        this.complaintResponseRepository = complaintResponseRepository;
        this.complaintResponseService = complaintResponseService;
        this.authorizationCheckService = authorizationCheckService;
        this.userService = userService;
    }

    /**
     * POST /complaint-responses: create a new complaint response
     *
     * @param complaintResponse the complaint response to create
     * @return the ResponseEntity with status 201 (Created) and with body the new complaint response
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/complaint-responses")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ComplaintResponse> createComplaintResponse(@RequestBody ComplaintResponse complaintResponse) throws URISyntaxException {
        log.debug("REST request to save ComplaintResponse: {}", complaintResponse);
        ComplaintResponse savedComplaintResponse = complaintResponseService.createComplaintResponse(complaintResponse);

        // To build correct creation alert on the client we must check which type is the complaint to apply correct i18n key.
        String entityName = complaintResponse.getComplaint().getComplaintType() == ComplaintType.MORE_FEEDBACK ? MORE_FEEDBACK_RESPONSE_ENITY_NAME : ENTITY_NAME;

        // always remove the student from the complaint as we don't need it in the corresponding client use case
        complaintResponse.getComplaint().filterSensitiveInformation();

        return ResponseEntity.created(new URI("/api/complaint-responses/" + savedComplaintResponse.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, entityName, savedComplaintResponse.getId().toString())).body(savedComplaintResponse);
    }

    /**
     * PUT /complaint-response: update an existing complaint response
     *
     * @param complaintResponse the complaint response to update
     * @return the ResponseEntity with status 200 (OK) and with the body of the updated complaint response
     */
    @PutMapping("/complaint-responses")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ComplaintResponse> updateComplaintResponse(@RequestBody ComplaintResponse complaintResponse) {
        log.debug("REST request to update ComplaintResponse: {}", complaintResponse);
        ComplaintResponse updatedComplaintResponse = complaintResponseService.updateComplaintResponse(complaintResponse);

        // To build correct creation alert on the client we must check which type is the complaint to apply correct i18n key.
        String entityName = complaintResponse.getComplaint().getComplaintType() == ComplaintType.MORE_FEEDBACK ? MORE_FEEDBACK_RESPONSE_ENITY_NAME : ENTITY_NAME;

        // always remove the student from the complaint as we don't need it in the corresponding client use case
        complaintResponse.getComplaint().filterSensitiveInformation();

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, entityName, updatedComplaintResponse.getId().toString()))
                .body(updatedComplaintResponse);
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
        var user = userService.getUserWithGroupsAndAuthorities();
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
}
