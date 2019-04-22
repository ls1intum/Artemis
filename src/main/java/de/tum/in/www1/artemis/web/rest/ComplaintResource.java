package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.repository.ComplaintResponseRepository;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ExerciseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

/**
 * REST controller for managing complaints.
 */
@RestController
@RequestMapping("/api")
public class ComplaintResource {

    private final Logger log = LoggerFactory.getLogger(SubmissionResource.class);

    private static final String ENTITY_NAME = "complaint";

    private ComplaintRepository complaintRepository;

    private ComplaintResponseRepository complaintResponseRepository;

    private ResultRepository resultRepository;

    private UserRepository userRepository;

    private AuthorizationCheckService authCheckService;
    private ExerciseService exerciseService;

    public ComplaintResource(ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository, ResultRepository resultRepository, UserRepository userRepository, AuthorizationCheckService authCheckService, ExerciseService exerciseService) {
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.exerciseService = exerciseService;
    }

    /**
     * POST /complaint: create a new complaint
     *
     * @param complaint the complaint to create
     * @return the ResponseEntity with status 201 (Created) and with body the new complaints
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/complaints")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Complaint> createComplaint(@RequestBody Complaint complaint, Principal principal) throws URISyntaxException {
        log.debug("REST request to save Complaint: {}", complaint);
        if (complaint.getId() != null) {
            throw new BadRequestAlertException("A new complaint cannot already have an id", ENTITY_NAME, "idexists");
        }

        if (complaint.getResult() == null || complaint.getResult().getId() == null) {
            throw new BadRequestAlertException("A complaint can be only associated to a result", ENTITY_NAME, "noresultid");
        }

        Long resultId = complaint.getResult().getId();
        String submissorName = principal.getName();
        User originalSubmissor = userRepository.findUserByResultId(resultId);

        if (!originalSubmissor.getLogin().equals(submissorName)) {
            throw new BadRequestAlertException("You can create a complaint only about a result you submitted", ENTITY_NAME, "differentuser");
        }

        // Do not trust user input
        Optional<Result> originalResultOptional = resultRepository.findById(resultId);

        if (!originalResultOptional.isPresent()) {
            throw new BadRequestAlertException("The result you are referring to does not exist", ENTITY_NAME, "noresult");
        }

        Result originalResult = originalResultOptional.get();
        originalResult.setHasComplaint(true);

        complaint.setSubmittedTime(ZonedDateTime.now());
        complaint.setStudent(originalSubmissor);
        complaint.setResult(originalResult);
        complaint.setResultBeforeComplaint(originalResult.getResultString());

        resultRepository.save(originalResult);

        Complaint savedComplaint = complaintRepository.save(complaint);
        return ResponseEntity.created(new URI("/api/complaints/" + savedComplaint.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, savedComplaint.getId().toString())).body(savedComplaint);
    }

    /**
     * GET /complaints/:id : get the "id" complaint.
     *
     * @param id the id of the complaint to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the complaint, or with status 404 (Not Found)
     */
    @GetMapping("/complaints/{id}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Complaint> getComplaint(@PathVariable Long id) {
        log.debug("REST request to get Complaint : {}", id);
        Optional<Complaint> complaint = complaintRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(complaint);
    }

    /**
     * Get /complaints/result/:id get a complaint associated with the result "id"
     *
     * @param resultId the id of the result for which we want to find a linked complaint
     * @return the ResponseEntity with status 200 (OK) and with body the complaint, or with status 404 (Not Found)
     */
    @GetMapping("/complaints/result/{resultId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Complaint> getComplaintByResultId(@PathVariable Long resultId) {
        log.debug("REST request to get Complaint associated to result : {}", resultId);
        Optional<Complaint> complaint = complaintRepository.findByResult_Id(resultId);
        return ResponseUtil.wrapOrNotFound(complaint);
    }

    /**
     * Get /complaints/for-tutor-dashboard/:exerciseId
     * <p>
     * Get all the complaints associated to an exercise, but filter out the ones that are about the tutor who is doing the request, since tutors cannot act on their own complaint
     *
     * @param exerciseId the id of the exercise we are interested in
     * @return the ResponseEntity with status 200 (OK) and a list of complaints. The list can be empty
     */
    @GetMapping("/complaints/for-tutor-dashboard/{exerciseId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<Complaint>> getComplaintsForTutorDashboard(@PathVariable Long exerciseId, Principal principal) {
        List<Complaint> responseComplaints = new ArrayList<>();

        Exercise exercise = exerciseService.findOne(exerciseId);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }

        Optional<List<Complaint>> databaseComplaints = complaintRepository.findByResult_Participation_Exercise_IdWithEagerSubmissionAndEagerAssessor(exerciseId);

        if (!databaseComplaints.isPresent()) {
            return ResponseEntity.ok(responseComplaints);
        }

        databaseComplaints.get().forEach(complaint -> {
            String submissorName = principal.getName();
            User assessor = complaint.getResult().getAssessor();

            if (!assessor.getLogin().equals(submissorName) || userIsComplaintReviewer(submissorName, complaint.getId())) {
                // Remove data about the student
                complaint.getResult().getParticipation().setStudent(null);
                complaint.setStudent(null);

                responseComplaints.add(complaint);
            }
        });

        return ResponseEntity.ok(responseComplaints);
    }

    /**
     * Checks if there is a complaint response and if the given user is the reviewer of the corresponding complaint.
     * This is used for returning complaints for a user. We want to return any complaint that does not belong to
     * the user's own assessments OR that was reviewed by the user. The additional check for the reviewer is necessary
     * because the assessor of an assessment changes when a user reviews a complaint and overrides the assessment.
     * Therefore, the reviewed complaint would not be shown in the list of complaints for the reviewer anymore.
     *
     * @param username the name of the current user
     * @param complaintId the id of the complaint
     * @return true if the current user is the reviwer of the complaint, false otherwise
     */
    private boolean userIsComplaintReviewer(String username, Long complaintId) {
        Optional<ComplaintResponse> optionalComplaintResponse = complaintResponseRepository.findByComplaint_Id(complaintId);
        return optionalComplaintResponse.map(complaintResponse -> complaintResponse.getReviewer().getLogin().equals(username)).orElse(false);
    }
}
