package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ComplaintService;
import de.tum.in.www1.artemis.service.dto.ComplaintRequestDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing complaints.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ComplaintResource {

    private static final Logger log = LoggerFactory.getLogger(ComplaintResource.class);

    private static final String COMPLAINT_ENTITY_NAME = "complaint";

    private static final String MORE_FEEDBACK_ENTITY_NAME = "moreFeedback";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseRepository exerciseRepository;

    private final UserRepository userRepository;

    private final ResultRepository resultRepository;

    private final ComplaintService complaintService;

    private final ComplaintRepository complaintRepository;

    private final CourseRepository courseRepository;

    public ComplaintResource(AuthorizationCheckService authCheckService, ExerciseRepository exerciseRepository, UserRepository userRepository, ResultRepository resultRepository,
            ComplaintService complaintService, ComplaintRepository complaintRepository, CourseRepository courseRepository) {
        this.authCheckService = authCheckService;
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
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
    @PostMapping("complaints")
    @EnforceAtLeastStudent
    public ResponseEntity<Complaint> createComplaint(@RequestBody ComplaintRequestDTO complaint, Principal principal) throws URISyntaxException {
        log.debug("REST request to save Complaint: {}", complaint);

        if (complaintRepository.findByResultId(complaint.resultId()).isPresent()) {
            throw new BadRequestAlertException("A complaint for this result already exists", COMPLAINT_ENTITY_NAME, "complaintexists");
        }

        Result result = resultRepository.findByIdElseThrow(complaint.resultId());

        String entityName = complaint.complaintType() == ComplaintType.MORE_FEEDBACK ? MORE_FEEDBACK_ENTITY_NAME : COMPLAINT_ENTITY_NAME;
        Complaint savedComplaint;
        if (complaint.examId().isPresent()) {
            if (!result.getParticipation().getExercise().isExamExercise()) {
                throw new BadRequestAlertException("A complaint for an course exercise cannot be filed using this component", COMPLAINT_ENTITY_NAME,
                        "complaintAboutCourseExerciseWrongComponent");
            }
            authCheckService.isOwnerOfParticipationElseThrow((StudentParticipation) result.getParticipation());
        }
        else {
            if (result.getParticipation().getExercise().isExamExercise()) {
                throw new BadRequestAlertException("A complaint for an exam exercise cannot be filed using this component", COMPLAINT_ENTITY_NAME,
                        "complaintAboutExamExerciseWrongComponent");
            }
            // Assumes user with participation in an exam exercise can file a complaint for that participation.
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, result.getParticipation().getExercise(), null);
        }
        savedComplaint = complaintService.createComplaint(complaint, complaint.examId(), principal);

        // Remove assessor information from client request
        savedComplaint.getResult().filterSensitiveInformation();

        return ResponseEntity.created(new URI("/api/complaints/" + savedComplaint.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, entityName, savedComplaint.getId().toString())).body(savedComplaint);
    }

    /**
     * GET complaints: get a complaint associated with a result of the submission "id"
     *
     * @param submissionId the id of the submission for whose results we want to find a linked complaint
     * @return the ResponseEntity with status 200 (OK) and either with the complaint as body or an empty body, if no complaint was found for the result
     */

    @GetMapping(value = "complaints", params = { "submissionId" })
    @EnforceAtLeastStudent
    public ResponseEntity<Complaint> getComplaintBySubmissionId(@RequestParam Long submissionId) {
        log.debug("REST request to get latest Complaint associated with a result of submission : {}", submissionId);

        Optional<Complaint> optionalComplaint = complaintRepository.findByResultSubmissionId(submissionId);
        if (optionalComplaint.isEmpty()) {
            return ResponseEntity.ok().build();
        }
        Complaint complaint = optionalComplaint.get();
        User user = userRepository.getUserWithGroupsAndAuthorities();
        StudentParticipation participation = (StudentParticipation) complaint.getResult().getParticipation();
        Exercise exercise = participation.getExercise();
        boolean isOwner = authCheckService.isOwnerOfParticipation(participation, user);
        boolean isAtLeastTutor = authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user);
        if (!isOwner && !isAtLeastTutor) {
            throw new AccessForbiddenException();
        }
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise, user);
        boolean isTeamParticipation = participation.getParticipant() instanceof Team;
        boolean isTutorOfTeam = user.getLogin().equals(participation.getTeam().map(team -> team.getOwner() != null ? team.getOwner().getLogin() : null).orElse(null));

        if (!isAtLeastTutor) {
            complaint.getResult().filterSensitiveInformation();
            if (complaint.getComplaintResponse() != null) {
                complaint.getComplaintResponse().setReviewer(null);
            }
        }
        if (!isAtLeastInstructor && (!isTeamParticipation || !isTutorOfTeam)) {
            complaint.filterSensitiveInformation();
        }
        // hide participation + exercise + course which might include sensitive information
        complaint.getResult().setParticipation(null);
        return ResponseEntity.ok(complaint);
    }

    /**
     * GET complaints: get all the complaints associated to a test run exercise, but filter out the ones that are not about the tutor who is doing the request,
     * since this indicates test run
     * exercises
     *
     * @param exerciseId the id of the exercise we are interested in
     * @param principal  the user that wants to get complaints
     * @return the ResponseEntity with status 200 (OK) and a list of complaints or a list of more feedback requests. The list can be empty
     */
    @GetMapping(value = "complaints", params = { "exerciseId" })
    @EnforceAtLeastInstructor
    public ResponseEntity<List<Complaint>> getComplaintsForTestRunDashboard(Principal principal, @RequestParam Long exerciseId) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        List<Complaint> responseComplaints = complaintRepository.getAllComplaintsByExerciseIdAndComplaintType(exerciseId, ComplaintType.COMPLAINT);
        responseComplaints = buildComplaintsListForAssessor(responseComplaints, principal, true, true, true);
        return ResponseEntity.ok(responseComplaints);
    }

    /**
     * GET complaints: get all the complaints filtered by courseId, complaintType and optionally tutorId.
     *
     * @param tutorId               the id of the tutor by which we want to filter
     * @param courseId              the id of the course we are interested in
     * @param complaintType         the type of complaints we are interested in
     * @param allComplaintsForTutor flag if all complaints should be send for a tutor
     * @return the ResponseEntity with status 200 (OK) and a list of complaints. The list can be empty
     */
    @GetMapping(value = "complaints", params = { "courseId", "complaintType" })
    @EnforceAtLeastTutor
    public ResponseEntity<List<Complaint>> getComplaintsByCourseId(@RequestParam Long courseId, @RequestParam ComplaintType complaintType,
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
     * GET complaints: get all the complaints filtered by exerciseId, complaintType and optionally tutorId.
     *
     * @param tutorId       the id of the tutor by which we want to filter
     * @param exerciseId    the id of the exercise we are interested in
     * @param complaintType the type of complaints we are interested in
     * @return the ResponseEntity with status 200 (OK) and a list of complaints. The list can be empty
     */
    @GetMapping(value = "complaints", params = { "exerciseId", "complaintType" })
    @EnforceAtLeastTutor
    public ResponseEntity<List<Complaint>> getComplaintsByExerciseId(@RequestParam Long exerciseId, @RequestParam ComplaintType complaintType,
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
     * GET complaints: get all the complaints filtered by courseId, complaintType and optionally tutorId.
     *
     * @param examId   the id of the tutor by which we want to filter
     * @param courseId the id of the course we are interested in
     * @return the ResponseEntity with status 200 (OK) and a list of complaints. The list can be empty
     */
    @GetMapping(value = "complaints", params = { "courseId", "examId" })
    @EnforceAtLeastInstructor
    public ResponseEntity<List<Complaint>> getComplaintsByCourseIdAndExamId(@RequestParam Long courseId, @RequestParam Long examId) {
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
            Submission submissionWithOnlyId = switch (originalSubmission) {
                case TextSubmission ignored -> new TextSubmission();
                case ModelingSubmission ignored -> new ModelingSubmission();
                case FileUploadSubmission ignored -> new FileUploadSubmission();
                case ProgrammingSubmission ignored -> new ProgrammingSubmission();
                default -> null;
            };

            if (submissionWithOnlyId == null) {
                return;
            }
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
