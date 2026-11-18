package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.TeamTextSubmissionUpdateDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.exercise.web.ParticipationTeamWebsocketService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.dto.TextSubmissionRequestDTO;
import de.tum.cit.aet.artemis.text.test_repository.TextSubmissionTestRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * A submission id in a save request is only applied to a submission of the participation the save resolves for the caller,
 * exam exercises included. The REST save refuses an id that does not belong to the caller; the team websocket save handles
 * only team exercises and does not save through an individual or exam participation. The exam matrix here pins that scope
 * for a running and an ended exam, reached from a course participation and from the caller's own exam participation, for
 * both entry points. Each case reads the stored row back after the request.
 */
class ExamSubmissionUpdateScopeTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "examsubmissionscope";

    private static final String STORED_TEXT = "STORED";

    private static final String UPDATE_TEXT = "UPDATE";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ParticipationTeamWebsocketService participationTeamWebsocketService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private TextSubmissionTestRepository textSubmissionRepository;

    private TextExercise courseExercise;

    private StudentParticipation courseParticipation;

    private TextExercise examExercise;

    private StudentParticipation examParticipation;

    private TextSubmission storedExamSubmission;

    private long storedParticipationId;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);
        Course course = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Course", TEST_PREFIX);
        courseExercise = ExerciseUtilService.findTextExerciseWithTitle(course.getExercises(), "Course");
        courseParticipation = participationUtilService.createAndSaveParticipationForExercise(courseExercise, TEST_PREFIX + "student1");
    }

    /**
     * Builds a released exam with a text exercise, a stored submission of student2 in it, and student1's own student exam
     * and participation in the same exam exercise, so both destinations can be exercised.
     *
     * @param examOver whether the exam's working time should already have ended
     */
    private void setUpExam(boolean examOver) {
        ExerciseGroup exerciseGroup = examUtilService.addEnrolledExerciseGroupWithExamAndCourse(true, true, TEST_PREFIX);
        Exam exam = exerciseGroup.getExam();
        examExercise = exerciseRepository.save(TextExerciseFactory.generateTextExerciseForExam(exerciseGroup));

        examUtilService.addExerciseToStudentExam(examUtilService.addStudentExamWithUser(exam, TEST_PREFIX + "student2"), examExercise);
        examUtilService.addExerciseToStudentExam(examUtilService.addStudentExamWithUser(exam, TEST_PREFIX + "student1"), examExercise);
        examParticipation = participationUtilService.createAndSaveParticipationForExercise(examExercise, TEST_PREFIX + "student1");

        TextSubmission stored = ParticipationFactory.generateTextSubmission(STORED_TEXT, Language.ENGLISH, true);
        stored.setSubmissionDate(ZonedDateTime.now().minusDays(1));
        storedExamSubmission = textExerciseUtilService.saveTextSubmission(examExercise, stored, TEST_PREFIX + "student2");
        storedParticipationId = storedExamSubmission.getParticipation().getId();

        if (examOver) {
            // the working time of both student exams is the exam duration, so moving the exam into the past ends it
            ZonedDateTime now = ZonedDateTime.now();
            examUtilService.setVisibleStartAndEndDateOfExam(exam, now.minusHours(3), now.minusHours(2), now.minusHours(1));
            examRepository.save(exam);
        }
    }

    private void sendViaWebsocket(long destinationParticipationId) {
        var payload = new TeamTextSubmissionUpdateDTO(storedExamSubmission.getId(), UPDATE_TEXT, Language.ENGLISH, true);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(TEST_PREFIX + "student1");
        try {
            participationTeamWebsocketService.updateTextSubmission(destinationParticipationId, payload, principal);
        }
        catch (AccessForbiddenException ignored) {
            // a refused save is a valid outcome, the row is asserted by the caller
        }
    }

    private void sendViaRest(long destinationExerciseId, HttpStatus expectedStatus) throws Exception {
        var payload = new TextSubmissionRequestDTO(storedExamSubmission.getId(), UPDATE_TEXT, Language.ENGLISH, true);
        request.put("/api/text/exercises/" + destinationExerciseId + "/text-submissions", payload, expectedStatus);
    }

    private void sendViaRestIgnoringStatus(long destinationExerciseId) throws Exception {
        var payload = new TextSubmissionRequestDTO(storedExamSubmission.getId(), UPDATE_TEXT, Language.ENGLISH, true);
        request.performMvcRequest(MockMvcRequestBuilders.put("/api/text/exercises/" + destinationExerciseId + "/text-submissions").contentType(MediaType.APPLICATION_JSON)
                .content(request.getObjectMapper().writeValueAsString(payload)));
    }

    private void assertStoredRowUnchanged() {
        TextSubmission reloaded = textSubmissionRepository.findById(storedExamSubmission.getId()).orElseThrow();
        assertThat(reloaded.getText()).isEqualTo(STORED_TEXT);
        assertThat(reloaded.getParticipation().getId()).isEqualTo(storedParticipationId);
        assertThat(reloaded.isSubmitted()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void teamWebsocketDoesNotSaveExamSubmissionFromCourseParticipation() {
        setUpExam(false);
        sendViaWebsocket(courseParticipation.getId());
        assertStoredRowUnchanged();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void restSaveRefusesExamSubmissionIdFromCourseExercise() throws Exception {
        setUpExam(false);
        // A course exercise has no exam gate to resolve the caller's participation, so the foreign id is refused outright.
        sendViaRest(courseExercise.getId(), HttpStatus.FORBIDDEN);
        assertStoredRowUnchanged();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void teamWebsocketDoesNotSaveExamSubmissionFromExamParticipation() {
        setUpExam(false);
        sendViaWebsocket(examParticipation.getId());
        assertStoredRowUnchanged();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void examSaveDoesNotWriteAnotherParticipationsRowViaExamExercise() throws Exception {
        setUpExam(false);
        // The running exam resolves the caller's own participation, so a submission id belonging to another participation
        // is never applied to that other row, whatever status the save returns.
        sendViaRestIgnoringStatus(examExercise.getId());
        assertStoredRowUnchanged();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void teamWebsocketDoesNotSaveEndedExamSubmissionFromCourseParticipation() {
        setUpExam(true);
        sendViaWebsocket(courseParticipation.getId());
        assertStoredRowUnchanged();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void teamWebsocketDoesNotSaveEndedExamSubmissionFromExamParticipation() {
        setUpExam(true);
        sendViaWebsocket(examParticipation.getId());
        assertStoredRowUnchanged();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void restSaveRefusesEndedExamSubmissionId() throws Exception {
        setUpExam(true);
        // After the exam has ended the exam gate refuses the save before the id is even considered.
        sendViaRest(examExercise.getId(), HttpStatus.FORBIDDEN);
        assertStoredRowUnchanged();
    }
}
