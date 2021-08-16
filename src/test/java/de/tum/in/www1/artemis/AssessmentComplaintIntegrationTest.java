package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ComplaintResponseRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.util.FileUtils;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionWithComplaintDTO;

public class AssessmentComplaintIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private ComplaintRepository complaintRepo;

    @Autowired
    private ComplaintResponseRepository complaintResponseRepo;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ObjectMapper mapper;

    private ModelingExercise modelingExercise;

    private ModelingSubmission modelingSubmission;

    private Result modelingAssessment;

    private Complaint complaint;

    private Complaint moreFeedbackRequest;

    private Course course;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(2, 2, 0, 1);

        // Initialize with 3 max complaints and 7 days max complaint deadline
        course = database.addCourseWithOneModelingExercise();
        modelingExercise = (ModelingExercise) course.getExercises().iterator().next();
        saveModelingSubmissionAndAssessment();
        complaint = new Complaint().result(modelingAssessment).complaintText("This is not fair").complaintType(ComplaintType.COMPLAINT);
        // complaint.setParticipant(students.get(0));
        moreFeedbackRequest = new Complaint().result(modelingAssessment).complaintText("Please explain").complaintType(ComplaintType.MORE_FEEDBACK);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1")
    public void submitComplaintAboutModelingAssessment() throws Exception {
        request.post("/api/complaints", complaint, HttpStatus.CREATED);

        Optional<Complaint> storedComplaint = complaintRepo.findByResultId(modelingAssessment.getId());
        assertThat(storedComplaint).as("complaint is saved").isPresent();
        assertThat(storedComplaint.get().getComplaintText()).as("complaint text got correctly saved").isEqualTo(complaint.getComplaintText());
        assertThat(storedComplaint.get().isAccepted()).as("accepted flag of complaint is not set").isNull();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).get();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
        Result result = storedComplaint.get().getResult();
        // set date to UTC for comparison as the date saved in resultBeforeComplaint string is in UTC
        storedResult.setCompletionDate(ZonedDateTime.ofInstant(storedResult.getCompletionDate().toInstant(), ZoneId.of("UTC")));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void submitComplaintWithId() throws Exception {
        complaint.setId(1L);
        request.post("/api/complaints", complaint, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void submitComplaintResultIsNull() throws Exception {
        complaint.setResult(null);
        request.post("/api/complaints", complaint, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1")
    public void submitComplaintAboutModellingAssessment_complaintLimitNotReached() throws Exception {
        // 2 complaints are allowed, the course is created with 3 max complaints
        database.addComplaints("student1", modelingAssessment.getParticipation(), 2, ComplaintType.COMPLAINT);

        request.post("/api/complaints", complaint, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).get();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
    }

    @Test
    @WithMockUser(username = "student1")
    public void submitComplaintAboutModelingAssessment_complaintLimitReached() throws Exception {
        database.addComplaints("student1", modelingAssessment.getParticipation(), 3, ComplaintType.COMPLAINT);

        request.post("/api/complaints", complaint, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is not saved").isNotPresent();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).get();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is false").isFalse();
    }

    @Test
    @WithMockUser(username = "student1")
    public void requestMoreFeedbackAboutModelingAssessment_noLimit() throws Exception {
        database.addComplaints("student1", modelingAssessment.getParticipation(), 3, ComplaintType.MORE_FEEDBACK);

        request.post("/api/complaints", complaint, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).get();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();

        // Only one complaint is possible for exercise regardless of its type
        request.post("/api/complaints", moreFeedbackRequest, HttpStatus.BAD_REQUEST);
        assertThat(complaintRepo.findByResultId(modelingAssessment.getId()).get().getComplaintType() == ComplaintType.MORE_FEEDBACK).as("more feedback request is not saved")
                .isFalse();
    }

    @Test
    @WithMockUser(username = "student1")
    public void submitComplaintAboutModelingAssessment_validDeadline() throws Exception {
        // Mock object initialized with 2 weeks deadline. One week after result date is fine.
        database.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusWeeks(1));
        database.updateResultCompletionDate(modelingAssessment.getId(), ZonedDateTime.now().minusWeeks(1));

        request.post("/api/complaints", complaint, HttpStatus.CREATED);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is saved").isPresent();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).get();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
    }

    @Test
    @WithMockUser(username = "student1")
    public void submitComplaintAboutModelingAssessment_assessmentTooOld() throws Exception {
        // 3 weeks is already past the deadline
        database.updateAssessmentDueDate(modelingExercise.getId(), ZonedDateTime.now().minusWeeks(3));
        database.updateResultCompletionDate(modelingAssessment.getId(), ZonedDateTime.now().minusWeeks(3));

        request.post("/api/complaints", complaint, HttpStatus.BAD_REQUEST);

        assertThat(complaintRepo.findByResultId(modelingAssessment.getId())).as("complaint is not saved").isNotPresent();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).get();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is false").isFalse();
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void submitComplaintResponse_rejectComplaint() throws Exception {
        complaint = complaintRepo.save(complaint);
        // creating the initial complaintResponse
        ComplaintResponse complaintResponse = database.createInitialEmptyResponse("tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("Rejected");

        request.put("/api/complaint-responses/complaint/" + complaint.getId() + "/resolve", complaintResponse, HttpStatus.OK);

        Complaint storedComplaint = complaintRepo.findByResultId(modelingAssessment.getId()).get();
        assertThat(storedComplaint.isAccepted()).as("complaint is not accepted").isFalse();
        Result storedResult = resultRepo.findWithEagerSubmissionAndFeedbackAndAssessorById(modelingAssessment.getId()).get();
        Result updatedResult = storedResult.getSubmission().getLatestResult();
        database.checkFeedbackCorrectlyStored(modelingAssessment.getFeedbacks(), updatedResult.getFeedbacks(), FeedbackType.MANUAL);
        assertThat(storedResult).as("only feedbacks are changed in the result").isEqualToIgnoringGivenFields(modelingAssessment, "feedbacks");
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void submitComplaintResponse_updateAssessment() throws Exception {
        complaint = complaintRepo.save(complaint);
        // creating the initial complaintResponse
        ComplaintResponse complaintResponse = database.createInitialEmptyResponse("tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(true);
        complaintResponse.setResponseText("Accepted");

        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        feedbacks.forEach((feedback -> feedback.setType(FeedbackType.MANUAL)));
        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(feedbacks).complaintResponse(complaintResponse);
        Result receivedResult = request.putWithResponseBody("/api/modeling-submissions/" + modelingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate,
                Result.class, HttpStatus.OK);

        assertThat(((StudentParticipation) receivedResult.getParticipation()).getStudent()).as("student is hidden in response").isEmpty();
        Complaint storedComplaint = complaintRepo.findByResultId(modelingAssessment.getId()).get();
        assertThat(storedComplaint.isAccepted()).as("complaint is accepted").isTrue();
        Result result = storedComplaint.getResult();
        // set dates to UTC and round to milliseconds for comparison
        result.setCompletionDate(ZonedDateTime.ofInstant(result.getCompletionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        modelingAssessment.setCompletionDate(ZonedDateTime.ofInstant(modelingAssessment.getCompletionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(modelingAssessment.getId()).get();
        Result resultAfterComplaintResponse = resultRepo.findByIdWithEagerFeedbacksAndAssessor(receivedResult.getId()).get();
        database.checkFeedbackCorrectlyStored(feedbacks, resultAfterComplaintResponse.getFeedbacks(), FeedbackType.MANUAL);
        assertThat(storedResult.getAssessor()).as("assessor is still the original one").isEqualTo(modelingAssessment.getAssessor());
    }

    @Test
    @WithMockUser(username = "student1")
    public void getComplaintByResultIdNoComplaintExists() throws Exception {
        request.get("/api/complaints/result/" + complaint.getResult().getId(), HttpStatus.OK, Void.class);
    }

    @Test
    @WithMockUser(username = "student1")
    public void getComplaintByResultId_assessorHiddenForStudent() throws Exception {
        complaintRepo.save(complaint);
        complaintRepo.save(complaint);

        Complaint receivedComplaint = request.get("/api/complaints/results/" + complaint.getResult().getId(), HttpStatus.OK, Complaint.class);

        assertThat(receivedComplaint.getResult().getAssessor()).as("assessor is not set").isNull();
    }

    @Test
    @WithMockUser(username = "student2")
    public void getComplaintByResultId_studentAndNotOwner_forbidden() throws Exception {
        complaint.setParticipant(database.getUserByLogin("student1"));
        complaintRepo.save(complaint);

        request.get("/api/complaints/results/" + complaint.getResult().getId(), HttpStatus.FORBIDDEN, Complaint.class);
    }

    @Test
    @WithMockUser(username = "instructor1")
    public void getComplaintByResultid_instructor_sensitiveDataHidden() throws Exception {
        complaintRepo.save(complaint);

        final var received = request.get("/api/complaints/results/" + complaint.getResult().getId(), HttpStatus.OK, Complaint.class);

        assertThat(received.getResult().getParticipation()).as("Complaint should not contain participation").isNull();
    }

    @Test
    @WithMockUser(username = "tutor1")
    public void getComplaintByResultid_tutor_sensitiveDataHidden() throws Exception {
        complaint.setParticipant(database.getUserByLogin("student1"));
        complaintRepo.save(complaint);

        final var received = request.get("/api/complaints/results/" + complaint.getResult().getId(), HttpStatus.OK, Complaint.class);

        assertThat(received.getResult().getAssessor()).as("Tutors should not see the assessor of a complaint").isNull();
        assertThat(received.getParticipant()).as("Tutors should not see the student of a complaint").isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getComplaintsForTutor_tutor_sensitiveDataHidden() throws Exception {
        complaint.setParticipant(database.getUserByLogin("student1"));
        complaintRepo.save(complaint);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        final var complaints = request.getList("/api/complaints", HttpStatus.OK, Complaint.class, params);

        complaints.forEach(c -> checkComplaintContainsNoSensitiveData(c, true));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getComplaintsByCourseIdTutorIsNotTutorForCourse() throws Exception {
        complaint.setParticipant(database.getUserByLogin("student1"));
        complaintRepo.save(complaint);
        course.setInstructorGroupName("test");
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        request.getList("/api/courses/" + modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/complaints", HttpStatus.FORBIDDEN, Complaint.class, params);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getComplaintsByCourseId_tutor_sensitiveDataHidden() throws Exception {
        complaint.setParticipant(database.getUserByLogin("student1"));
        complaintRepo.save(complaint);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        final var complaints = request.getList("/api/courses/" + modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/complaints", HttpStatus.OK, Complaint.class,
                params);

        complaints.forEach(c -> checkComplaintContainsNoSensitiveData(c, true));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getComplaintsForAssessmentDashboardTutorIsNotTutorForCourse() throws Exception {
        complaint.setParticipant(database.getUserByLogin("student1"));
        complaintRepo.save(complaint);
        course.setInstructorGroupName("test");
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);

        final var params = new LinkedMultiValueMap<String, String>();
        request.getList("/api/exercises/" + modelingExercise.getId() + "/submissions-with-complaints", HttpStatus.FORBIDDEN, Complaint.class, params);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getComplaintsForAssessmentDashboard_sameTutorAsAssessor_studentInfoHidden() throws Exception {
        complaint.setParticipant(database.getUserByLogin("student1"));
        complaintRepo.save(complaint);
        complaint.getResult().setHasComplaint(true);
        resultRepo.save(complaint.getResult());

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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getComplaintsForAssessmentDashboardTestRunTutorIsNotTutorForCourse() throws Exception {
        User instructor = database.getUserByLogin("instructor1");
        complaint.setParticipant(instructor);
        complaint.getResult().setAssessor(instructor);
        resultRepo.save(complaint.getResult());
        complaint = complaintRepo.save(complaint);
        course.setInstructorGroupName("test");
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        request.getList("/api/exercises/" + modelingExercise.getId() + "/complaints-for-test-run-dashboard", HttpStatus.FORBIDDEN, Complaint.class, params);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getComplaintsForAssessmentDashboard_testRun() throws Exception {
        User instructor = database.getUserByLogin("instructor1");
        complaint.setParticipant(instructor);
        complaint.getResult().setAssessor(instructor);
        resultRepo.save(complaint.getResult());
        complaint = complaintRepo.save(complaint);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        final var complaints = request.getList("/api/exercises/" + modelingExercise.getId() + "/complaints-for-test-run-dashboard", HttpStatus.OK, Complaint.class, params);
        assertThat(complaints.size()).isEqualTo(1);
        complaints.forEach(compl -> {
            assertThat(compl.getResult()).isEqualTo(complaint.getResult());
            assertThat(compl.getParticipant()).as("No student information").isNull();
        });
    }

    @Test
    @WithMockUser(username = "student1")
    public void getComplaintResponseByComplaintId_reviewerHiddenForStudent() throws Exception {
        complaint.setParticipant(database.getUserByLogin("student1"));
        complaintRepo.save(complaint);

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected").reviewer(database.getUserByLogin("tutor1"));
        complaintResponseRepo.save(complaintResponse);

        ComplaintResponse receivedComplaintResponse = request.get("/api/complaint-responses/complaint/" + complaint.getId(), HttpStatus.OK, ComplaintResponse.class);

        assertThat(receivedComplaintResponse.getReviewer()).as("reviewer is not set").isNull();
        assertThat(receivedComplaintResponse.getComplaint()).as("complaint is not set").isNull();
    }

    @Test
    @WithMockUser(username = "tutor1")
    public void getComplaintResponseByComplaintId_sensitiveDataHiddenForTutor() throws Exception {
        complaint.setParticipant(database.getUserByLogin("student1"));

        complaint = complaintRepo.save(complaint);
        ComplaintResponse complaintResponse = new ComplaintResponse();
        complaintResponse.setComplaint(complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");
        complaintResponse = complaintResponseRepo.save(complaintResponse);
        complaintResponse.setReviewer(database.getUserByLogin("tutor1"));

        complaintResponseRepo.save(complaintResponse);

        ComplaintResponse receivedComplaintResponse = request.get("/api/complaint-responses/complaint/" + complaint.getId(), HttpStatus.OK, ComplaintResponse.class);

        Complaint receivedComplaint = receivedComplaintResponse.getComplaint();
        assertThat(receivedComplaint.getParticipant()).as("student is not set").isNull();
        assertThat(receivedComplaint.getResult().getParticipation()).as("participation is not set").isNull();
        assertThat(receivedComplaint.getResult().getSubmission()).as("submission is not set").isNull();
    }

    @Test
    @WithMockUser(username = "instructor1")
    public void getComplaintResponseByComplaintId_sensitiveDataHiddenForInstructor() throws Exception {
        complaint.setParticipant(database.getUserByLogin("student1"));
        complaint = complaintRepo.save(complaint);
        ComplaintResponse complaintResponse = new ComplaintResponse();
        complaintResponse.setComplaint(complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");
        complaintResponse = complaintResponseRepo.save(complaintResponse);
        complaintResponse.setReviewer(database.getUserByLogin("instructor1"));

        complaintResponseRepo.save(complaintResponse);

        ComplaintResponse receivedComplaintResponse = request.get("/api/complaint-responses/complaint/" + complaint.getId(), HttpStatus.OK, ComplaintResponse.class);

        Complaint receivedComplaint = receivedComplaintResponse.getComplaint();
        assertThat(receivedComplaint.getParticipant()).as("student is set").isNotNull();
        assertThat(receivedComplaint.getResult().getParticipation()).as("participation is not set").isNull();
        assertThat(receivedComplaint.getResult().getSubmission()).as("submission is not set").isNull();
    }

    @Test
    @WithMockUser(username = "student2")
    public void getComplaintResponseByComplaintId_studentNotOriginalAuthor_forbidden() throws Exception {
        complaint.setParticipant(database.getUserByLogin("student1"));
        complaintRepo.save(complaint);

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected").reviewer(database.getUserByLogin("tutor1"));
        complaintResponseRepo.save(complaintResponse);

        request.get("/api/complaint-responses/complaint/" + complaint.getId(), HttpStatus.FORBIDDEN, ComplaintResponse.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getSubmittedComplaints_byComplaintType() throws Exception {
        database.addComplaints("student1", modelingAssessment.getParticipation(), 1, ComplaintType.COMPLAINT);
        database.addComplaints("student1", modelingAssessment.getParticipation(), 2, ComplaintType.MORE_FEEDBACK);

        String exercisesUrl = "/api/exercises/" + modelingExercise.getId() + "/complaints";
        String coursesUrl = "/api/courses/" + modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/complaints";
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("complaintType", ComplaintType.COMPLAINT.toString());
        List<ComplaintResponse> complaintResponsesByCourse = request.getList(coursesUrl, HttpStatus.OK, ComplaintResponse.class, params);
        List<ComplaintResponse> complaintResponsesByExercise = request.getList(exercisesUrl, HttpStatus.OK, ComplaintResponse.class, params);
        assertThat(complaintResponsesByExercise.size()).isEqualTo(complaintResponsesByCourse.size()).isEqualTo(1);

        params.set("complaintType", ComplaintType.MORE_FEEDBACK.toString());
        complaintResponsesByCourse = request.getList(exercisesUrl, HttpStatus.OK, ComplaintResponse.class, params);
        complaintResponsesByExercise = request.getList(coursesUrl, HttpStatus.OK, ComplaintResponse.class, params);
        assertThat(complaintResponsesByCourse.size()).isEqualTo(complaintResponsesByExercise.size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "student1")
    public void getSubmittedComplaints_asStudent_forbidden() throws Exception {
        complaintRepo.save(complaint);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("complaintType", ComplaintType.COMPLAINT.toString());
        params.add("exerciseId", modelingExercise.getId().toString());

        request.getList("/api/complaints", HttpStatus.FORBIDDEN, ComplaintResponse.class, params);
    }

    private void saveModelingSubmissionAndAssessment() throws Exception {
        modelingSubmission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        modelingSubmission = database.addModelingSubmission(modelingExercise, modelingSubmission, "student1");
        modelingAssessment = database.addModelingAssessmentForSubmission(modelingExercise, modelingSubmission, "test-data/model-assessment/assessment.54727.v2.json", "tutor1",
                true);
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
            if (exercise instanceof ModelingExercise modelingExercise) {
                assertThat(modelingExercise.getSampleSolutionModel()).as("Exercise only contains title and ID").isNull();
                assertThat(modelingExercise.getSampleSolutionExplanation()).as("Exercise only contains title and ID").isNull();
            }
            else if (exercise instanceof TextExercise textExercise) {
                assertThat(textExercise.getSampleSolution()).as("Exercise only contains title and ID").isNull();
                assertThat(textExercise.getExampleSubmissions()).as("Exercise only contains title and ID").isNull();
            }
            else if (exercise instanceof ProgrammingExercise programmingExercise) {
                assertThat(programmingExercise.getProgrammingLanguage()).as("Exercise only contains title and ID").isNull();
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
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getComplaintsByExerciseIdTutorIsNotTutorForCourse() throws Exception {
        complaint.setParticipant(database.getUserByLogin("student1"));
        complaintRepo.save(complaint);
        course.setInstructorGroupName("test");
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        request.getList("/api/exercises/" + complaint.getResult().getParticipation().getExercise().getId() + "/complaints", HttpStatus.FORBIDDEN, Complaint.class, params);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getComplaintsByExerciseId_tutor_sensitiveDataHidden() throws Exception {
        complaint.setParticipant(database.getUserByLogin("student1"));
        complaintRepo.save(complaint);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.COMPLAINT.name());
        final var complaints = request.getList("/api/exercises/" + complaint.getResult().getParticipation().getExercise().getId() + "/complaints", HttpStatus.OK, Complaint.class,
                params);

        complaints.forEach(c -> checkComplaintContainsNoSensitiveData(c, true));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getNumberOfAllowedComplaintsInCourseComplaintsDisabled() throws Exception {
        // complaints enabled will return zero
        course.setMaxComplaintTimeDays(-1);
        courseRepository.save(course);
        request.get("/api/courses/" + modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/allowed-complaints", HttpStatus.BAD_REQUEST, Long.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getNumberOfAllowedComplaintsInCourse() throws Exception {
        complaint.setParticipant(database.getUserByLogin("student1"));
        complaintRepo.save(complaint);
        Long nrOfAllowedComplaints = request.get("/api/courses/" + modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/allowed-complaints", HttpStatus.OK,
                Long.class);
        assertThat(nrOfAllowedComplaints.intValue()).isEqualTo(course.getMaxComplaints());
        // TODO: there should be a second test case where the student already has 2 complaints and the number is reduced
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getNumberOfAllowedComplaintsInCourseTeamMode() throws Exception {
        complaint.setParticipant(database.getUserByLogin("student1"));
        complaintRepo.save(complaint);
        request.get("/api/courses/" + modelingExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/allowed-complaints?teamMode=true", HttpStatus.BAD_REQUEST, Long.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getMoreFeedbackRequestsForAssessmentDashboardTutorIsNotTutorForCourse() throws Exception {
        complaint.setParticipant(database.getUserByLogin("student1"));
        moreFeedbackRequest.setAccepted(true);
        complaintRepo.save(moreFeedbackRequest);
        course.setInstructorGroupName("test");
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.MORE_FEEDBACK.name());
        request.getList("/api/exercises/" + modelingExercise.getId() + "/more-feedback-for-assessment-dashboard", HttpStatus.FORBIDDEN, Complaint.class, params);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getMoreFeedbackRequestsForAssessmentDashboard() throws Exception {
        complaint.setParticipant(database.getUserByLogin("student1"));
        moreFeedbackRequest.setAccepted(true);
        complaintRepo.save(moreFeedbackRequest);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("complaintType", ComplaintType.MORE_FEEDBACK.name());
        final var complaints = request.getList("/api/exercises/" + modelingExercise.getId() + "/more-feedback-for-assessment-dashboard", HttpStatus.OK, Complaint.class, params);

        complaints.forEach(compl -> {
            final var participation = (StudentParticipation) compl.getResult().getParticipation();
            assertThat(participation.getStudent()).as("No student information").isEmpty();
            assertThat(compl.getParticipant()).as("No student information").isNull();
            assertThat(participation.getExercise()).as("No additional exercise information").isNull();
        });
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void submitComplaintForExamExerciseComplaintAlreadyHasId() throws Exception {
        final TextExercise examExercise = database.addCourseExamExerciseGroupWithOneTextExercise();
        final long examId = examExercise.getExerciseGroup().getExam().getId();
        final TextSubmission textSubmission = ModelFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        database.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, "student1", "tutor1");
        var examExerciseComplaint = new Complaint().result(textSubmission.getLatestResult()).complaintText("This is not fair").complaintType(ComplaintType.COMPLAINT);
        examExerciseComplaint.setId(1L);
        final String url = "/api/complaints/exam/{examId}".replace("{examId}", String.valueOf(examId));
        request.post(url, examExerciseComplaint, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void submitComplaintForExamExerciseResultIsNull() throws Exception {
        final TextExercise examExercise = database.addCourseExamExerciseGroupWithOneTextExercise();
        final long examId = examExercise.getExerciseGroup().getExam().getId();
        final TextSubmission textSubmission = ModelFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        database.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, "student1", "tutor1");
        final var examExerciseComplaint = new Complaint().result(null).complaintText("This is not fair").complaintType(ComplaintType.COMPLAINT);
        final String url = "/api/complaints/exam/{examId}".replace("{examId}", String.valueOf(examId));
        request.post(url, examExerciseComplaint, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void submitComplaintForExamExerciseWithinStudentReviewTime() throws Exception {
        final TextExercise examExercise = database.addCourseExamWithReviewDatesExerciseGroupWithOneTextExercise();
        final long examId = examExercise.getExerciseGroup().getExam().getId();
        final TextSubmission textSubmission = ModelFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        database.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, "student1", "tutor1");
        final var examExerciseComplaint = new Complaint().result(textSubmission.getLatestResult()).complaintText("This is not fair").complaintType(ComplaintType.COMPLAINT);

        final String url = "/api/complaints/exam/{examId}".replace("{examId}", String.valueOf(examId));
        request.post(url, examExerciseComplaint, HttpStatus.CREATED);

        Optional<Complaint> storedComplaint = complaintRepo.findByResultId(textSubmission.getLatestResult().getId());
        assertThat(storedComplaint).as("complaint is saved").isPresent();
        assertThat(storedComplaint.get().getComplaintText()).as("complaint text got correctly saved").isEqualTo(examExerciseComplaint.getComplaintText());
        assertThat(storedComplaint.get().isAccepted()).as("accepted flag of complaint is not set").isNull();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(textSubmission.getLatestResult().getId()).get();
        assertThat(storedResult.hasComplaint()).as("hasComplaint flag of result is true").isTrue();
        // set date to UTC for comparison as the date saved in resultBeforeComplaint string is in UTC
        storedResult.setCompletionDate(ZonedDateTime.ofInstant(storedResult.getCompletionDate().toInstant(), ZoneId.of("UTC")));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void submitComplaintForExamExerciseOutsideOfStudentReviewTime_badRequest() throws Exception {
        final TextExercise examExercise = database.addCourseExamExerciseGroupWithOneTextExercise();
        final long examId = examExercise.getExerciseGroup().getExam().getId();
        final TextSubmission textSubmission = ModelFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        database.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, "student1", "tutor1");
        final var examExerciseComplaint = new Complaint().result(null).complaintText("This is not fair").complaintType(ComplaintType.COMPLAINT);
        final String url = "/api/complaints/exam/{examId}".replace("{examId}", String.valueOf(examId));
        request.post(url, examExerciseComplaint, HttpStatus.BAD_REQUEST);

    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetComplaintsByCourseIdAndExamIdTutorIsNotTutorForCourse() throws Exception {
        final TextExercise examExercise = database.addCourseExamWithReviewDatesExerciseGroupWithOneTextExercise();
        final long examId = examExercise.getExerciseGroup().getExam().getId();
        final long courseId = examExercise.getExerciseGroup().getExam().getCourse().getId();
        Course course = examExercise.getExerciseGroup().getExam().getCourse();
        course.setInstructorGroupName("test");
        course.setTeachingAssistantGroupName("test");
        courseRepository.save(course);

        request.getList("/api/courses/" + courseId + "/exams/" + examId + "/complaints", HttpStatus.FORBIDDEN, Complaint.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetComplaintsByCourseIdAndExamId() throws Exception {
        final TextExercise examExercise = database.addCourseExamWithReviewDatesExerciseGroupWithOneTextExercise();
        final long examId = examExercise.getExerciseGroup().getExam().getId();
        final long courseId = examExercise.getExerciseGroup().getExam().getCourse().getId();
        final TextSubmission textSubmission = ModelFactory.generateTextSubmission("This is my submission", Language.ENGLISH, true);
        database.saveTextSubmissionWithResultAndAssessor(examExercise, textSubmission, "student1", "tutor1");
        final var examExerciseComplaint = new Complaint().result(textSubmission.getLatestResult()).complaintText("This is not fair").complaintType(ComplaintType.COMPLAINT);
        final String url = "/api/complaints/exam/{examId}".replace("{examId}", String.valueOf(examId));
        request.post(url, examExerciseComplaint, HttpStatus.CREATED);

        Optional<Complaint> storedComplaint = complaintRepo.findByResultId(textSubmission.getLatestResult().getId());
        request.get("/api/courses/" + courseId + "/exams/" + examId + "/complaints", HttpStatus.FORBIDDEN, List.class);
        database.changeUser("tutor1");
        request.get("/api/courses/" + courseId + "/exams/" + examId + "/complaints", HttpStatus.FORBIDDEN, List.class);
        database.changeUser("instructor1");
        var fetchedComplaints = request.getList("/api/courses/" + courseId + "/exams/" + examId + "/complaints", HttpStatus.OK, Complaint.class);
        assertThat(fetchedComplaints.get(0).getId()).isEqualTo(storedComplaint.get().getId().intValue());
        assertThat(fetchedComplaints.get(0).getComplaintText()).isEqualTo(storedComplaint.get().getComplaintText());
    }
}
