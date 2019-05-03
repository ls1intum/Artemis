package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ComplaintResponseRepository;
import de.tum.in.www1.artemis.web.rest.SubmissionResource;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * Service for managing complaints.
 */
@Service
public class ComplaintResponseService {

    private final Logger log = LoggerFactory.getLogger(SubmissionResource.class);

    private static final String ENTITY_NAME = "complaintResponse";

    private ComplaintRepository complaintRepository;

    private ComplaintResponseRepository complaintResponseRepository;

    private UserService userService;

    private AuthorizationCheckService authorizationCheckService;

    public ComplaintResponseService(ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository, UserService userService,
            AuthorizationCheckService authorizationCheckService) {
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.userService = userService;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Create a new complaint response and update the 'accepted' flag of the corresponding complaint.
     *
     * @param complaintResponse the complaint response to create
     * @return the saved complaint response
     */
    public ComplaintResponse createComplaintResponse(ComplaintResponse complaintResponse) {
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
        Optional<Complaint> originalComplaintOptional = complaintRepository.findByIdWithEagerAssessor(complaintId);
        if (!originalComplaintOptional.isPresent()) {
            throw new BadRequestAlertException("The complaint you are referring to does not exist", ENTITY_NAME, "noresult");
        }

        Complaint originalComplaint = originalComplaintOptional.get();

        if (complaintResponseRepository.findByComplaint_Id(originalComplaint.getId()).isPresent()) {
            throw new BadRequestAlertException("The complaint you are referring to does already have a response", ENTITY_NAME, "complaintresponseexists");
        }
        // Only tutors who are not part the original assessors can reply to a complaint
        if (!authorizationCheckService.isAtLeastTeachingAssistantForExercise(originalComplaint.getResult().getParticipation().getExercise())
                || originalComplaint.getResult().getAssessor().equals(reviewer)) {
            throw new AccessForbiddenException("Insufficient permission for creating a complaint response");
        }

        originalComplaint.setAccepted(complaintResponse.getComplaint().isAccepted());
        complaintRepository.save(originalComplaint);

        complaintResponse.setSubmittedTime(ZonedDateTime.now());
        complaintResponse.setReviewer(reviewer);
        return complaintResponseRepository.save(complaintResponse);
    }
}
