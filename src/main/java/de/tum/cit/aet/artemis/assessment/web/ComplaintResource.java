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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.ComplaintDTO;
import de.tum.cit.aet.artemis.assessment.dto.ComplaintRequestDTO;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.ComplaintService;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exam.api.ExamAccessApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;

/**
 * REST controller for managing complaints.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.COMPLAINTS)
@RestController
@RequestMapping("api/assessment/")
public class ComplaintResource {

    private static final Logger log = LoggerFactory.getLogger(ComplaintResource.class);

    private static final String COMPLAINT_ENTITY_NAME = "complaint";

    private static final String MORE_FEEDBACK_ENTITY_NAME = "moreFeedback";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final AuthorizationCheckService authCheckService;

    private final SubmissionRepository submissionRepository;

    private final UserRepository userRepository;

    private final ResultRepository resultRepository;

    private final ComplaintService complaintService;

    private final ComplaintRepository complaintRepository;

    private final Optional<ExamAccessApi> examAccessApi;

    public ComplaintResource(AuthorizationCheckService authCheckService, SubmissionRepository submissionRepository, UserRepository userRepository,
            ResultRepository resultRepository, ComplaintService complaintService, ComplaintRepository complaintRepository, Optional<ExamAccessApi> examAccessApi) {
        this.authCheckService = authCheckService;
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.resultRepository = resultRepository;
        this.complaintService = complaintService;
        this.complaintRepository = complaintRepository;
        this.examAccessApi = examAccessApi;
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
        savedComplaint = complaintService.createComplaint(complaint, principal);

        // Remove assessor information from the client request
        savedComplaint.getResult().filterSensitiveInformation();

        return ResponseEntity.created(new URI("/api/complaints/" + savedComplaint.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, entityName, savedComplaint.getId().toString())).body(ComplaintDTO.of(savedComplaint));
    }

    /**
     * GET complaints: get a complaint associated with a result of the submission "id"
     *
     * @param submissionId the id of the submission for whose results we want to find a linked complaint
     * @return the ResponseEntity with status 200 (OK) and either with the complaint as a body or an empty body, if no complaint was found for the result
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
        StudentParticipation participation = (StudentParticipation) complaint.getResult().getSubmission().getParticipation();
        if (!authCheckService.isOwnerOfParticipation(participation, userRepository.getUserWithAuthorities())) {
            throw new AccessForbiddenException();
        }
        complaint.getResult().filterSensitiveInformation();
        if (complaint.getComplaintResponse() != null) {
            complaint.getComplaintResponse().setReviewer(null);
        }
        complaint.filterSensitiveInformation();
        return complaintDetailResponse(complaint);
    }

    /**
     * Get a complaint for assessment using the submission's exercise to authorize the caller, including when no complaint exists.
     *
     * @param submissionId the submission whose complaint is requested
     * @return the assessment complaint or an empty response
     */
    @GetMapping(value = "complaints/for-assessment", params = { "submissionId" })
    @EnforceAtLeastStudent
    @ManualConfig
    public ResponseEntity<ComplaintDTO> getComplaintBySubmissionIdForAssessment(@RequestParam Long submissionId) {
        Optional<Complaint> optionalComplaint = complaintRepository.findByResultSubmissionId(submissionId);
        var submission = optionalComplaint.map(complaint -> complaint.getResult().getSubmission()).orElseGet(() -> submissionRepository.findByIdElseThrow(submissionId));
        Participation participation = submission.getParticipation();
        if (participation == null || participation.getExercise() == null || participation.getExercise().getCourseViaExerciseGroupOrCourseMember() == null) {
            throw new EntityNotFoundException("Course", "of submission " + submissionId);
        }
        Exercise exercise = participation.getExercise();
        User user = userRepository.getUserWithAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, user);
        if (optionalComplaint.isEmpty()) {
            return ResponseEntity.ok().build();
        }
        Complaint complaint = optionalComplaint.get();
        StudentParticipation studentParticipation = (StudentParticipation) participation;
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise, user);
        boolean isTeamParticipation = studentParticipation.getParticipant() instanceof Team;
        boolean isTutorOfTeam = user.getLogin().equals(studentParticipation.getTeam().map(team -> team.getOwner() != null ? team.getOwner().getLogin() : null).orElse(null));
        if (!isAtLeastInstructor && (!isTeamParticipation || !isTutorOfTeam)) {
            complaint.filterSensitiveInformation();
        }
        return complaintDetailResponse(complaint);
    }

    private ResponseEntity<ComplaintDTO> complaintDetailResponse(Complaint complaint) {
        // Hide participation + exercise + course which might include sensitive information.
        complaint.getResult().getSubmission().setParticipation(null);
        return ResponseEntity.ok(ComplaintDTO.of(complaint));
    }

    /**
     * GET complaints: get all the complaints associated with a test run exercise, but filter out the ones that are not about the tutor who is doing the request,
     * since this indicates test run
     * exercises
     *
     * @param exerciseId the id of the exercise we are interested in
     * @param principal  the user that wants to get complaints
     * @return the ResponseEntity with status 200 (OK) and a list of complaints or a list of more feedback requests. The list can be empty
     */
    @GetMapping(value = "complaints", params = { "exerciseId", "!complaintType", "!courseId", "!submissionId" })
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<List<ComplaintDTO>> getComplaintsForTestRunDashboard(Principal principal, @RequestParam Long exerciseId) {
        List<Complaint> responseComplaints = complaintRepository.getAllComplaintsByExerciseIdAndComplaintType(exerciseId, ComplaintType.COMPLAINT);
        responseComplaints = buildComplaintsListForAssessor(responseComplaints, principal, true, true, true);
        return ResponseEntity.ok(responseComplaints.stream().map(ComplaintDTO::of).toList());
    }

    /**
     * Get identified course complaints for instructors, optionally filtered by assessor.
     *
     * @param courseId              the course
     * @param complaintType         the complaint type
     * @param tutorId               optional assessor filter
     * @param allComplaintsForTutor whether to redact foreign assessors in the existing all overview
     * @return the matching complaints
     */
    @GetMapping(value = "complaints", params = { "courseId", "complaintType", "!exerciseId", "!examId", "!submissionId" })
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<ComplaintDTO>> getComplaintsByCourseId(@RequestParam Long courseId, @RequestParam ComplaintType complaintType,
            @RequestParam(required = false) Long tutorId, @RequestParam(required = false) boolean allComplaintsForTutor) {
        return courseComplaintResponse(courseId, complaintType, tutorId, allComplaintsForTutor, false);
    }

    /**
     * Get identified exercise complaints for instructors, optionally filtered by assessor.
     *
     * @param exerciseId            the exercise
     * @param complaintType         the complaint type
     * @param tutorId               optional assessor filter
     * @param allComplaintsForTutor whether to redact foreign assessors in the existing all overview
     * @return the matching complaints
     */
    @GetMapping(value = "complaints", params = { "exerciseId", "complaintType", "!courseId", "!submissionId" })
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<List<ComplaintDTO>> getComplaintsByExerciseId(@RequestParam Long exerciseId, @RequestParam ComplaintType complaintType,
            @RequestParam(required = false) Long tutorId, @RequestParam(required = false) boolean allComplaintsForTutor) {
        return exerciseComplaintResponse(exerciseId, complaintType, tutorId, allComplaintsForTutor, false);
    }

    /**
     * Get anonymous course complaints, deriving the assessor for the mine scope from the current user.
     *
     * @param courseId              the course
     * @param complaintType         the complaint type
     * @param allComplaintsForTutor whether to include all assessors
     * @return the matching anonymous complaints
     */
    @GetMapping(value = "complaints/for-tutor", params = { "courseId", "complaintType", "!exerciseId", "!examId", "!submissionId" })
    @EnforceAtLeastTutorInCourse
    public ResponseEntity<List<ComplaintDTO>> getComplaintsForTutorByCourseId(@RequestParam Long courseId, @RequestParam ComplaintType complaintType,
            @RequestParam(required = false) boolean allComplaintsForTutor) {
        return courseComplaintResponse(courseId, complaintType, userRepository.getUser().getId(), allComplaintsForTutor, true);
    }

    /**
     * Get anonymous exercise complaints, deriving the assessor for the mine scope from the current user.
     *
     * @param exerciseId            the exercise
     * @param complaintType         the complaint type
     * @param allComplaintsForTutor whether to include all assessors
     * @return the matching anonymous complaints
     */
    @GetMapping(value = "complaints/for-tutor", params = { "exerciseId", "complaintType", "!courseId", "!examId", "!submissionId" })
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<List<ComplaintDTO>> getComplaintsForTutorByExerciseId(@RequestParam Long exerciseId, @RequestParam ComplaintType complaintType,
            @RequestParam(required = false) boolean allComplaintsForTutor) {
        return exerciseComplaintResponse(exerciseId, complaintType, userRepository.getUser().getId(), allComplaintsForTutor, true);
    }

    private ResponseEntity<List<ComplaintDTO>> courseComplaintResponse(Long courseId, ComplaintType complaintType, Long tutorId, boolean allComplaintsForTutor,
            boolean tutorProjection) {
        List<Complaint> complaints = tutorId == null || allComplaintsForTutor ? complaintService.getAllComplaintsByCourseId(courseId)
                : complaintService.getAllComplaintsByCourseIdAndTutorId(courseId, tutorId);
        return complaintListResponse(complaints, complaintType, tutorId != null && allComplaintsForTutor, tutorProjection);
    }

    private ResponseEntity<List<ComplaintDTO>> exerciseComplaintResponse(Long exerciseId, ComplaintType complaintType, Long tutorId, boolean allComplaintsForTutor,
            boolean tutorProjection) {
        List<Complaint> complaints = tutorId == null || allComplaintsForTutor ? complaintService.getAllComplaintsByExerciseId(exerciseId)
                : complaintService.getAllComplaintsByExerciseIdAndTutorId(exerciseId, tutorId);
        return complaintListResponse(complaints, complaintType, tutorId != null && allComplaintsForTutor, tutorProjection);
    }

    private ResponseEntity<List<ComplaintDTO>> complaintListResponse(List<Complaint> complaints, ComplaintType complaintType, boolean redactForeignAssessors,
            boolean tutorProjection) {
        filterStudentInformationFromComplaints(complaints, tutorProjection);
        return ResponseEntity.ok(redactForeignAssessors ? getComplaintsByComplaintTypeForTutorOverview(complaints, complaintType, userRepository.getUser())
                : getComplaintsByComplaintType(complaints, complaintType));
    }

    /**
     * GET complaints: get all the complaints filtered by courseId, complaintType, and optionally tutorId.
     *
     * @param examId   the id of the tutor by which we want to filter
     * @param courseId the id of the course we are interested in
     * @return the ResponseEntity with status 200 (OK) and a list of complaints. The list can be empty
     */
    @GetMapping(value = "complaints", params = { "courseId", "examId", "!exerciseId", "!submissionId" })
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<ComplaintDTO>> getComplaintsByCourseIdAndExamId(@RequestParam Long courseId, @RequestParam Long examId) {
        // checks the instructor role in the course and that the exam belongs to the course
        ExamAccessApi api = examAccessApi.orElseThrow(() -> new ExamApiNotPresentException(ExamAccessApi.class));
        api.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        List<Complaint> complaints = complaintService.getAllComplaintsByExamId(examId);
        filterStudentInformationFromComplaints(complaints, false);

        return ResponseEntity.ok(getComplaintsByComplaintType(complaints, ComplaintType.COMPLAINT));
    }

    /**
     * Filter out all complaints that are not of a specified type.
     *
     * @param complaints    list of complaints
     * @param complaintType the type of complaints we want to get
     * @return an unmodifiable list of the complaints
     */
    private List<ComplaintDTO> getComplaintsByComplaintType(List<Complaint> complaints, ComplaintType complaintType) {
        return complaints.stream().filter(complaint -> complaint.getComplaintType() == complaintType).map(ComplaintDTO::of).toList();
    }

    /**
     * Like {@link #getComplaintsByComplaintType}, but for the tutor "All" overview: keeps {@code assessorKey}/{@code assessorLabel}
     * while clearing {@code result.assessor} for complaints assessed by someone other than the caller.
     */
    private List<ComplaintDTO> getComplaintsByComplaintTypeForTutorOverview(List<Complaint> complaints, ComplaintType complaintType, User caller) {
        return complaints.stream().filter(complaint -> complaint.getComplaintType() == complaintType).map(complaint -> {
            ComplaintDTO dto = ComplaintDTO.of(complaint);
            User assessor = complaint.getResult() != null ? complaint.getResult().getAssessor() : null;
            if (assessor != null && !caller.equals(assessor)) {
                return dto.withoutResultAssessor();
            }
            return dto;
        }).toList();
    }

    private void filterOutStudentFromComplaint(Complaint complaint) {
        complaint.setParticipant(null);

        if (complaint.getResult() != null && complaint.getResult().getSubmission().getParticipation() != null) {
            StudentParticipation studentParticipation = (StudentParticipation) complaint.getResult().getSubmission().getParticipation();
            studentParticipation.setParticipant(null);
        }
    }

    /**
     * Removes the participant (i.e. student or team) information from the given complaints when the caller is not allowed to see it.
     * The remaining reduction of the object graph (exercise, submission, participation) is handled by {@link ComplaintDTO#of}, which only exposes a minimal set of fields.
     *
     * @param complaints                     the complaints to filter
     * @param filterOutStudentFromComplaints whether the participant information should be removed
     */
    private void filterStudentInformationFromComplaints(List<Complaint> complaints, boolean filterOutStudentFromComplaints) {
        if (filterOutStudentFromComplaints) {
            complaints.forEach(this::filterOutStudentFromComplaint);
        }
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
                StudentParticipation studentParticipation = (StudentParticipation) complaint.getResult().getSubmission().getParticipation();
                studentParticipation.setParticipant(null);
                studentParticipation.setExercise(null);
                complaint.setParticipant(null);

                responseComplaints.add(complaint);
            }
        });

        return responseComplaints;
    }
}
