package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.UserService;
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

    private static final long MAX_COMPLAINT_NUMBER_PER_STUDENT = 3;

    private ComplaintRepository complaintRepository;

    private ResultRepository resultRepository;

    private AuthorizationCheckService authCheckService;

    private ExerciseService exerciseService;

    private UserService userService;

    public ComplaintResource(ComplaintRepository complaintRepository, ResultRepository resultRepository,
                             AuthorizationCheckService authCheckService, ExerciseService exerciseService,
                             UserService userService) {
        this.complaintRepository = complaintRepository;
        this.resultRepository = resultRepository;
        this.authCheckService = authCheckService;
        this.exerciseService = exerciseService;
        this.userService = userService;
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

        // Do not trust user input
        Result originalResult = resultRepository.findById(complaint.getResult().getId())
            .orElseThrow(() -> new BadRequestAlertException("The result you are referring to does not exist", ENTITY_NAME, "resultnotfound"));
        User originalSubmissor = originalResult.getParticipation().getStudent();
        Long courseId = originalResult.getParticipation().getExercise().getCourse().getId();

        long numberOfUnacceptedComplaints = complaintRepository.countUnacceptedComplaintsByStudentIdAndCourseId(originalSubmissor.getId(), courseId);
        if (numberOfUnacceptedComplaints >= MAX_COMPLAINT_NUMBER_PER_STUDENT) {
            throw new BadRequestAlertException("You cannot have more than " + MAX_COMPLAINT_NUMBER_PER_STUDENT + " open or rejected complaints at the same time.", ENTITY_NAME, "toomanycomplaints");
        }

        if (!originalSubmissor.getLogin().equals(principal.getName())) {
            throw new BadRequestAlertException("You can create a complaint only about a result you submitted", ENTITY_NAME, "differentuser");
        }

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
     * @return the ResponseEntity with status 200 (OK) and either with the complaint as body
     *         or an empty body, if no complaint was found for the result
     */
    @GetMapping("/complaints/result/{resultId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Complaint> getComplaintByResultId(@PathVariable Long resultId) {
        log.debug("REST request to get Complaint associated to result : {}", resultId);
        Optional<Complaint> complaint = complaintRepository.findByResult_Id(resultId);
        if (complaint.isPresent()) {
            return ResponseEntity.ok(complaint.get());
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Get /complaints/not-accepted/:id get the number of complaints that a student is still allowed to submit in the
     * given course. It is determined by the max. complaint limit and the current number of open or rejected complaints
     * of the student in the course.
     *
     * @param courseId the id of the course for which we want to get the number of allowed complaints
     * @return the ResponseEntity with status 200 (OK) and the number of still allowed complaints
     */
    // TODO CZ: adjust url? adjust/remove endpoint?
    @GetMapping("/complaints/allowed/{courseId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Long> getNumberOfAllowedComplaintsInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get the number of unaccepted Complaints associated to the current user in course : {}", courseId);
        long unacceptedComplaints = complaintRepository.countUnacceptedComplaintsByStudentIdAndCourseId(userService.getUser().getId(), courseId);
        return ResponseEntity.ok(Math.max( MAX_COMPLAINT_NUMBER_PER_STUDENT - unacceptedComplaints, 0));
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

            if (!assessor.getLogin().equals(submissorName)) {
                // Remove data about the student
                complaint.getResult().getParticipation().setStudent(null);
                complaint.setStudent(null);

                responseComplaints.add(complaint);
            }
        });

        return ResponseEntity.ok(responseComplaints);
    }
}
