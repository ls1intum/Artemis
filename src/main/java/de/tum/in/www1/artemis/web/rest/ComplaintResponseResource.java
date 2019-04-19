package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ComplaintResponseRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
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

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private ComplaintRepository complaintRepository;

    private ComplaintResponseRepository complaintResponseRepository;

    private UserService userService;

    private AuthorizationCheckService authorizationCheckService;

    public ComplaintResponseResource(ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository, UserService userService,
            AuthorizationCheckService authorizationCheckService) {
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.userService = userService;
        this.authorizationCheckService = authorizationCheckService;
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
        if (complaintResponse.getId() != null) {
            throw new BadRequestAlertException("A new complaint response cannot already have an id", ENTITY_NAME, "idexists");
        }

        if (complaintResponse.getComplaint() == null || complaintResponse.getComplaint().getId() == null) {
            throw new BadRequestAlertException("A complaint response can be only associated to a complaint", ENTITY_NAME, "noresultid");
        }

        Long complaintId = complaintResponse.getComplaint().getId();
        User reviewer = this.userService.getUser();

        // Do not trust user input
        Optional<Complaint> originalComplaintOptional = complaintRepository.findById(complaintId);

        if (!originalComplaintOptional.isPresent()) {
            throw new BadRequestAlertException("The complaint you are referring to does not exist", ENTITY_NAME, "noresult");
        }

        Complaint originalComplaint = originalComplaintOptional.get();

        // Only tutors who are not part the original assessors can reply to a complaint
        if (!authorizationCheckService.isAtLeastTeachingAssistantForExercise(originalComplaint.getResult().getParticipation().getExercise())
                || originalComplaint.getResult().getAssessor().equals(reviewer)) {
            throw new AccessForbiddenException("Insufficient permission for creating a complaint response");
        }

        originalComplaint.setAccepted(true);

        complaintResponse.setSubmittedTime(ZonedDateTime.now());
        complaintResponse.setReviewer(reviewer);

        complaintRepository.save(originalComplaint);

        ComplaintResponse savedComplaint = complaintResponseRepository.save(complaintResponse);
        return ResponseEntity.created(new URI("/api/complaint-responses/" + savedComplaint.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, savedComplaint.getId().toString())).body(savedComplaint);
    }

    /**
     * GET /complaint-responses/:id : get the "id" complaint response.
     *
     * @param id the id of the complaint response to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the complaint response, or with status 404 (Not Found)
     */
    @GetMapping("/complaint-responses/{id}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ComplaintResponse> getComplaintResponse(@PathVariable Long id, Principal principal) {
        log.debug("REST request to get ComplaintResponse : {}", id);
        Optional<ComplaintResponse> complaintResponse = complaintResponseRepository.findById(id);

        if (!complaintResponse.isPresent()) {
            throw new EntityNotFoundException("ComplaintResponse with " + id + " was not found!");
        }

        // All tutors and higher can see this, and also the students who first open the complaint
        canUserReadComplaintResponse(complaintResponse.get(), principal.getName());

        return ResponseUtil.wrapOrNotFound(complaintResponse);
    }

    /**
     * Get /complaint-responses/complaint/:id get a complaint response associated with the complaint "id"
     *
     * @param complaintId the id of the complaint for which we want to find a linked response
     * @return the ResponseEntity with status 200 (OK) and with body the complaint response, or with status 404 (Not Found)
     */
    @GetMapping("/complaint-responses/complaint/{complaintId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ComplaintResponse> getComplaintResponseByComplaintId(@PathVariable Long complaintId, Principal principal) {
        log.debug("REST request to get ComplaintResponse associated to complaint : {}", complaintId);
        Optional<ComplaintResponse> complaintResponse = complaintResponseRepository.findByComplaint_Id(complaintId);

        if (!complaintResponse.isPresent()) {
            throw new EntityNotFoundException("ComplaintResponse with " + complaintId + " was not found!");
        }

        canUserReadComplaintResponse(complaintResponse.get(), principal.getName());

        return ResponseUtil.wrapOrNotFound(complaintResponse);
    }

    private void canUserReadComplaintResponse(ComplaintResponse complaintResponse, String username) {
        // All tutors and higher can see this, and also the students who first open the complaint
        User originalAuthor = complaintResponse.getComplaint().getStudent();
        Exercise exercise = complaintResponse.getComplaint().getResult().getParticipation().getExercise();
        if (!authorizationCheckService.isAtLeastTeachingAssistantForExercise(exercise) && !originalAuthor.getLogin().equals(username)) {
            throw new AccessForbiddenException("Insufficient permission for this complaint response");
        }
    }
}
