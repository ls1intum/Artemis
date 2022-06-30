package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ComplaintService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing complaints.
 */
@RestController
@RequestMapping("/api")
public class ComplaintResource {

    private final Logger log = LoggerFactory.getLogger(SubmissionResource.class);

    private static final String COMPLAINT_ENTITY_NAME = "complaint";

    private static final String MORE_FEEDBACK_ENTITY_NAME = "moreFeedback";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseRepository exerciseRepository;

    private final UserRepository userRepository;

    private final TeamRepository teamRepository;

    private final ResultRepository resultRepository;

    private final ComplaintService complaintService;

    private final ComplaintRepository complaintRepository;

    private final CourseRepository courseRepository;

    public ComplaintResource(AuthorizationCheckService authCheckService, ExerciseRepository exerciseRepository, UserRepository userRepository, TeamRepository teamRepository,
            ResultRepository resultRepository, ComplaintService complaintService, ComplaintRepository complaintRepository, CourseRepository courseRepository) {
        this.authCheckService = authCheckService;
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.resultRepository = resultRepository;
        this.complaintService = complaintService;
        this.courseRepository = courseRepository;
        this.complaintRepository = complaintRepository;
    }

    /**
     * POST complaints: create a new complaint
     *
     * @param complaint the complaint to create
     * @param principal that wants to complain
     * @return the ResponseEntity with status 201 (Created) and with body the new complaints
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("complaints") // TODO: should be participations/{participationId}/results/{resultId}/complaints
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Complaint> createComplaint(@RequestBody Complaint complaint, Principal principal) throws URISyntaxException {
        log.debug("REST request to save Complaint: {}", complaint);
        if (complaint.getId() != null) {
            throw new BadRequestAlertException("A new complaint cannot already have an id", COMPLAINT_ENTITY_NAME, "idexists");
        }

        if (complaint.getResult() == null || complaint.getResult().getId() == null) {
            throw new BadRequestAlertException("A complaint can be only associated to a result", COMPLAINT_ENTITY_NAME, "noresultid");
        }

        if (complaintRepository.findByResultId(complaint.getResult().getId()).isPresent()) {
            throw new BadRequestAlertException("A complaint for this result already exists", COMPLAINT_ENTITY_NAME, "complaintexists");
        }

        Result result = resultRepository.findByIdElseThrow(complaint.getResult().getId());

        // For exam exercises, the POST complaints/exam/examId should be used
        if (result.getParticipation().getExercise().isExamExercise()) {
            throw new BadRequestAlertException("A complaint for an exam exercise cannot be filed using this component", COMPLAINT_ENTITY_NAME,
                    "complaintAboutExamExerciseWrongComponent");
        }

        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, result.getParticipation().getExercise(), null);

        // To build correct creation alert on the front-end we must check which type is the complaint to apply correct i18n key.
        String entityName = complaint.getComplaintType() == ComplaintType.MORE_FEEDBACK ? MORE_FEEDBACK_ENTITY_NAME : COMPLAINT_ENTITY_NAME;
        Complaint savedComplaint = complaintService.createComplaint(complaint, OptionalLong.empty(), principal);

        // Remove assessor information from client request
        savedComplaint.getResult().setAssessor(null);

        return ResponseEntity.created(new URI("/api/complaints/" + savedComplaint.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, entityName, savedComplaint.getId().toString())).body(savedComplaint);
    }

    /**
     * POST complaints/exam/examId: create a new complaint for an exam exercise
     *
     * @param complaint the complaint to create
     * @param principal that wants to complain
     * @param examId the examId of the exam which contains the exercise
     * @return the ResponseEntity with status 201 (Created) and with body the new complaints
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("complaints/exam/{examId}") // TODO: should be exams/{examId}/(participations/{participationId}/)complaints
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Complaint> createComplaintForExamExercise(@PathVariable Long examId, @RequestBody Complaint complaint, Principal principal) throws URISyntaxException {
        log.debug("REST request to save Complaint for exam exercise: {}", complaint);
        if (complaint.getId() != null) {
            throw new BadRequestAlertException("A new complaint cannot already have an id", COMPLAINT_ENTITY_NAME, "idexists");
        }

        if (complaint.getResult() == null || complaint.getResult().getId() == null) {
            throw new BadRequestAlertException("A complaint can be only associated to a result", COMPLAINT_ENTITY_NAME, "noresultid");
        }

        if (complaintRepository.findByResultId(complaint.getResult().getId()).isPresent()) {
            throw new BadRequestAlertException("A complaint for this result already exists", COMPLAINT_ENTITY_NAME, "complaintexists");
        }

        Result result = resultRepository.findByIdElseThrow(complaint.getResult().getId());

        // For non-exam exercises, the POST complaints should be used
        if (!result.getParticipation().getExercise().isExamExercise()) {
            throw new BadRequestAlertException("A complaint for an course exercise cannot be filed using this component", COMPLAINT_ENTITY_NAME,
                    "complaintAboutCourseExerciseWrongComponent");
        }

        authCheckService.isOwnerOfParticipationElseThrow((StudentParticipation) result.getParticipation());
        // To build correct creation alert on the front-end we must check which type is the complaint to apply correct i18n key.
        String entityName = complaint.getComplaintType() == ComplaintType.MORE_FEEDBACK ? MORE_FEEDBACK_ENTITY_NAME : COMPLAINT_ENTITY_NAME;
        Complaint savedComplaint = complaintService.createComplaint(complaint, OptionalLong.of(examId), principal);

        // Remove assessor information from client request
        savedComplaint.getResult().setAssessor(null);

        return ResponseEntity.created(new URI("/api/complaints/" + savedComplaint.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, entityName, savedComplaint.getId().toString())).body(savedComplaint);
    }

    /**
     * Get complaints/submissions/{submissionId} get a complaint associated with a result of the submission "id"
     *
     * @param submissionId the id of the submission for whose results we want to find a linked complaint
     * @return the ResponseEntity with status 200 (OK) and either with the complaint as body or an empty body, if no complaint was found for the result
     */
    @GetMapping("complaints/submissions/{submissionId}")
    @PreAuthorize("hasRole('USER')")
    // TODO: the URL should rather be "submissions/{submissionId}/complaints"
    public ResponseEntity<Complaint> getComplaintBySubmissionId(@PathVariable Long submissionId) {
        log.debug("REST request to get latest Complaint associated with a result of submission : {}", submissionId);

        Optional<Complaint> optionalComplaint = complaintRepository.findByResultSubmissionId(submissionId);
        if (optionalComplaint.isEmpty()) {
            return ResponseEntity.ok().build();
        }
        Complaint complaint = optionalComplaint.get();
        var user = userRepository.getUserWithGroupsAndAuthorities();
        StudentParticipation participation = (StudentParticipation) complaint.getResult().getParticipation();
        var exercise = participation.getExercise();
        var isOwner = authCheckService.isOwnerOfParticipation(participation, user);
        var isAtLeastTA = authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user);
        if (!isOwner && !isAtLeastTA) {
            throw new AccessForbiddenException();
        }
        var isAtLeastTutor = authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user);
        var isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise, user);
        var isTeamParticipation = participation.getParticipant() instanceof Team;
        var isTutorOfTeam = user.getLogin().equals(participation.getTeam().map(team -> team.getOwner().getLogin()).orElse(null));

        if (!isAtLeastTutor) {
            complaint.getResult().setAssessor(null);
        }
        if (!isAtLeastInstructor && (!isTeamParticipation || !isTutorOfTeam)) {
            complaint.filterSensitiveInformation();
        }
        // hide participation + exercise + course which might include sensitive information
        complaint.getResult().setParticipation(null);
        return ResponseEntity.ok(complaint);
    }

    /**
     * Get courses/{courseId}/allowed-complaints get the number of complaints that a student or team is still allowed to submit in the given course.
     * It is determined by the max. complaint limit and the current number of open or rejected complaints of the student or team in the course.
     * Students use their personal complaints for individual exercises and team complaints for team-based exercises, i.e. each student has
     * maxComplaints for personal complaints and additionally maxTeamComplaints for complaints by their team in the course.
     *
     * @param courseId the id of the course for which we want to get the number of allowed complaints
     * @param teamMode whether to return the number of allowed complaints per team (instead of per student)
     * @return the ResponseEntity with status 200 (OK) and the number of still allowed complaints
     */
    @GetMapping("courses/{courseId}/allowed-complaints")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Long> getNumberOfAllowedComplaintsInCourse(@PathVariable Long courseId, @RequestParam(defaultValue = "false") Boolean teamMode) {
        log.debug("REST request to get the number of unaccepted Complaints associated to the current user in course : {}", courseId);
        User user = userRepository.getUser();
        Participant participant = user;
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!course.getComplaintsEnabled()) {
            throw new BadRequestAlertException("Complaints are disabled for this course", COMPLAINT_ENTITY_NAME, "complaintsDisabled");
        }
        if (teamMode) {
            Optional<Team> team = teamRepository.findAllByCourseIdAndUserIdOrderByIdDesc(course.getId(), user.getId()).stream().findFirst();
            participant = team.orElseThrow(() -> new BadRequestAlertException("You do not belong to a team in this course.", COMPLAINT_ENTITY_NAME, "noAssignedTeamInCourse"));
        }
        long unacceptedComplaints = complaintService.countUnacceptedComplaintsByParticipantAndCourseId(participant, courseId);
        return ResponseEntity.ok(Math.max(complaintService.getMaxComplaintsPerParticipant(course, participant) - unacceptedComplaints, 0));
    }

    /**
     * Get exercises/{exerciseId}/complaints-for-test-run-dashboard
     * <p>
     * Get all the complaints associated to a test run exercise, but filter out the ones that are not about the tutor who is doing the request, since this indicates test run exercises
     *
     * @param exerciseId the id of the exercise we are interested in
     * @param principal that wants to get complaints
     * @return the ResponseEntity with status 200 (OK) and a list of complaints. The list can be empty
     */
    @GetMapping("exercises/{exerciseId}/complaints-for-test-run-dashboard")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<Complaint>> getComplaintsForTestRunDashboard(@PathVariable Long exerciseId, Principal principal) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        List<Complaint> responseComplaints = complaintRepository.getAllComplaintsByExerciseIdAndComplaintType(exerciseId, ComplaintType.COMPLAINT);
        responseComplaints = buildComplaintsListForAssessor(responseComplaints, principal, true, true, true);
        return ResponseEntity.ok(responseComplaints);
    }

    /**
     * Get exercises/:exerciseId/more-feedback-for-assessment-dashboard
     * <p>
     * Get all the more feedback requests associated to an exercise, that are about the tutor who is doing the request.
     * @param exerciseId the id of the exercise we are interested in
     * @param principal that wants to get more feedback requests
     * @return the ResponseEntity with status 200 (OK) and a list of more feedback requests. The list can be empty
     */
    @GetMapping("exercises/{exerciseId}/more-feedback-for-assessment-dashboard")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<Complaint>> getMoreFeedbackRequestsForAssessmentDashboard(@PathVariable Long exerciseId, Principal principal) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        List<Complaint> responseComplaints = complaintService.getMyMoreFeedbackRequests(exerciseId);
        responseComplaints = buildComplaintsListForAssessor(responseComplaints, principal, true, false, false);
        return ResponseEntity.ok(responseComplaints);
    }

    /**
     * Get complaints
     * <p>
     * Get all the complaints for tutor.
     * @param complaintType the type of complaints we are interested in
     * @return the ResponseEntity with status 200 (OK) and a list of complaints. The list can be empty
     */
    @GetMapping("complaints") // TODO: should be "courses/{courseId}/tutors/{tutorId}/complaints"
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<Complaint>> getComplaintsForTutor(@RequestParam ComplaintType complaintType) {
        // Only tutors can retrieve all their own complaints without filter by course or exerciseId. Instructors need
        // to filter by at least exerciseId or courseId, to be sure they are really instructors for that course /
        // exercise.
        // Of course tutors cannot ask for complaints about other tutors.
        User user = userRepository.getUser();
        List<Complaint> complaints = complaintService.getAllComplaintsByTutorId(user.getId());
        filterOutUselessDataFromComplaints(complaints, true);
        return ResponseEntity.ok(getComplaintsByComplaintType(complaints, complaintType));
    }

    /**
     * Get courses/:courseId/complaints/:complaintType
     * <p>
     * Get all the complaints filtered by courseId, complaintType and optionally tutorId.
     * @param tutorId the id of the tutor by which we want to filter
     * @param courseId the id of the course we are interested in
     * @param complaintType the type of complaints we are interested in
     * @param allComplaintsForTutor flag if all complaints should be send for a tutor
     * @return the ResponseEntity with status 200 (OK) and a list of complaints. The list can be empty
     */
    @GetMapping("courses/{courseId}/complaints")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<Complaint>> getComplaintsByCourseId(@PathVariable Long courseId, @RequestParam ComplaintType complaintType,
            @RequestParam(required = false) Long tutorId, @RequestParam(required = false) boolean allComplaintsForTutor) {
        // Filtering by courseId
        Course course = courseRepository.findByIdElseThrow(courseId);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorInCourse(course, user);

        if (!isAtLeastInstructor) {
            tutorId = user.getId();
        }
        List<Complaint> complaints;

        if (tutorId == null) {
            complaints = complaintService.getAllComplaintsByCourseId(courseId);
            filterOutUselessDataFromComplaints(complaints, !isAtLeastInstructor);
        }
        else if (allComplaintsForTutor) {
            complaints = complaintService.getAllComplaintsByCourseId(courseId);
            filterOutUselessDataFromComplaints(complaints, !isAtLeastInstructor);
            // For a tutor, all foreign reviewers are filtered out
            complaints.forEach(complaint -> complaint.filterForeignReviewer(user));
        }
        else {
            complaints = complaintService.getAllComplaintsByCourseIdAndTutorId(courseId, tutorId);
            filterOutUselessDataFromComplaints(complaints, !isAtLeastInstructor);
        }

        return ResponseEntity.ok(getComplaintsByComplaintType(complaints, complaintType));
    }

    /**
     * Get exercises/:exerciseId/complaints
     * <p>
     * Get all the complaints filtered by exerciseId, complaintType and optionally tutorId.
     * @param tutorId the id of the tutor by which we want to filter
     * @param exerciseId the id of the exercise we are interested in
     * @param complaintType the type of complaints we are interested in
     * @return the ResponseEntity with status 200 (OK) and a list of complaints. The list can be empty
     */
    @GetMapping("exercises/{exerciseId}/complaints")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<Complaint>> getComplaintsByExerciseId(@PathVariable Long exerciseId, @RequestParam ComplaintType complaintType,
            @RequestParam(required = false) Long tutorId) {
        // Filtering by exerciseId
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, user);
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise, user);

        // Only instructors can access all complaints about an exercise without filtering by tutorId
        if (!isAtLeastInstructor) {
            tutorId = userRepository.getUser().getId();
        }

        List<Complaint> complaints;

        if (tutorId == null) {
            complaints = complaintService.getAllComplaintsByExerciseId(exerciseId);
            filterOutUselessDataFromComplaints(complaints, !isAtLeastInstructor);
        }
        else {
            complaints = complaintService.getAllComplaintsByExerciseIdAndTutorId(exerciseId, tutorId);
            filterOutUselessDataFromComplaints(complaints, !isAtLeastInstructor);
        }

        return ResponseEntity.ok(getComplaintsByComplaintType(complaints, complaintType));
    }

    /**
     * Get courses/:courseId/exams/:examId/complaints
     * <p>
     * Get all the complaints filtered by courseId, complaintType and optionally tutorId.
     * @param examId the id of the tutor by which we want to filter
     * @param courseId the id of the course we are interested in
     * @return the ResponseEntity with status 200 (OK) and a list of complaints. The list can be empty
     */
    @GetMapping("courses/{courseId}/exams/{examId}/complaints")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<Complaint>> getComplaintsByCourseIdAndExamId(@PathVariable Long courseId, @PathVariable Long examId) {
        // Filtering by courseId
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
        List<Complaint> complaints = complaintService.getAllComplaintsByExamId(examId);
        filterOutUselessDataFromComplaints(complaints, false);

        return ResponseEntity.ok(getComplaintsByComplaintType(complaints, ComplaintType.COMPLAINT));
    }

    /**
     * Filter out all complaints that are not of a specified type.
     *
     * @param complaints    list of complaints
     * @param complaintType the type of complaints we want to get
     * @return an unmodifiable list of the complaints
     */
    private List<Complaint> getComplaintsByComplaintType(List<Complaint> complaints, ComplaintType complaintType) {
        return complaints.stream().filter(complaint -> complaint.getComplaintType() == complaintType).toList();
    }

    private void filterOutStudentFromComplaint(Complaint complaint) {
        complaint.setParticipant(null);

        if (complaint.getResult() != null && complaint.getResult().getParticipation() != null) {
            StudentParticipation studentParticipation = (StudentParticipation) complaint.getResult().getParticipation();
            studentParticipation.setParticipant(null);
        }
    }

    private void filterOutUselessDataFromComplaint(Complaint complaint) {
        if (complaint.getResult() == null) {
            return;
        }

        StudentParticipation originalParticipation = (StudentParticipation) complaint.getResult().getParticipation();
        if (originalParticipation != null && originalParticipation.getExercise() != null) {
            Exercise exerciseWithOnlyTitle = originalParticipation.getExercise();
            if (exerciseWithOnlyTitle instanceof TextExercise) {
                exerciseWithOnlyTitle = new TextExercise();
            }
            else if (exerciseWithOnlyTitle instanceof ModelingExercise) {
                exerciseWithOnlyTitle = new ModelingExercise();
            }
            else if (exerciseWithOnlyTitle instanceof FileUploadExercise) {
                exerciseWithOnlyTitle = new FileUploadExercise();
            }

            else if (exerciseWithOnlyTitle instanceof ProgrammingExercise) {
                exerciseWithOnlyTitle = new ProgrammingExercise();
            }
            exerciseWithOnlyTitle.setTitle(originalParticipation.getExercise().getTitle());
            exerciseWithOnlyTitle.setId(originalParticipation.getExercise().getId());

            originalParticipation.setExercise(exerciseWithOnlyTitle);
        }

        Submission originalSubmission = complaint.getResult().getSubmission();
        if (originalSubmission != null) {
            Submission submissionWithOnlyId;
            if (originalSubmission instanceof TextSubmission) {
                submissionWithOnlyId = new TextSubmission();
            }
            else if (originalSubmission instanceof ModelingSubmission) {
                submissionWithOnlyId = new ModelingSubmission();
            }
            else if (originalSubmission instanceof FileUploadSubmission) {
                submissionWithOnlyId = new FileUploadSubmission();
            }
            else if (originalSubmission instanceof ProgrammingSubmission) {
                submissionWithOnlyId = new ProgrammingSubmission();
            }
            else {
                return;
            }
            submissionWithOnlyId.setId(originalSubmission.getId());
            complaint.getResult().setSubmission(submissionWithOnlyId);
        }
    }

    private void filterOutUselessDataFromComplaints(List<Complaint> complaints, boolean filterOutStudentFromComplaints) {
        if (filterOutStudentFromComplaints) {
            complaints.forEach(this::filterOutStudentFromComplaint);
        }
        complaints.forEach(this::filterOutUselessDataFromComplaint);
    }

    private List<Complaint> buildComplaintsListForAssessor(List<Complaint> complaints, Principal principal, boolean assessorSameAsCaller, boolean isTestRun,
            boolean isAtLeastInstructor) {
        List<Complaint> responseComplaints = new ArrayList<>();

        if (complaints.isEmpty()) {
            return responseComplaints;
        }

        complaints.forEach(complaint -> {
            String submissorName = principal.getName();
            User assessor = complaint.getResult().getAssessor();
            User student = complaint.getStudent();

            if (assessor != null && (assessor.getLogin().equals(submissorName) == assessorSameAsCaller || isAtLeastInstructor)
                    && (student != null && assessor.getLogin().equals(student.getLogin())) == isTestRun) {
                // Remove data about the student
                StudentParticipation studentParticipation = (StudentParticipation) complaint.getResult().getParticipation();
                studentParticipation.setParticipant(null);
                studentParticipation.setExercise(null);
                complaint.setParticipant(null);

                responseComplaints.add(complaint);
            }
        });

        return responseComplaints;
    }
}
