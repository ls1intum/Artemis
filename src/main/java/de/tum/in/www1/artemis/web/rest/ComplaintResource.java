package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.MAX_COMPLAINT_NUMBER_PER_STUDENT;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing complaints.
 */
@RestController
@RequestMapping("/api")
public class ComplaintResource {

    private final Logger log = LoggerFactory.getLogger(SubmissionResource.class);

    private static final String ENTITY_NAME = "complaint";

    private AuthorizationCheckService authCheckService;

    private ExerciseService exerciseService;

    private UserService userService;

    private ComplaintService complaintService;

    private CourseService courseService;

    public ComplaintResource(AuthorizationCheckService authCheckService, ExerciseService exerciseService, UserService userService, ComplaintService complaintService,
            CourseService courseService) {
        this.authCheckService = authCheckService;
        this.exerciseService = exerciseService;
        this.userService = userService;
        this.complaintService = complaintService;
        this.courseService = courseService;
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

        if (complaintService.getById(complaint.getResult().getId()).isPresent()) {
            throw new BadRequestAlertException("A complaint for this result already exists", ENTITY_NAME, "complaintexists");
        }

        Complaint savedComplaint = complaintService.createComplaint(complaint, principal);
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
        return ResponseUtil.wrapOrNotFound(complaintService.getById(id));
    }

    /**
     * Get /complaints/result/:id get a complaint associated with the result "id"
     *
     * @param resultId the id of the result for which we want to find a linked complaint
     * @return the ResponseEntity with status 200 (OK) and either with the complaint as body or an empty body, if no complaint was found for the result
     */
    @GetMapping("/complaints/result/{resultId}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Complaint> getComplaintByResultId(@PathVariable Long resultId) {
        log.debug("REST request to get Complaint associated to result : {}", resultId);
        Optional<Complaint> complaint = complaintService.getByResultId(resultId);
        return complaint.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.ok().build());
    }

    /**
     * Get /:courseId/allowed-complaints get the number of complaints that a student is still allowed to submit in the given course. It is determined by the max. complaint limit
     * and the current number of open or rejected complaints of the student in the course.
     *
     * @param courseId the id of the course for which we want to get the number of allowed complaints
     * @return the ResponseEntity with status 200 (OK) and the number of still allowed complaints
     */
    @GetMapping("/{courseId}/allowed-complaints")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Long> getNumberOfAllowedComplaintsInCourse(@PathVariable Long courseId) {
        log.debug("REST request to get the number of unaccepted Complaints associated to the current user in course : {}", courseId);
        long unacceptedComplaints = complaintService.countUnacceptedComplaintsByStudentIdAndCourseId(userService.getUser().getId(), courseId);
        return ResponseEntity.ok(Math.max(MAX_COMPLAINT_NUMBER_PER_STUDENT - unacceptedComplaints, 0));
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
        Exercise exercise = exerciseService.findOne(exerciseId);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }

        List<Complaint> responseComplaints = complaintService.getAllComplaintsByExerciseIdButMine(exerciseId, principal);
        return ResponseEntity.ok(responseComplaints);
    }

    @GetMapping("/complaints")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<Complaint>> getComplaintsFilteredBy(@RequestParam(required = false) Long tutorId, @RequestParam(required = false) Long exerciseId,
            @RequestParam(required = false) Long courseId) {
        if (tutorId == null && exerciseId == null && courseId == null) {
            throw new BadRequestAlertException("You need to specify at least one between tutorId, exerciseId, and courseId", ENTITY_NAME, "specifyFilter");
        }

        // Only tutors can retrieve all their own complaints without filter by course or exerciseId. Instructors need
        // to filter by at least exerciseId or courseId, to be sure they are really instructors for that course /
        // exercise.
        // Of course tutors cannot ask for complaints about other tutors.
        // So, if the courseId is null, and the exerciseId is null, we just use the userId of the caller
        if (exerciseId == null && courseId == null) {
            User callerUser = userService.getUser();
            List<Complaint> complaints = complaintService.getAllComplaintsByTutorId(callerUser.getId());

            return ResponseEntity.ok(complaints);
        }

        if (courseId != null) {
            // Filtering by courseId
            Course course = courseService.findOne(courseId);

            if (course == null) {
                throw new BadRequestAlertException("The requested course does not exist", ENTITY_NAME, "wrongCourseId");
            }

            boolean isAtLeastTutor = authCheckService.isAtLeastTeachingAssistantInCourse(course, null);
            boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForCourse(course, null);

            if (!isAtLeastTutor) {
                throw new AccessForbiddenException("Insufficient permission for these complaints");
            }

            if (!isAtLeastInstructor) {
                tutorId = userService.getUser().getId();
            }

            List<Complaint> complaints;

            if (tutorId == null) {
                complaints = complaintService.getAllComplaintsByCourseId(courseId, isAtLeastInstructor);
            }
            else {
                complaints = complaintService.getAllComplaintsByCourseIdAndTutorId(courseId, tutorId, isAtLeastInstructor);
            }

            return ResponseEntity.ok(complaints);
        }

        // Filtering by exerciseId
        Exercise exercise = exerciseService.findOne(exerciseId);

        if (exercise == null) {
            throw new BadRequestAlertException("The requested exercise does not exist", ENTITY_NAME, "wrongExerciseId");
        }

        boolean isAtLeastTutor = authCheckService.isAtLeastTeachingAssistantForExercise(exercise);
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise);

        if (!isAtLeastTutor) {
            throw new AccessForbiddenException("Insufficient permission for these complaints");
        }

        // Only instructors can access all complaints about a exercise without filtering by tutorId
        if (!isAtLeastInstructor) {
            tutorId = userService.getUser().getId();
        }

        List<Complaint> complaints;

        if (tutorId == null) {
            complaints = complaintService.getAllComplaintsByExerciseId(exerciseId, isAtLeastInstructor);
        }
        else {
            complaints = complaintService.getAllComplaintsByExerciseIdAndTutorId(exerciseId, tutorId, isAtLeastInstructor);
        }

        return ResponseEntity.ok(complaints);
    }
}
