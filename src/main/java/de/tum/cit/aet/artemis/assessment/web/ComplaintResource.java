package de.tum.cit.aet.artemis.assessment.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.ComplaintDTO;
import de.tum.cit.aet.artemis.assessment.dto.ComplaintRequestDTO;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.ComplaintService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * REST controller for managing complaints.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/assessment/")
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
    public ResponseEntity<ComplaintDTO> createComplaint(@RequestBody ComplaintRequestDTO complaint, Principal principal) throws URISyntaxException {
        log.debug("REST request to save Complaint: {}", complaint);

        if (complaintRepository.findByResultId(complaint.resultId()).isPresent()) {
            throw new BadRequestAlertException("A complaint for this result already exists", COMPLAINT_ENTITY_NAME, "complaintexists");
        }

        Result result = resultRepository.findByIdElseThrow(complaint.resultId());

        String entityName = complaint.complaintType() == ComplaintType.MORE_FEEDBACK ? MORE_FEEDBACK_ENTITY_NAME : COMPLAINT_ENTITY_NAME;
        Complaint savedComplaint;
        if (complaint.examId().isPresent()) {
            if (!result.getSubmission().getParticipation().getExercise().isExamExercise()) {
                throw new BadRequestAlertException("A complaint for an course exercise cannot be filed using this component", COMPLAINT_ENTITY_NAME,
                        "complaintAboutCourseExerciseWrongComponent");
            }
            authCheckService.isOwnerOfParticipationElseThrow((StudentParticipation) result.getSubmission().getParticipation());
        }
        else {
            if (result.getSubmission().getParticipation().getExercise().isExamExercise()) {
                throw new BadRequestAlertException("A complaint for an exam exercise cannot be filed using this component", COMPLAINT_ENTITY_NAME,
                        "complaintAboutExamExerciseWrongComponent");
            }
            // Assumes user with participation in an exam exercise can file a complaint for that participation.
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, result.getSubmission().getParticipation().getExercise(), null);
        }
        savedComplaint = complaintService.createComplaint(complaint, complaint.examId(), principal);

        ComplaintDTO complaintDTO = ComplaintDTO.of(savedComplaint);
        complaintDTO = complaintDTO.withSensitiveInformationFiltered();

        return ResponseEntity.created(new URI("/api/complaints/" + complaintDTO.id()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, entityName, String.valueOf(complaintDTO.id()))).body(complaintDTO);
    }

    /**
     * GET complaints: get a complaint associated with a result of the submission "id"
     *
     * @param submissionId the id of the submission for whose results we want to find a linked complaint
     * @return the ResponseEntity with status 200 (OK) and either with the complaint as body or an empty body, if no complaint was found for the result
     */

    @GetMapping(value = "complaints", params = { "submissionId" })
    @EnforceAtLeastStudent
    public ResponseEntity<ComplaintDTO> getComplaintBySubmissionId(@RequestParam Long submissionId) {
        log.debug("REST request to get latest Complaint associated with a result of submission : {}", submissionId);

        Optional<Complaint> optionalComplaint = complaintRepository.findByResultSubmissionId(submissionId);
        if (optionalComplaint.isEmpty()) {
            return ResponseEntity.ok().build();
        }
        Complaint complaint = optionalComplaint.get();
        User user = userRepository.getUserWithGroupsAndAuthorities();
        StudentParticipation participation = (StudentParticipation) complaint.getResult().getSubmission().getParticipation();
        Exercise exercise = participation.getExercise();
        boolean isOwner = authCheckService.isOwnerOfParticipation(participation, user);
        boolean isAtLeastTutor = authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user);
        if (!isOwner && !isAtLeastTutor) {
            throw new AccessForbiddenException();
        }
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise, user);
        boolean isTeamParticipation = participation.getParticipant() instanceof Team;
        boolean isTutorOfTeam = user.getLogin().equals(participation.getTeam().map(team -> team.getOwner() != null ? team.getOwner().getLogin() : null).orElse(null));

        ComplaintDTO complaintDTO = ComplaintDTO.of(complaint);

        if (!isAtLeastTutor) {
            complaintDTO = complaintDTO.withResultAndComplaintResponseSensitiveInformationFiltered();
        }
        if (!isAtLeastInstructor && (!isTeamParticipation || !isTutorOfTeam)) {
            complaintDTO = complaintDTO.withSensitiveInformationFiltered();
        }
        // hide participation + exercise + course which might include sensitive information
        complaintDTO = complaintDTO.withResultAndComplaintResponseSensitiveInformationFiltered();
        return ResponseEntity.ok(complaintDTO);
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
    public ResponseEntity<List<ComplaintDTO>> getComplaintsForTestRunDashboard(Principal principal, @RequestParam Long exerciseId) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        List<Complaint> responseComplaints = complaintRepository.getAllComplaintsByExerciseIdAndComplaintType(exerciseId, ComplaintType.COMPLAINT);
        List<ComplaintDTO> complaintDTOs = buildComplaintDTOsListForAssessor(responseComplaints, principal, true, true, true);
        return ResponseEntity.ok(complaintDTOs);
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
    public ResponseEntity<List<ComplaintDTO>> getComplaintsByCourseId(@RequestParam Long courseId, @RequestParam ComplaintType complaintType,
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

        List<Complaint> responseComplaints = getComplaintsByComplaintType(complaints, complaintType);

        // Convert to DTOs and apply filtering
        List<ComplaintDTO> complaintDTOs = responseComplaints.stream().map(ComplaintDTO::of).map(complaintDTO -> {
            if (!isAtLeastInstructor) {
                return complaintDTO.withSensitiveInformationFiltered();
            }
            return complaintDTO;
        }).toList();
        return ResponseEntity.ok(complaintDTOs);
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
    public ResponseEntity<List<ComplaintDTO>> getComplaintsByExerciseId(@RequestParam Long exerciseId, @RequestParam ComplaintType complaintType,
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

        List<Complaint> responseComplaints = getComplaintsByComplaintType(complaints, complaintType);

        // Convert to DTOs and apply filtering
        List<ComplaintDTO> complaintDTOs = responseComplaints.stream().map(ComplaintDTO::of).map(complaintDTO -> {
            if (!isAtLeastInstructor) {
                return complaintDTO.withSensitiveInformationFiltered();
            }
            return complaintDTO;
        }).toList();
        return ResponseEntity.ok(complaintDTOs);
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
    public ResponseEntity<List<ComplaintDTO>> getComplaintsByCourseIdAndExamId(@RequestParam Long courseId, @RequestParam Long examId) {
        // Filtering by courseId
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
        List<Complaint> complaints = complaintService.getAllComplaintsByExamId(examId);
        filterOutUselessDataFromComplaints(complaints, false);

        List<Complaint> responseComplaints = getComplaintsByComplaintType(complaints, ComplaintType.COMPLAINT);
        List<ComplaintDTO> complaintDTOs = responseComplaints.stream().map(ComplaintDTO::of).toList();
        return ResponseEntity.ok(complaintDTOs);
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

        if (complaint.getResult() != null && complaint.getResult().getSubmission().getParticipation() != null) {
            StudentParticipation studentParticipation = (StudentParticipation) complaint.getResult().getSubmission().getParticipation();
            studentParticipation.setParticipant(null);
        }
    }

    private void filterOutUselessDataFromComplaint(Complaint complaint) {
        if (complaint.getResult() == null) {
            return;
        }

        StudentParticipation originalParticipation = (StudentParticipation) complaint.getResult().getSubmission().getParticipation();
        if (originalParticipation != null && originalParticipation.getExercise() != null) {
            final var exerciseWithOnlyTitle = getExercise(originalParticipation);

            originalParticipation.setExercise(exerciseWithOnlyTitle);
            originalParticipation.setSubmissions(null);
        }

        Submission originalSubmission = complaint.getResult().getSubmission();
        if (originalSubmission != null) {
            Submission submissionWithOnlyId;
            switch (originalSubmission) {
                case TextSubmission ignored -> submissionWithOnlyId = new TextSubmission();
                case ModelingSubmission ignored -> submissionWithOnlyId = new ModelingSubmission();
                case FileUploadSubmission ignored -> submissionWithOnlyId = new FileUploadSubmission();
                case ProgrammingSubmission ignored -> submissionWithOnlyId = new ProgrammingSubmission();
                default -> {
                    return;
                }
            }
            submissionWithOnlyId.setId(originalSubmission.getId());
            submissionWithOnlyId.setParticipation(originalSubmission.getParticipation());
            complaint.getResult().setSubmission(submissionWithOnlyId);
        }
    }

    private static Exercise getExercise(StudentParticipation originalParticipation) {
        Exercise exerciseFromParticipation = originalParticipation.getExercise();
        Exercise exerciseWithOnlyTitle = switch (exerciseFromParticipation) {
            case TextExercise ignored -> new TextExercise();
            case ModelingExercise ignored -> new ModelingExercise();
            case FileUploadExercise ignored -> new FileUploadExercise();
            case ProgrammingExercise ignored -> new ProgrammingExercise();
            default -> exerciseFromParticipation;
        };

        exerciseWithOnlyTitle.setTitle(originalParticipation.getExercise().getTitle());
        exerciseWithOnlyTitle.setId(originalParticipation.getExercise().getId());
        return exerciseWithOnlyTitle;
    }

    private void filterOutUselessDataFromComplaints(List<Complaint> complaints, boolean filterOutStudentFromComplaints) {
        if (filterOutStudentFromComplaints) {
            complaints.forEach(this::filterOutStudentFromComplaint);
        }
        complaints.forEach(this::filterOutUselessDataFromComplaint);
    }

    private List<ComplaintDTO> buildComplaintDTOsListForAssessor(List<Complaint> complaints, Principal principal, boolean assessorSameAsCaller, boolean isTestRun,
            boolean isAtLeastInstructor) {
        List<ComplaintDTO> responseComplaintDTOs = new ArrayList<>();

        if (complaints.isEmpty()) {
            return responseComplaintDTOs;
        }

        complaints.forEach(complaint -> {
            String submissorName = principal.getName();
            User assessor = complaint.getResult().getAssessor();
            User student = complaint.getStudent();

            if (assessor != null && (assessor.getLogin().equals(submissorName) == assessorSameAsCaller || isAtLeastInstructor)
                    && (student != null && assessor.getLogin().equals(student.getLogin())) == isTestRun) {
                // Convert to DTO and apply filtering
                ComplaintDTO complaintDTO = ComplaintDTO.of(complaint);
                ComplaintDTO filteredDTO = complaintDTO.withSensitiveInformationFiltered();
                responseComplaintDTOs.add(filteredDTO);
            }
        });

        return responseComplaintDTOs;
    }
}
