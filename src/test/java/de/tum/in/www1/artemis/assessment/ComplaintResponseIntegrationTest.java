package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.text.TextExerciseFactory;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ComplaintResponseRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.dto.ComplaintAction;
import de.tum.in.www1.artemis.service.dto.ComplaintResponseUpdateDTO;

class ComplaintResponseIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final Logger log = LoggerFactory.getLogger(ComplaintResponseIntegrationTest.class);

    private static final String TEST_PREFIX = "complaintresponseintegration";

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ComplaintResponseRepository complaintResponseRepository;

    private Complaint complaint;

    @BeforeEach
    void initTestCase() throws Exception {
        // creating the users student1, tutor1-tutor3 and instructor1
        userUtilService.addUsers(TEST_PREFIX, 1, 3, 0, 1);

        // creating course
        // students: student1 | tutors: tutor1-tutor3 | instructors: instructor1
        Course course = courseUtilService.createCourse();
        // creating text exercise
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(null, null, null, course);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        textExercise = exerciseRepository.saveAndFlush(textExercise);

        // creating participation of student1 by starting the exercise
        User student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow();
        StudentParticipation studentParticipation = participationService.startExercise(textExercise, student1, false);
        // creating submission of student1
        TextSubmission submission = new TextSubmission();
        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(studentParticipation);
        submission.setSubmitted(Boolean.TRUE);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission.text("hello world");
        submission = submissionRepository.saveAndFlush(submission);
        // creating assessment by tutor1
        User tutor1 = userRepository.findOneByLogin(TEST_PREFIX + "tutor1").orElseThrow();
        Result result = ParticipationFactory.generateResult(true, 50D);
        result.setAssessor(tutor1);
        result.setHasComplaint(true);
        result.setParticipation(studentParticipation);
        result = resultRepository.saveAndFlush(result);

        submission.addResult(result);
        result.setSubmission(submission);
        submissionRepository.save(submission);

        log.debug("3 Test setup submissions done");

        // creating complaint by student 1
        complaint = new Complaint();
        complaint.setComplaintType(ComplaintType.COMPLAINT);
        complaint.setComplaintText("Unfair");
        complaint.setResult(result);
        complaint.setAccepted(null);
        complaint.setSubmittedTime(null);
        complaint.setParticipant(student1);

        complaint = complaintRepository.save(complaint);

        log.debug("4 Test setup complaints done");
    }

    @AfterEach
    void tearDown() {
        Constants.COMPLAINT_LOCK_DURATION_IN_MINUTES = 5;
    }

    // === TESTING SECURITY ===
    private void testAllPreAuthorize() throws Exception {
        request.postWithoutLocation("/api/complaints/" + complaint.getId() + "/response", null, HttpStatus.FORBIDDEN, null);
        request.delete("/api/complaints/" + complaint.getId() + "/response", HttpStatus.FORBIDDEN);
        ComplaintResponseUpdateDTO complaintResponseUpdateResolve = new ComplaintResponseUpdateDTO(null, null, ComplaintAction.RESOLVE_COMPLAINT);
        ComplaintResponseUpdateDTO complaintResponseUpdateRefresh = new ComplaintResponseUpdateDTO(null, null, ComplaintAction.REFRESH_LOCK);

        request.patch("/api/complaints/" + complaint.getId() + "/response", complaintResponseUpdateResolve, HttpStatus.FORBIDDEN);
        request.patch("/api/complaints/" + complaint.getId() + "/response", complaintResponseUpdateRefresh, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    // === TESTING CREATE LOCK ===

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void createLock_noFailureCondition_shouldCreateEmptyComplaintResponse() throws Exception {
        request.postWithoutLocation("/api/complaints/" + complaint.getId() + "/response", null, HttpStatus.CREATED, null);
        Optional<ComplaintResponse> optionalComplaintResponse = complaintResponseRepository.findByComplaint_Id(complaint.getId());
        assertThat(optionalComplaintResponse).isPresent();
        ComplaintResponse complaintResponse = optionalComplaintResponse.get();
        assertThat(complaintResponse.getComplaint().isAccepted()).isNull(); // not handled yet
        assertThat(complaintResponse.getReviewer().getLogin()).isEqualTo(TEST_PREFIX + "tutor2"); // lock creator
        assertThat(complaintResponse.getCreatedDate()).isNotNull();// set by entity listener
        assertThat(complaintResponse.isCurrentlyLocked()).isTrue(); // should be locked now
        assertThat(complaintResponse.lockEndDate()).isNotNull(); // lock end date should be available
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createLock_sameAsAssessor_shouldThrowAccessForbiddenException() throws Exception {
        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO(null, null, ComplaintAction.RESOLVE_COMPLAINT);
        request.patch("/api/complaints/" + complaint.getId() + "/response", complaintResponseUpdate, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void createLock_complaintDoesNotExistInDb_shouldThrowIllegalArgumentException() throws Exception {
        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO(null, null, ComplaintAction.RESOLVE_COMPLAINT);
        request.patch("/api/complaints/" + 0 + "/response", complaintResponseUpdate, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void createLock_alreadyResolved_shouldThrowIllegalArgumentException() throws Exception {
        complaint.setAccepted(true);
        complaint = complaintRepository.saveAndFlush(complaint);
        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO(null, null, ComplaintAction.RESOLVE_COMPLAINT);
        request.patch("/api/complaints/" + complaint.getId() + "/response", complaintResponseUpdate, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void createLock_alreadyConnectedToComplaintResponse_shouldThrowIllegalArgumentException() throws Exception {
        ComplaintResponse complaintResponse = new ComplaintResponse();
        complaintResponse.setComplaint(complaint);
        complaintResponseRepository.saveAndFlush(complaintResponse);
        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO(null, null, ComplaintAction.RESOLVE_COMPLAINT);
        request.patch("/api/complaints/" + complaint.getId() + "/response", complaintResponseUpdate, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // === TESTING REFRESH LOCK ===

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor3", roles = "TA")
    void refreshLock_noFailureCondition_shouldRefreshLock() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", true);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isFalse();

        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO(null, null, ComplaintAction.REFRESH_LOCK);
        request.patch("/api/complaints/" + complaint.getId() + "/response", complaintResponseUpdate, HttpStatus.OK);
        Constants.COMPLAINT_LOCK_DURATION_IN_MINUTES = 5;
        assertThatLockWasReplaced(initialLockComplaintResponse, TEST_PREFIX + "tutor3");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void refreshLock_LockActiveBUTInstructor_shouldRefreshLock() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();
        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO(null, null, ComplaintAction.REFRESH_LOCK);
        request.patch("/api/complaints/" + complaint.getId() + "/response", complaintResponseUpdate, HttpStatus.OK);
        assertThatLockWasReplaced(initialLockComplaintResponse, TEST_PREFIX + "instructor1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void refreshLock_LockActiveBUTInitialCreatorOfLock_shouldRefreshLock() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();
        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO(null, null, ComplaintAction.REFRESH_LOCK);
        request.patch("/api/complaints/" + complaint.getId() + "/response", complaintResponseUpdate, HttpStatus.OK);
        assertThatLockWasReplaced(initialLockComplaintResponse, TEST_PREFIX + "tutor2");
    }

    private void assertThatLockWasReplaced(ComplaintResponse originalLock, String loginOfNewLockCreator) {
        Optional<ComplaintResponse> optionalComplaintResponse = complaintResponseRepository.findByComplaint_Id(complaint.getId());
        assertThat(optionalComplaintResponse).isPresent();
        ComplaintResponse complaintResponse = optionalComplaintResponse.get();
        assertThat(complaintResponse.getComplaint().isAccepted()).isNull(); // not handled yet
        assertThat(complaintResponse.getReviewer().getLogin()).isEqualTo(loginOfNewLockCreator); // lock creator
        assertThat(complaintResponse.getCreatedDate()).isNotNull();// set by entity listener
        assertThat(complaintResponse.isCurrentlyLocked()).isTrue(); // should be locked now
        assertThat(complaintResponse.lockEndDate()).isNotNull(); // lock end date should be available
        assertThat(originalLock.getId()).isNotEqualTo(complaintResponse.getId()); // lock should have been replaced
        assertThat(complaintResponseRepository.existsById(originalLock.getId())).isFalse(); // lock should have been replaced
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor3", roles = "TA")
    void refreshLock_LockStillActive_shouldThrowComplaintResponseLockedException() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();

        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO(null, null, ComplaintAction.REFRESH_LOCK);
        request.patch("/api/complaints/" + complaint.getId() + "/response", complaintResponseUpdate, HttpStatus.BAD_REQUEST);
        // initial lock should still exist
        assertThat(complaintResponseRepository.existsById(initialLockComplaintResponse.getId())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor3", roles = "TA")
    void refreshLock_noinitialLockComplaintResponseExists_shouldThrowIllegalArgumentException() throws Exception {
        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO(null, null, ComplaintAction.REFRESH_LOCK);
        request.patch("/api/complaints/" + complaint.getId() + "/response", complaintResponseUpdate, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor3", roles = "TA")
    void refreshLock_complaintDoesNotExist_shouldThrowIllegalArgumentException() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", true);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isFalse();
        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO(null, null, ComplaintAction.REFRESH_LOCK);
        request.patch("/api/complaints/" + 0 + "/response", complaintResponseUpdate, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(complaintResponseRepository.existsById(initialLockComplaintResponse.getId())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor3", roles = "TA")
    void refreshLock_complaintAlreadyResolved_shouldThrowIllegalArgumentException() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", true);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isFalse();
        complaint.setAccepted(true);
        complaint = complaintRepository.saveAndFlush(complaint);
        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO(null, null, ComplaintAction.REFRESH_LOCK);
        request.patch("/api/complaints/" + complaint.getId() + "/response", complaintResponseUpdate, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(complaintResponseRepository.existsById(initialLockComplaintResponse.getId())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void refreshLock_sameAsAssessor_shouldThrowAccessForbiddenException() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", true);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isFalse();
        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO(null, null, ComplaintAction.REFRESH_LOCK);
        request.patch("/api/complaints/" + complaint.getId() + "/response", complaintResponseUpdate, HttpStatus.FORBIDDEN);
        assertThat(complaintResponseRepository.existsById(initialLockComplaintResponse.getId())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor3", roles = "TA")
    void refreshLock_complaintResponseAlreadySubmitted_shouldThrowIllegalArgumentException() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", true);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isFalse();
        initialLockComplaintResponse.setSubmittedTime(ZonedDateTime.now());
        complaintResponseRepository.saveAndFlush(initialLockComplaintResponse);
        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO(null, null, ComplaintAction.REFRESH_LOCK);
        request.patch("/api/complaints/" + complaint.getId() + "/response", complaintResponseUpdate, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(complaintResponseRepository.existsById(initialLockComplaintResponse.getId())).isTrue();
    }

    // === TESTING REMOVE LOCK ===

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void removeLock_creatorAndLockActive_shouldRemoveLock() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();
        request.delete("/api/complaints/" + complaint.getId() + "/response", HttpStatus.OK);
        assertThat(complaintResponseRepository.existsById(initialLockComplaintResponse.getId())).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void removeLock_creatorAndLockInactive_shouldRemoveLock() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", true);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isFalse();
        request.delete("/api/complaints/" + complaint.getId() + "/response", HttpStatus.OK);
        assertThat(complaintResponseRepository.existsById(initialLockComplaintResponse.getId())).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor3", roles = "TA")
    void removeLock_notCreatorAndLockActive_shouldThrowComplaintResponseLockedException() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();
        request.delete("/api/complaints/" + complaint.getId() + "/response", HttpStatus.BAD_REQUEST);
        assertThat(complaintResponseRepository.existsById(initialLockComplaintResponse.getId())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor3", roles = "TA")
    void removeLock_notCreatorAndLockNotActive_shouldRemoveLock() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", true);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isFalse();
        request.delete("/api/complaints/" + complaint.getId() + "/response", HttpStatus.OK);
        assertThat(complaintResponseRepository.existsById(initialLockComplaintResponse.getId())).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void removeLock_ComplaintNotFoundInDatabase_shouldThrowIllegalArgumentException() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();
        request.delete("/api/complaints/" + 0 + "/response", HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(complaintResponseRepository.existsById(initialLockComplaintResponse.getId())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void removeLock_complaintIsAlreadyHandled_shouldThrowIllegalArgumentException() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();
        complaint.setAccepted(true);
        complaintRepository.saveAndFlush(complaint);
        request.delete("/api/complaints/" + complaint.getId() + "/response", HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(complaintResponseRepository.existsById(initialLockComplaintResponse.getId())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void removeLock_noComplaintResponseExists_shouldThrowIllegalArgumentException() throws Exception {
        request.delete("/api/complaints/" + complaint.getId() + "/response", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void removeLock_complaintResponseIsAlreadySubmitted_shouldThrowIllegalArgumentException() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();
        initialLockComplaintResponse.setSubmittedTime(ZonedDateTime.now());
        complaintResponseRepository.saveAndFlush(initialLockComplaintResponse);
        request.delete("/api/complaints/" + complaint.getId() + "/response", HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(complaintResponseRepository.existsById(initialLockComplaintResponse.getId())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void removeLock_IsAssessor_shouldThrowAccessForbiddenException() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor1", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();
        request.delete("/api/complaints/" + complaint.getId() + "/response", HttpStatus.FORBIDDEN);
        assertThat(complaintResponseRepository.existsById(initialLockComplaintResponse.getId())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void removeLock_asInstructor_shouldRemoveLock() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();
        request.delete("/api/complaints/" + complaint.getId() + "/response", HttpStatus.OK);
        assertThat(complaintResponseRepository.existsById(initialLockComplaintResponse.getId())).isFalse();
    }

    // === TESTING RESOLVE COMPLAINT ===

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void resolveComplaint_noFailureCondition_shouldResolveComplaint() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();

        ComplaintResponseUpdateDTO initialLockComplaintResponseUpdated = new ComplaintResponseUpdateDTO("Accepted", true, ComplaintAction.RESOLVE_COMPLAINT);

        request.patch("/api/complaints/" + complaint.getId() + "/response", initialLockComplaintResponseUpdated, HttpStatus.OK);
        validateThatComplaintIsResolved(TEST_PREFIX + "tutor2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void resolveMoreFeedback_assessor_shouldResolve() throws Exception {
        complaint.setComplaintType(ComplaintType.MORE_FEEDBACK);
        complaintRepository.saveAndFlush(complaint);
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor1", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();

        ComplaintResponseUpdateDTO initialLockComplaintResponseUpdated = new ComplaintResponseUpdateDTO("Accepted", true, ComplaintAction.RESOLVE_COMPLAINT);

        request.patch("/api/complaints/" + complaint.getId() + "/response", initialLockComplaintResponseUpdated, HttpStatus.OK);
        validateThatComplaintIsResolved(TEST_PREFIX + "tutor1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void resolveComplaint_emptyComplaintResponseNotFoundInDatabase_shouldThrowIllegalArgumentException() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();

        initialLockComplaintResponse.setResponseText("Accepted");
        initialLockComplaintResponse.getComplaint().setAccepted(true);
        initialLockComplaintResponse.setId(0L);
        long complaintId = 0L;

        ComplaintResponseUpdateDTO complaintResponseUpdate = new ComplaintResponseUpdateDTO("Accepted", true, ComplaintAction.RESOLVE_COMPLAINT);
        request.patch("/api/complaints/" + complaintId + "/response", complaintResponseUpdate, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void resolveComplaint_complaintResponseInDatabaseNotEmpty_shouldThrowIllegalArgumentException() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();
        initialLockComplaintResponse.setSubmittedTime(ZonedDateTime.now());
        initialLockComplaintResponse.setResponseText("NotEmpty");
        complaintResponseRepository.saveAndFlush(initialLockComplaintResponse);

        ComplaintResponseUpdateDTO initialLockComplaintResponseUpdated = new ComplaintResponseUpdateDTO("Accepted", true, ComplaintAction.RESOLVE_COMPLAINT);

        request.patch("/api/complaints/" + complaint.getId() + "/response", initialLockComplaintResponseUpdated, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void resolveComplaint_complaintAlreadyAnswered_shouldThrowIllegalArgumentException() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();
        complaint.setAccepted(true);
        complaintRepository.saveAndFlush(complaint);

        ComplaintResponseUpdateDTO initialLockComplaintResponseUpdated = new ComplaintResponseUpdateDTO("Accepted", true, ComplaintAction.RESOLVE_COMPLAINT);

        request.patch("/api/complaints/" + complaint.getId() + "/response", initialLockComplaintResponseUpdated, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void resolveComplaint_noDecisionMade_shouldThrowIllegalArgumentException() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();

        ComplaintResponseUpdateDTO initialLockComplaintResponseUpdated = new ComplaintResponseUpdateDTO("Accepted", null, ComplaintAction.RESOLVE_COMPLAINT);

        request.patch("/api/complaints/" + complaint.getId() + "/response", initialLockComplaintResponseUpdated, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void resolveComplaint_assessor_shouldThrowAccessForbiddenException() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", true);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isFalse();

        ComplaintResponseUpdateDTO initialLockComplaintResponseUpdated = new ComplaintResponseUpdateDTO("Accepted", true, ComplaintAction.RESOLVE_COMPLAINT);

        request.patch("/api/complaints/" + complaint.getId() + "/response", initialLockComplaintResponseUpdated, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void resolveMoreFeedBackRequest_NOTassessor_shouldThrowAccessForbiddenException() throws Exception {
        complaint.setComplaintType(ComplaintType.MORE_FEEDBACK);
        complaintRepository.saveAndFlush(complaint);
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor1", true);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isFalse();

        ComplaintResponseUpdateDTO initialLockComplaintResponseUpdated = new ComplaintResponseUpdateDTO("Accepted", true, ComplaintAction.RESOLVE_COMPLAINT);

        request.patch("/api/complaints/" + complaint.getId() + "/response", initialLockComplaintResponseUpdated, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor3", roles = "TA")
    void resolveComplaint_LockActive_shouldThrowComplaintResponseLockedException() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();

        ComplaintResponseUpdateDTO initialLockComplaintResponseUpdated = new ComplaintResponseUpdateDTO("Accepted", true, ComplaintAction.RESOLVE_COMPLAINT);

        request.patch("/api/complaints/" + complaint.getId() + "/response", initialLockComplaintResponseUpdated, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void resolveComplaint_LockActiveBUTInstructor_shouldResolve() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();

        ComplaintResponseUpdateDTO initialLockComplaintResponseUpdated = new ComplaintResponseUpdateDTO("Accepted", true, ComplaintAction.RESOLVE_COMPLAINT);

        request.patch("/api/complaints/" + complaint.getId() + "/response", initialLockComplaintResponseUpdated, HttpStatus.OK);
        validateThatComplaintIsResolved(TEST_PREFIX + "instructor1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void resolveComplaint_LockActiveBUTCreatorOfLock_shouldResolve() throws Exception {
        ComplaintResponse initialLockComplaintResponse = createLockOnComplaint(TEST_PREFIX + "tutor2", false);
        assertThat(initialLockComplaintResponse.isCurrentlyLocked()).isTrue();

        ComplaintResponseUpdateDTO initialLockComplaintResponseUpdated = new ComplaintResponseUpdateDTO("Accepted", true, ComplaintAction.RESOLVE_COMPLAINT);

        request.patch("/api/complaints/" + complaint.getId() + "/response", initialLockComplaintResponseUpdated, HttpStatus.OK);
        validateThatComplaintIsResolved(TEST_PREFIX + "tutor2");
    }

    private void validateThatComplaintIsResolved(String loginOfResolver) {
        Optional<ComplaintResponse> optionalComplaintResponse = complaintResponseRepository.findByComplaint_Id(complaint.getId());
        assertThat(optionalComplaintResponse).isPresent();
        ComplaintResponse complaintResponse = optionalComplaintResponse.get();
        assertThat(complaintResponse.getComplaint().isAccepted()).isTrue();
        assertThat(complaintResponse.getSubmittedTime()).isNotNull();
        assertThat(complaintResponse.getResponseText()).isEqualTo("Accepted");
        assertThat(complaintResponse.getReviewer().getLogin()).isEqualTo(loginOfResolver);
    }

    private ComplaintResponse createLockOnComplaint(String lockOwnerLogin, boolean runOut) {
        ComplaintResponse complaintResponse = new ComplaintResponse();
        complaintResponse.setComplaint(complaint);
        User tutor = userRepository.findOneByLogin(lockOwnerLogin).orElseThrow();
        complaintResponse.setReviewer(tutor);
        complaintResponse = complaintResponseRepository.saveAndFlush(complaintResponse);

        // manually modifying the created date set by entity listener
        if (runOut) {
            Constants.COMPLAINT_LOCK_DURATION_IN_MINUTES = -5;
        }
        return complaintResponse;
    }

}
