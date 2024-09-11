package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintResponseRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.ExamFactory;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.fileupload.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.exercise.modeling.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.exercise.programming.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.text.TextExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.participation.ParticipationFactory;
import de.tum.cit.aet.artemis.participation.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.service.dto.ComplaintAction;
import de.tum.cit.aet.artemis.service.dto.ComplaintRequestDTO;
import de.tum.cit.aet.artemis.service.dto.ComplaintResponseUpdateDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.util.TestResourceUtils;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUpdateDTO;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionWithComplaintDTO;

class AssessmentComplaintIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "assessmentcomplaintintegration";

    @Autowired
    private SubmissionRepository submissionRepo;

    @Autowired
    private ComplaintRepository complaintRepo;

    @Autowired
    private ComplaintResponseRepository complaintResponseRepo;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ComplaintUtilService complaintUtilService;

    private ModelingExercise modelingExercise;

    private ModelingSubmission modelingSubmission;

    private Result modelingAssessment;

    private Complaint complaint;

    private ComplaintRequestDTO complaintRequest;

    private Course course;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 2, 2, 0, 1);

        // Initialize with 3 max complaints and 7 days max complaint due date
        course = courseUtilService.addCourseWithModelingAndTextAndFileUploadExercise();
        modelingExercise = exerciseUtilService.getFirstExerciseWithType(course, ModelingExercise.class);
        saveModelingSubmissionAndAssessment();
        complaint = new Complaint().result(modelingAssessment).complaintText("This is not fair").complaintType(ComplaintType.COMPLAINT);
        complaintRequest = new ComplaintRequestDTO(modelingAssessment.getId(), "This is not fair", ComplaintType.COMPLAINT, Optional.empty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void submitComplaintAboutModelingAssessmentResultBeforeDueDate() throws Exception {
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(2));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(1));
        modelingAssessment.setCompletionDate(modelingExercise.getDueDate().minusDays(1));
        resultRepository.save(modelingAssessment);

        verifySuccessfulComplaint();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void submitComplaintAboutModelingAssessmentResultBeforeAssessmentDueDate() throws Exception {
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(3));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(1));
        modelingAssessment.setCompletionDate(modelingExercise.getAssessmentDueDate().minusDays(1));
        resultRepository.save(modelingAssessment);

        verifySuccessfulComplaint();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void submitComplaintAboutModelingAssessmentResultAfterAssessmentDueDate() throws Exception {
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(3));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(2));
        modelingAssessment.setCompletionDate(modelingExercise.getAssessmentDueDate().plusDays(1));
        resultRepository.save(modelingAssessment);

        verifySuccessfulComplaint();
    }

    private void verifySuccessfulComplaint() throws Exception {
        request.post("/api/complaints", complaintRequest, HttpStatus.CREATED);

        Optional<Complaint> storedComplaint = complaintRepo.findByResultId(modelingAssessment.getId());
        assertThat(storedComplaint).as("complaint is saved").isPresent();
        assertThat(storedComplaint.orElseThrow().getComplaintText()).as("complaint text got correctly saved").isEqualTo(complaint.getComplaintText());
        assertThat(storedComplaint.orElseThrow().isAccepted()).as("accepted flag of complaint is not set").isNull();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
        Result result = storedComplaint.orElseThrow().getResult();
        assertThat(result.getId()).isEqualTo(storedResult.getId());
        // set date to UTC for comparison
        storedResult.setCompletionDate(ZonedDateTime.ofInstant(storedResult.getCompletionDate().toInstant(), ZoneId.of("UTC")));
        // TODO add assertion
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void submitComplaintAboutModellingAssessment_complaintLimitNotReached() throws Exception {
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(2));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(1));

        // 2 complaints are allowed, the course is created with 3 max complaints
        complaintUtilService.addComplaints(TEST_PREFIX + "student1", modelingAssessment.getParticipation(), 2, ComplaintType.COMPLAINT);

        request.post("/api/complaints", complaintRequest, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void submitComplaintAboutModelingAssessment_complaintLimitReached() throws Exception {
        complaintUtilService.addComplaints(TEST_PREFIX + "student1", modelingAssessment.getParticipation(), 3, ComplaintType.COMPLAINT);

        request.post("/api/complaints", complaintRequest, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is not saved").isNotPresent();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is false").isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void requestMoreFeedbackAboutModelingAssessment_noLimit() throws Exception {
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(2));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(1));

        complaintUtilService.addComplaints(TEST_PREFIX + "student1", modelingAssessment.getParticipation(), 3, ComplaintType.MORE_FEEDBACK);

        request.post("/api/complaints", complaintRequest, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();

        // Only one complaint is possible for exercise regardless of its type
        var moreFeedbackRequest = new ComplaintRequestDTO(modelingAssessment.getId(), "Please explain", ComplaintType.MORE_FEEDBACK, Optional.empty());
        request.post("/api/complaints", moreFeedbackRequest, HttpStatus.BAD_REQUEST);
        assertThat(complaintRepo.findByResultId(modelingAssessment.getId()).orElseThrow().getComplaintType()).as("more feedback request is not saved")
                .isNotEqualTo(ComplaintType.MORE_FEEDBACK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void submitComplaintAboutModelingAssessment_validDueDate() throws Exception {
        // Set due date for mock course to 2 weeks. Complaint created one week after result date is fine.
        course.setMaxComplaintTimeDays(14);
        courseRepository.save(course);

        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusWeeks(1));
        exerciseUtilService.updateResultCompletionDate(modelingAssessment.getId(), ZonedDateTime.now().minusWeeks(1));

        request.post("/api/complaints", complaintRequest, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void submitComplaintAboutModelingAssessment_assessmentTooOld() throws Exception {
        // 3 weeks is already past the due date
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusWeeks(4));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusWeeks(3));
        exerciseUtilService.updateResultCompletionDate(modelingAssessment.getId(), ZonedDateTime.now().minusWeeks(2));

        request.post("/api/complaints", complaintRequest, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is not saved").isNotPresent();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is false").isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void submitComplaintResponse_rejectComplaint() throws Exception {
        complaint = complaintRepo.save(complaint);
        // creating the initial complaintResponse
        complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO("rejected", false, ComplaintAction.RESOLVE_COMPLAINT);

        request.patch("/api/complaints/" + complaint.getId() + "/response", complaintResponseUpdate, HttpStatus.OK);

        Complaint storedComplaint = complaintRepo.findByResultId(modelingAssessment.getId()).orElseThrow();
        assertThat(storedComplaint.isAccepted()).as("complaint is not accepted").isFalse();
        Result storedResult = resultRepository.findWithBidirectionalSubmissionAndFeedbackAndAssessorAndAssessmentNoteAndTeamStudentsByIdElseThrow(modelingAssessment.getId());
        Result updatedResult = storedResult.getSubmission().getLatestResult();
        participationUtilService.checkFeedbackCorrectlyStored(modelingAssessment.getFeedbacks(), updatedResult.getFeedbacks(), FeedbackType.MANUAL);
        final String[] ignoringFields = { "feedbacks", "submission", "participation", "assessor" };
        assertThat(storedResult).as("only feedbacks are changed in the result").usingRecursiveComparison().ignoringFields(ignoringFields).isEqualTo(modelingAssessment);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void submitComplaintResponse_updateAssessment() throws Exception {
        complaint = complaintRepo.save(complaint);
        // creating the initial complaintResponse
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(true);
        complaintResponse.setResponseText("Accepted");

        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        feedbacks.forEach((feedback -> feedback.setType(FeedbackType.MANUAL)));
        final var assessmentUpdate = new AssessmentUpdateDTO(feedbacks, complaintResponse, null);
        Result receivedResult = request.putWithResponseBody("/api/modeling-submissions/" + modelingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate,
                Result.class, HttpStatus.OK);

        assertThat(((StudentParticipation) receivedResult.getParticipation()).getStudent()).as("student is hidden in response").isEmpty();
        Complaint storedComplaint = complaintRepo.findByResultId(modelingAssessment.getId()).orElseThrow();
        assertThat(storedComplaint.isAccepted()).as("complaint is accepted").isTrue();
        Result result = storedComplaint.getResult();
        // set dates to UTC and round to milliseconds for comparison
        result.setCompletionDate(ZonedDateTime.ofInstant(result.getCompletionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        modelingAssessment.setCompletionDate(ZonedDateTime.ofInstant(modelingAssessment.getCompletionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).orElseThrow();
        Result resultAfterComplaintResponse = resultRepository.findByIdWithEagerFeedbacksAndAssessor(receivedResult.getId()).orElseThrow();
        participationUtilService.checkFeedbackCorrectlyStored(feedbacks, resultAfterComplaintResponse.getFeedbacks(), FeedbackType.MANUAL);
        assertThat(storedResult.getAssessor()).as("assessor is still the original one").isEqualTo(modelingAssessment.getAssessor());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void submitComplaintResponseComplaintResponseTextLimitExceeded() throws Exception {
        complaint = complaintRepo.save(complaint);
        course = courseUtilService.updateCourseComplaintResponseTextLimit(course, 25);
        // creating the initial complaintResponse
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(true);
        // 26 characters
        complaintResponse.setResponseText("abcdefghijklmnopqrstuvwxyz");

        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        feedbacks.forEach((feedback -> feedback.setType(FeedbackType.MANUAL)));
        final var assessmentUpdate = new AssessmentUpdateDTO(feedbacks, complaintResponse, null);
        request.putWithResponseBody("/api/modeling-submissions/" + modelingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void submitComplaintResponseComplaintResponseTextLimitNotExceeded() throws Exception {
        complaint = complaintRepo.save(complaint);
        course = courseUtilService.updateCourseComplaintResponseTextLimit(course, 26);
        // creating the initial complaintResponse
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(true);
        // 26 characters
        complaintResponse.setResponseText("abcdefghijklmnopqrstuvwxyz");

        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        feedbacks.forEach((feedback -> feedback.setType(FeedbackType.MANUAL)));
        final var assessmentUpdate = new AssessmentUpdateDTO(feedbacks, complaintResponse, null);
        request.putWithResponseBody("/api/modeling-submissions/" + modelingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class, HttpStatus.OK);
        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void submitComplaintResponse_examExercise() throws Exception {
        TextExercise examExercise = textExerciseUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        Course examCourse = examExercise.getCourseViaExerciseGroupOrCourseMember();

        Exam exam = examExercise.getExam();
        exam.setExamStudentReviewStart(ZonedDateTime.now().minusHours(1));
        exam.setExamStudentReviewEnd(ZonedDateTime.now().plusHours(1));
        examRepository.save(exam);

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        Complaint examExerciseComplaint = new Complaint().result(textSubmission.getLatestResult()).complaintType(ComplaintType.COMPLAINT);
        examExerciseComplaint = complaintRepo.save(examExerciseComplaint);

        examCourse = courseUtilService.updateCourseComplaintResponseTextLimit(examCourse, 20);
        courseRepository.save(examCourse);

        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", examExerciseComplaint);
        complaintResponse.getComplaint().setAccepted(true);
        // 26 characters, above course limit but valid for exam exercises (where complaint limits don't apply)
        complaintResponse.setResponseText("abcdefghijklmnopqrstuvwxyz");
        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO("abcdefghijklmnopqrstuvwxyz", true, ComplaintAction.RESOLVE_COMPLAINT);

        request.patch("/api/complaints/" + examExerciseComplaint.getId() + "/response", complaintResponseUpdate, HttpStatus.OK);

        assertThat(textSubmission.getLatestResult()).isNotNull();
        assertThat(complaintRepo.findByResultId(textSubmission.getLatestResult().getId())).isPresent();

        Complaint finalExamExerciseComplaint = examExerciseComplaint;
        await().untilAsserted(() -> assertThat(complaintResponseRepo.findByComplaint_Id(finalExamExerciseComplaint.getId())).isPresent());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getComplaintByResultIdNoComplaintExists() throws Exception {
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("submissionId", modelingSubmission.getId().toString());

        request.get("/api/complaints", HttpStatus.OK, Void.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getComplaintByResultId_assessorHiddenForStudent() throws Exception {
        submissionRepo.save(modelingSubmission);
        complaintRepo.save(complaint);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("submissionId", modelingSubmission.getId().toString());
        Complaint receivedComplaint = request.get("/api/complaints", HttpStatus.OK, Complaint.class, params);

        assertThat(receivedComplaint.getResult().getAssessor()).as("assessor is not set").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void getComplaintByResultId_studentAndNotOwner_forbidden() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("submissionId", modelingSubmission.getId().toString());

        request.get("/api/complaints", HttpStatus.FORBIDDEN, Complaint.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1")
    void getComplaintByResultid_instructor_sensitiveDataHidden() throws Exception {
        complaintRepo.save(complaint);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("submissionId", modelingSubmission.getId().toString());

        final var received = request.get("/api/complaints", HttpStatus.OK, Complaint.class, params);

        assertThat(received.getResult().getParticipation()).as("Complaint should not contain participation").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void getComplaintByResultid_tutor_sensitiveDataHidden() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("submissionId", modelingSubmission.getId().toString());

        final var received = request.get("/api/complaints", HttpStatus.OK, Complaint.class, params);

        assertThat(received.getParticipant()).as("Tutors should not see the student of a complaint").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getComplaintByResultid_student_sensitiveDataHidden() throws Exception {
        complaint = complaintRepo.save(complaint);
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponseRepo.save(complaintResponse);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("submissionId", modelingSubmission.getId().toString());

        final var received = request.get("/api/complaints", HttpStatus.OK, Complaint.class, params);

        assertThat(received.getParticipant()).as("The participant should always be hidden").isNull();
        assertThat(received.getResult().getAssessor()).as("Students should not see the initial assessor").isNull();
        assertThat(received.getComplaintResponse().getReviewer()).as("Students should not see the complaint reviewer").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getComplaintsByCourseIdTutorIsNotTutorForCourse() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        course.setInstructorGroupName("test");
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        params.add("courseId", modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId().toString());

        request.getList("/api/complaints", HttpStatus.FORBIDDEN, Complaint.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getComplaintsByCourseId_tutor_sensitiveDataHidden() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        params.add("courseId", modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId().toString());

        final var complaints = request.getList("/api/complaints", HttpStatus.OK, Complaint.class, params);

        complaints.forEach(c -> checkComplaintContainsNoSensitiveData(c, true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getComplaintsByCourseId_tutor_allComplaintsForTutor() throws Exception {
        complaint.getResult().setAssessor(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        resultRepository.save(complaint.getResult());
        complaintRepo.save(complaint);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        params.add("courseId", modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId().toString());

        final var tutorComplaints = request.getList("/api/complaints", HttpStatus.OK, Complaint.class, params);
        assertThat(tutorComplaints).isEmpty();

        params.add("allComplaintsForTutor", "true");
        final var allComplaints = request.getList("/api/complaints", HttpStatus.OK, Complaint.class, params);

        assertThat(allComplaints).hasSize(1);
        allComplaints.forEach(c -> checkComplaintContainsNoSensitiveData(c, true));

        // Check assessor is filtered out if the user was not the assessor.
        allComplaints.forEach(c -> assertThat(c.getResult().getAssessor()).isNull());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getComplaintsForAssessmentDashboardTutorIsNotTutorForCourse() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        course.setInstructorGroupName("test");
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);

        final var params = new LinkedMultiValueMap<String, String>();
        request.getList("/api/exercises/" + modelingExercise.getId() + "/submissions-with-complaints", HttpStatus.FORBIDDEN, Complaint.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getComplaintsForAssessmentDashboard_sameTutorAsAssessor_studentInfoHidden() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        complaint.getResult().setHasComplaint(true);
        resultRepository.save(complaint.getResult());

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        final var submissionWithComplaintDTOs = request.getList("/api/exercises/" + modelingExercise.getId() + "/submissions-with-complaints", HttpStatus.OK,
                SubmissionWithComplaintDTO.class, params);

        submissionWithComplaintDTOs.forEach(dto -> {
            final var participation = (StudentParticipation) dto.complaint().getResult().getParticipation();
            assertThat(participation.getStudent()).as("No student information").isEmpty();
            assertThat(dto.complaint().getParticipant()).as("No student information").isNull();
            assertThat(participation.getExercise()).as("No additional exercise information").isNull();
            assertThat(((StudentParticipation) dto.submission().getParticipation()).getParticipant()).as("No student information in participation").isNull();
            assertThat(dto.submission().getParticipation().getExercise()).as("No additional exercise information").isNull();

        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getComplaintsForAssessmentDashboardTestRunTutorIsNotTutorForCourse() throws Exception {
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        complaint.setParticipant(instructor);
        complaint.getResult().setAssessor(instructor);
        resultRepository.save(complaint.getResult());
        complaint = complaintRepo.save(complaint);
        course.setInstructorGroupName("test");
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        params.add("exerciseId", modelingExercise.getId().toString());
        request.getList("/api/complaints", HttpStatus.FORBIDDEN, Complaint.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getComplaintsForAssessmentDashboard_testRun() throws Exception {
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        complaint.setParticipant(instructor);
        complaint.getResult().setAssessor(instructor);
        resultRepository.save(complaint.getResult());
        complaint = complaintRepo.save(complaint);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("exerciseId", modelingExercise.getId().toString());

        final var complaints = request.getList("/api/complaints", HttpStatus.OK, Complaint.class, params);
        assertThat(complaints).hasSize(1);
        complaints.forEach(compl -> {
            assertThat(compl.getResult()).isEqualTo(complaint.getResult());
            assertThat(compl.getParticipant()).as("No student information").isNull();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getComplaintsForAssessmentDashboard_testRun_emptyComplaints() throws Exception {

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        params.add("exerciseId", modelingExercise.getId().toString());

        final var complaints = request.getList("/api/complaints", HttpStatus.OK, Complaint.class, params);
        assertThat(complaints).hasSize(0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSubmittedComplaints_byComplaintType() throws Exception {
        complaintUtilService.addComplaints(TEST_PREFIX + "student1", modelingAssessment.getParticipation(), 1, ComplaintType.COMPLAINT);
        complaintUtilService.addComplaints(TEST_PREFIX + "student1", modelingAssessment.getParticipation(), 2, ComplaintType.MORE_FEEDBACK);

        String complaintsUrl = "/api/complaints";
        LinkedMultiValueMap<String, String> paramsExercise = new LinkedMultiValueMap<>();
        LinkedMultiValueMap<String, String> paramsCourse = new LinkedMultiValueMap<>();
        paramsExercise.add("complaintType", ComplaintType.COMPLAINT.toString());
        paramsExercise.add("exerciseId", modelingExercise.getId().toString());
        paramsCourse.add("complaintType", ComplaintType.COMPLAINT.toString());
        paramsCourse.add("courseId", modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId().toString());
        List<Complaint> complaintsByCourse = request.getList(complaintsUrl, HttpStatus.OK, Complaint.class, paramsCourse);
        List<Complaint> complaintsByExercise = request.getList(complaintsUrl, HttpStatus.OK, Complaint.class, paramsExercise);
        assertThat(complaintsByExercise).hasSameSizeAs(complaintsByCourse).hasSize(1);
        assertThat(complaintsByCourse).hasSize(1).allMatch(complaint -> complaint.getComplaintType() == ComplaintType.COMPLAINT);

        paramsCourse.set("complaintType", ComplaintType.MORE_FEEDBACK.toString());
        paramsExercise.set("complaintType", ComplaintType.MORE_FEEDBACK.toString());
        complaintsByCourse = request.getList(complaintsUrl, HttpStatus.OK, Complaint.class, paramsCourse);
        complaintsByExercise = request.getList(complaintsUrl, HttpStatus.OK, Complaint.class, paramsExercise);
        assertThat(complaintsByCourse).hasSameSizeAs(complaintsByExercise).hasSize(2);
        assertThat(complaintsByCourse).hasSize(2).allMatch(complaint -> complaint.getComplaintType() == ComplaintType.MORE_FEEDBACK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSubmittedComplaintsForProgrammingExercise() throws Exception {
        var programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        var programmingSubmission = ParticipationFactory.generateProgrammingSubmission(true);

        programmingExerciseUtilService.addProgrammingSubmissionWithResultAndAssessor(programmingExercise, programmingSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1",
                AssessmentType.MANUAL, false);
        courseRepository.save(course);
        complaintUtilService.addComplaintToSubmission(programmingSubmission, TEST_PREFIX + "student1", ComplaintType.COMPLAINT);
        var programmingComplaint = complaintRepo.findByResultId(programmingSubmission.getResultWithComplaint().getId()).orElseThrow();
        programmingComplaint.setComplaintText("Programming exercise complaint");
        complaintRepo.save(programmingComplaint);

        String coursesUrl = "/api/complaints";
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("complaintType", ComplaintType.COMPLAINT.toString());
        params.add("courseId", course.getId().toString());
        List<Complaint> complaints = request.getList(coursesUrl, HttpStatus.OK, Complaint.class, params);
        assertThat(complaints).hasSize(1);
        Complaint complaintFromServer = complaints.getFirst();
        assertThat(complaintFromServer.getId()).isEqualTo(programmingComplaint.getId());
        assertThat(complaintFromServer.getComplaintText()).isEqualTo(programmingComplaint.getComplaintText());

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getSubmittedComplaintsForFileUploadExercise() throws Exception {
        var fileUploadExercise = exerciseUtilService.getFirstExerciseWithType(course, FileUploadExercise.class);
        var fileUploadSubmission = ParticipationFactory.generateFileUploadSubmission(true);

        fileUploadSubmission = fileUploadExerciseUtilService.saveFileUploadSubmissionWithResultAndAssessor(fileUploadExercise, fileUploadSubmission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1");
        courseRepository.save(course);
        complaintUtilService.addComplaintToSubmission(fileUploadSubmission, TEST_PREFIX + "student1", ComplaintType.COMPLAINT);
        var fileUploadComplaint = complaintRepo.findByResultId(fileUploadSubmission.getResultWithComplaint().getId()).orElseThrow();
        fileUploadComplaint.setComplaintText("File upload complaint");
        complaintRepo.save(fileUploadComplaint);

        String coursesUrl = "/api/complaints";
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("complaintType", ComplaintType.COMPLAINT.toString());
        params.add("courseId", course.getId().toString());
        List<Complaint> complaints = request.getList(coursesUrl, HttpStatus.OK, Complaint.class, params);
        assertThat(complaints).hasSize(1);
        Complaint complaintFromServer = complaints.getFirst();
        assertThat(complaintFromServer.getId()).isEqualTo(fileUploadComplaint.getId());
        assertThat(complaintFromServer.getComplaintText()).isEqualTo(fileUploadComplaint.getComplaintText());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getSubmittedComplaints_asStudent_forbidden() throws Exception {
        complaintRepo.save(complaint);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("complaintType", ComplaintType.COMPLAINT.toString());
        params.add("exerciseId", modelingExercise.getId().toString());

        request.getList("/api/complaints", HttpStatus.FORBIDDEN, ComplaintResponse.class, params);
    }

    private void saveModelingSubmissionAndAssessment() throws Exception {
        modelingSubmission = ParticipationFactory.generateModelingSubmission(TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        modelingSubmission = modelingExerciseUtilService.addModelingSubmission(modelingExercise, modelingSubmission, TEST_PREFIX + "student1");
        modelingAssessment = modelingExerciseUtilService.addModelingAssessmentForSubmission(modelingExercise, modelingSubmission,
                "test-data/model-assessment/assessment.54727.v2.json", TEST_PREFIX + "tutor1", true);
    }

    private void checkComplaintContainsNoSensitiveData(Complaint receivedComplaint, boolean shouldStudentBeFilteredOut) {
        if (shouldStudentBeFilteredOut) {
            checkIfNoStudentInformationPresent(receivedComplaint);
        }

        checkIfNoSensitiveExerciseDataPresent(receivedComplaint);
        checkIfNoSensitiveSubmissionDataPresent(receivedComplaint);
    }

    private void checkIfNoSensitiveSubmissionDataPresent(Complaint receivedComplaint) {
        final var submission = receivedComplaint.getResult().getSubmission();
        if (submission != null) {
            assertThat(submission.getParticipation()).as("Submission only contains ID").isNull();
            assertThat(submission.getLatestResult()).as("Submission only contains ID").isNull();
            assertThat(submission.getSubmissionDate()).as("Submission only contains ID").isNull();
        }
    }

    private void checkIfNoSensitiveExerciseDataPresent(Complaint receivedComplaint) {
        final var participation = receivedComplaint.getResult().getParticipation();
        if (participation != null && participation.getExercise() != null) {
            final var exercise = participation.getExercise();
            assertThat(exercise.getGradingInstructions()).as("Exercise only contains title and ID").isNull();
            assertThat(exercise.getTotalNumberOfAssessments()).as("Exercise only contains title and ID").isNull();
            assertThat(exercise.getNumberOfComplaints()).as("Exercise only contains title and ID").isNull();
            assertThat(exercise.getNumberOfMoreFeedbackRequests()).as("Exercise only contains title and ID").isNull();
            assertThat(exercise.getNumberOfSubmissions()).as("Exercise only contains title and ID").isNull();
            assertThat(exercise.getProblemStatement()).as("Exercise only contains title and ID").isNull();
            assertThat(exercise.getCourseViaExerciseGroupOrCourseMember()).as("Exercise only contains title and ID").isNull();
            assertThat(exercise.getAssessmentDueDate()).as("Exercise only contains title and ID").isNull();
            assertThat(exercise.getStudentParticipations()).as("Exercise only contains title and ID").isNullOrEmpty();
            assertThat(exercise.getTutorParticipations()).as("Exercise only contains title and ID").isNullOrEmpty();
            // TODO check exercise type specific sensitive attributes
            switch (exercise) {
                case ModelingExercise aModelingExercise -> {
                    assertThat(aModelingExercise.getExampleSolutionModel()).as("Exercise only contains title and ID").isNull();
                    assertThat(aModelingExercise.getExampleSolutionExplanation()).as("Exercise only contains title and ID").isNull();
                }
                case TextExercise textExercise -> {
                    assertThat(textExercise.getExampleSolution()).as("Exercise only contains title and ID").isNull();
                    assertThat(textExercise.getExampleSubmissions()).as("Exercise only contains title and ID").isNull();
                }
                case ProgrammingExercise programmingExercise -> assertThat(programmingExercise.getProgrammingLanguage()).as("Exercise only contains title and ID").isNull();
                default -> {
                }
            }
        }
    }

    private void checkIfNoStudentInformationPresent(Complaint receivedComplaint) {
        assertThat(receivedComplaint.getParticipant()).as("Student should not be contained").isNull();

        if (complaint.getResult() != null && complaint.getResult().getParticipation() != null) {
            assertThat(((StudentParticipation) receivedComplaint.getResult().getParticipation()).getStudent()).as("Result in complaint shouldn't contain student participation")
                    .isEmpty();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getComplaintsByExerciseIdTutorIsNotTutorForCourse() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        course.setInstructorGroupName("test");
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        params.add("exerciseId", complaint.getResult().getParticipation().getExercise().getId().toString());
        request.getList("/api/complaints", HttpStatus.FORBIDDEN, Complaint.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getComplaintsByExerciseId_tutor_sensitiveDataHidden() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        var exercise = complaint.getResult().getParticipation().getExercise();
        params.add("exerciseId", exercise.getId().toString());

        var complaints = request.getList("/api/complaints", HttpStatus.OK, Complaint.class, params);
        complaints.forEach(complaint -> checkComplaintContainsNoSensitiveData(complaint, true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getNumberOfAllowedComplaintsInCourseComplaintsDisabled() throws Exception {
        // complaints enabled will return zero
        course.setMaxComplaintTimeDays(-1);
        courseRepository.save(course);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("courseId", modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId().toString());
        request.get("/api/complaints", HttpStatus.BAD_REQUEST, Long.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getNumberOfAllowedComplaintsInCourseTeamMode() throws Exception {
        complaint.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        complaintRepo.save(complaint);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("courseId", modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId().toString());
        params.add("teamMode", "true");
        request.get("/api/complaints", HttpStatus.BAD_REQUEST, Long.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForExamExerciseWithinStudentReviewTime() throws Exception {
        final TextExercise examExercise = textExerciseUtilService.addCourseExamWithReviewDatesExerciseGroupWithOneTextExercise();
        final long examId = examExercise.getExerciseGroup().getExam().getId();
        final TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        final var examExerciseComplaint = new ComplaintRequestDTO(textSubmission.getLatestResult().getId(), "This is not fair", ComplaintType.COMPLAINT, Optional.of(examId));

        final String url = "/api/complaints";
        request.post(url, examExerciseComplaint, HttpStatus.CREATED);

        Optional<Complaint> storedComplaint = complaintRepo.findByResultId(textSubmission.getLatestResult().getId());
        assertThat(storedComplaint).as("complaint is saved").isPresent();
        assertThat(storedComplaint.orElseThrow().getComplaintText()).as("complaint text got correctly saved").isEqualTo(examExerciseComplaint.complaintText());
        assertThat(storedComplaint.get().isAccepted()).as("accepted flag of complaint is not set").isNull();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(textSubmission.getLatestResult().getId()).orElseThrow();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
        // set date to UTC for comparison
        storedResult.setCompletionDate(ZonedDateTime.ofInstant(storedResult.getCompletionDate().toInstant(), ZoneId.of("UTC")));
        // TODO add assertion
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForCourseExerciseUsingTheExamExerciseCall_badRequest() throws Exception {
        // "Mock Exam" which id is used to call the wrong REST-Call
        final Exam exam = ExamFactory.generateExam(course);
        examRepository.save(exam);
        // The complaint is about a course exercise, not an exam exercise
        var complaintRequest = new ComplaintRequestDTO(complaint.getResult().getId(), complaint.getComplaintText(), complaint.getComplaintType(), Optional.of(exam.getId()));

        request.post("/api/complaints", complaintRequest, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForExamExerciseUsingTheCourseExerciseCall_badRequest() throws Exception {
        // Set up Exam, Exercise, Participation and Complaint
        final TextExercise examExercise = textExerciseUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        final TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        var examExerciseComplaint = new ComplaintRequestDTO(textSubmission.getLatestResult().getId(), "This is not fair", ComplaintType.COMPLAINT, Optional.empty());
        // The complaint is about an exam exercise, but the REST-Call for course exercises is used
        request.post("/api/complaints", examExerciseComplaint, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForExamExerciseOutsideOfStudentReviewTime_badRequest() throws Exception {
        final TextExercise examExercise = textExerciseUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        final long examId = examExercise.getExerciseGroup().getExam().getId();
        final TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        final var examExerciseComplaint = new ComplaintRequestDTO(1, "This is not fair", ComplaintType.COMPLAINT, Optional.of(examId));

        final String url = "/api/complaints";
        request.post(url, examExerciseComplaint, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetComplaintsByCourseIdAndExamIdTutorIsNotTutorForCourse() throws Exception {
        final TextExercise examExercise = textExerciseUtilService.addCourseExamWithReviewDatesExerciseGroupWithOneTextExercise();
        final long examId = examExercise.getExerciseGroup().getExam().getId();
        final long courseId = examExercise.getExerciseGroup().getExam().getCourse().getId();
        Course course = examExercise.getExerciseGroup().getExam().getCourse();
        course.setInstructorGroupName("test");
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("examId", String.valueOf(examId));
        params.add("courseId", String.valueOf(courseId));

        request.getList("/api/complaints", HttpStatus.FORBIDDEN, Complaint.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetComplaintsByCourseIdAndExamId() throws Exception {
        final TextExercise examExercise = textExerciseUtilService.addCourseExamWithReviewDatesExerciseGroupWithOneTextExercise();
        final long examId = examExercise.getExerciseGroup().getExam().getId();
        final long courseId = examExercise.getExerciseGroup().getExam().getCourse().getId();
        final TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        final var examExerciseComplaint = new ComplaintRequestDTO(textSubmission.getLatestResult().getId(), "This is not fair", ComplaintType.COMPLAINT, Optional.of(examId));

        final String url = "/api/complaints";
        request.post(url, examExerciseComplaint, HttpStatus.CREATED);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("examId", String.valueOf(examId));
        params.add("courseId", String.valueOf(courseId));

        Optional<Complaint> storedComplaint = complaintRepo.findByResultId(textSubmission.getLatestResult().getId());
        request.get("/api/complaints", HttpStatus.FORBIDDEN, List.class, params);
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        request.get("/api/complaints", HttpStatus.FORBIDDEN, List.class, params);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        var fetchedComplaints = request.getList("/api/complaints", HttpStatus.OK, Complaint.class, params);
        assertThat(fetchedComplaints.getFirst().getId()).isEqualTo(storedComplaint.orElseThrow().getId().intValue());
        assertThat(fetchedComplaints.getFirst().getComplaintText()).isEqualTo(storedComplaint.get().getComplaintText());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForExerciseComplaintExceededTextLimit() throws Exception {
        course = courseUtilService.updateCourseComplaintTextLimit(course, 25);
        // 26 characters
        complaint.setComplaintText("abcdefghijklmnopqrstuvwxyz");
        var complaintRequest = new ComplaintRequestDTO(complaint.getResult().getId(), "abcdefghijklmnopqrstuvwxyz", complaint.getComplaintType(), Optional.empty());
        request.post("/api/complaints", complaintRequest, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForExerciseComplaintNotExceededTextLimit() throws Exception {
        exerciseUtilService.updateExerciseDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(2));
        exerciseUtilService.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusDays(1));
        course = courseUtilService.updateCourseComplaintTextLimit(course, 27);
        // 26 characters
        complaint.setComplaintText("abcdefghijklmnopqrstuvwxyz");
        var complaintRequest = new ComplaintRequestDTO(complaint.getResult().getId(), "abcdefghijklmnopqrstuvwxyz", complaint.getComplaintType(), Optional.empty());
        request.post("/api/complaints", complaintRequest, HttpStatus.CREATED);
        Optional<Complaint> storedComplaint = complaintRepo.findByResultId(modelingAssessment.getId());
        assertThat(storedComplaint).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForExam_courseComplaintsEnabled_exceededCourseLimit_success() throws Exception {
        TextExercise examExercise = textExerciseUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        Course examCourse = examExercise.getCourseViaExerciseGroupOrCourseMember();
        examCourse = courseUtilService.updateCourseComplaintTextLimit(examCourse, 25);
        // enable course complaints
        examCourse.setMaxComplaintTimeDays(3);
        courseRepository.save(examCourse);
        // 26 characters, exceeds course limit but lower than 2000 --> allowed for exam exercise
        String complaintText = "abcdefghijklmnopqrstuvwxyz";
        var examSubmission = createComplaintForExamExercise(examExercise, complaintText, HttpStatus.CREATED);
        Optional<Complaint> storedComplaint = complaintRepo.findByResultSubmissionId(examSubmission.getId());
        assertThat(storedComplaint).isPresent();
        assertThat(storedComplaint.get().getComplaintText()).isEqualTo(complaintText);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForExam_courseComplaintsDisabled_notExceededTextLimit() throws Exception {
        TextExercise examExercise = textExerciseUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        Course examCourse = examExercise.getCourseViaExerciseGroupOrCourseMember();
        // disable course complaints
        examCourse.setMaxComplaintTimeDays(0);
        courseRepository.save(examCourse);
        // less than 2000 characters
        var examSubmission = createComplaintForExamExercise(examExercise, "abcdefghijklmnopqrstuvwxyz", HttpStatus.CREATED);
        Optional<Complaint> storedComplaint = complaintRepo.findByResultSubmissionId(examSubmission.getId());
        assertThat(storedComplaint).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitComplaintForExam_courseComplaintsDisabled_exceededTextLimit() throws Exception {
        TextExercise examExercise = textExerciseUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        Course examCourse = examExercise.getCourseViaExerciseGroupOrCourseMember();
        // disable course complaints
        examCourse.setMaxComplaintTimeDays(0);
        courseRepository.save(examCourse);
        // 2004 characters (4 over the limit of 2000)
        createComplaintForExamExercise(examExercise, "abcd".repeat(501), HttpStatus.BAD_REQUEST);
    }

    private Submission createComplaintForExamExercise(TextExercise examExercise, String complaintText, HttpStatus expectedStatus) throws Exception {
        examExercise.getExam().setExamStudentReviewStart(ZonedDateTime.now().minusHours(1));
        examExercise.getExam().setExamStudentReviewEnd(ZonedDateTime.now().plusHours(1));
        examRepository.save(examExercise.getExam());
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        var examId = examExercise.getExam().getId();
        final var examExerciseComplaint = new ComplaintRequestDTO(textSubmission.getLatestResult().getId(), complaintText, ComplaintType.COMPLAINT, Optional.of(examId));

        String url = "/api/complaints";
        request.post(url, examExerciseComplaint, expectedStatus);
        return textSubmission;
    }
}
