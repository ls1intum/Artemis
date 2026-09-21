package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackDTO;
import de.tum.cit.aet.artemis.assessment.util.ComplaintUtilService;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.dto.ModelingAssessmentDTO;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextBlockType;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.dto.ComplaintResponseRequestDTO;
import de.tum.cit.aet.artemis.text.dto.TextAssessmentDTO;
import de.tum.cit.aet.artemis.text.dto.TextAssessmentUpdateDTO;
import de.tum.cit.aet.artemis.text.dto.TextBlockDTO;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * The assessment endpoints take feedback ids and text block ids from the request body, and both name stored rows. These tests send ids of a result and a submission the assessed
 * result does not own, and check that the request is refused and the stored rows stay where they are. Every row is read back through a fresh persistence context.
 */
class AssessmentIdValidationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "assessmentidvalid";

    private static final String OTHER_LONG_TEXT = "long feedback text " + "L".repeat(1500);

    private static final String OTHER_SUBMISSION_TEXT = "another submission";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ComplaintUtilService complaintUtilService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 3, 3, 2, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void textAssessmentRejectsFeedbackIdOfAnotherResult() throws Exception {
        Fixture fixture = setUpTextFixture("FeedbackX", "FeedbackY");

        TextAssessmentDTO body = new TextAssessmentDTO(
                List.of(new FeedbackDTO(fixture.otherFeedbackId(), "text", "D".repeat(1500), true, null, 1.0, true, FeedbackType.MANUAL_UNREFERENCED, null, null, null)), null,
                null);
        request.putAndExpectError(fixture.url(), body, HttpStatus.BAD_REQUEST, "feedbackIdMismatch");

        assertThat(scalar("SELECT f.result.id FROM Feedback f WHERE f.id = :id", fixture.otherFeedbackId())).as("the feedback stays on the result that owns it")
                .isEqualTo(fixture.otherResultId());
        assertThat(scalar("SELECT l.text FROM LongFeedbackText l WHERE l.feedback.id = :id", fixture.otherFeedbackId())).as("its long feedback text is unchanged")
                .isEqualTo(OTHER_LONG_TEXT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void textAssessmentRejectsTextBlockOfAnotherSubmission() throws Exception {
        Fixture fixture = setUpTextFixture("BlockX", "BlockY");
        TextBlock otherBlock = TextExerciseFactory.generateTextBlock(0, OTHER_SUBMISSION_TEXT.length(), OTHER_SUBMISSION_TEXT);
        textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(Set.of(otherBlock), fixture.otherSubmission());
        String otherBlockId = otherBlock.getId();
        Object submissionBefore = scalar("SELECT b.submission.id FROM TextBlock b WHERE b.id = :id", otherBlockId);

        TextAssessmentDTO body = new TextAssessmentDTO(List.of(new FeedbackDTO(null, "text", "detail", false, null, 1.0, true, FeedbackType.MANUAL_UNREFERENCED, null, null, null)),
                Set.of(new TextBlockDTO(otherBlockId, "changed block text", 0, 5, TextBlockType.MANUAL)), null);
        request.putAndExpectError(fixture.url(), body, HttpStatus.BAD_REQUEST, "textBlockSubmissionMismatch");

        assertThat(scalar("SELECT b.submission.id FROM TextBlock b WHERE b.id = :id", otherBlockId)).as("the text block stays on the submission that owns it")
                .isEqualTo(submissionBefore);
        assertThat(scalar("SELECT b.text FROM TextBlock b WHERE b.id = :id", otherBlockId)).as("its text is unchanged").isEqualTo(OTHER_SUBMISSION_TEXT);
        assertThat(scalar("SELECT COUNT(f) FROM Feedback f WHERE f.result.id = :id", fixture.resultId())).as("the refused request writes no assessment").isEqualTo(0L);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor3", roles = "TA")
    void textAssessmentAfterComplaintRejectsFeedbackIdOfAnotherResult() throws Exception {
        Fixture fixture = setUpTextFixture("ComplaintX", "ComplaintY");
        Result assessedResult = resultRepository.findByIdElseThrow(fixture.resultId());
        ComplaintResponse complaintResponse = complaintUtilService.createComplaintAndResponse(assessedResult, TEST_PREFIX + "tutor3").complaintResponse();
        Complaint complaint = complaintResponse.getComplaint();
        // the endpoint resolves the complaint before it updates the assessment, and the two steps commit separately, so a
        // request refused for a foreign feedback id must not have resolved the complaint either
        Object acceptedBefore = scalar("SELECT c.accepted FROM Complaint c WHERE c.id = :id", complaint.getId());
        Object submittedTimeBefore = scalar("SELECT cr.submittedTime FROM ComplaintResponse cr WHERE cr.id = :id", complaintResponse.getId());

        TextAssessmentUpdateDTO body = new TextAssessmentUpdateDTO(
                List.of(new FeedbackDTO(fixture.otherFeedbackId(), "text", null, true, null, 1.0, true, FeedbackType.MANUAL_UNREFERENCED, null, null, null)),
                new ComplaintResponseRequestDTO(complaintResponse.getId(), "rejected", new ComplaintResponseRequestDTO.ComplaintRequestDTO(complaint.getId(), false)), null,
                Set.of());
        request.putAndExpectError("/api/text/participations/" + fixture.participationId() + "/submissions/" + fixture.submissionId() + "/text-assessment-after-complaint", body,
                HttpStatus.BAD_REQUEST, "feedbackIdMismatch");

        assertThat(scalar("SELECT f.result.id FROM Feedback f WHERE f.id = :id", fixture.otherFeedbackId())).as("the feedback stays on the result that owns it")
                .isEqualTo(fixture.otherResultId());
        assertThat(scalar("SELECT c.accepted FROM Complaint c WHERE c.id = :id", complaint.getId())).as("the complaint is not resolved by the refused request")
                .isEqualTo(acceptedBefore);
        assertThat(scalar("SELECT cr.submittedTime FROM ComplaintResponse cr WHERE cr.id = :id", complaintResponse.getId()))
                .as("the complaint response is not submitted by the refused request").isEqualTo(submittedTimeBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void modelingAssessmentRejectsFeedbackIdOfAnotherResult() throws Exception {
        Course course = modelingExerciseUtilService.addCourseWithOneModelingExercise("ModelX");
        ModelingExercise exercise = (ModelingExercise) course.getExercises().iterator().next();
        enroll(TEST_PREFIX + "tutor1", course, CourseRole.TEACHING_ASSISTANT);
        ModelingSubmission submission = modelingExerciseUtilService.addModelingSubmission(exercise, ParticipationFactory.generateModelingSubmission("{}", true),
                TEST_PREFIX + "student1");
        Result result = participationUtilService.addResultToSubmission(AssessmentType.MANUAL, ZonedDateTime.now(), submission, TEST_PREFIX + "tutor1", List.of());

        Course otherCourse = modelingExerciseUtilService.addCourseWithOneModelingExercise("ModelY");
        ModelingExercise otherExercise = (ModelingExercise) otherCourse.getExercises().iterator().next();
        enroll(TEST_PREFIX + "tutor2", otherCourse, CourseRole.TEACHING_ASSISTANT);
        enroll(TEST_PREFIX + "student2", otherCourse, CourseRole.STUDENT);
        ModelingSubmission otherSubmission = modelingExerciseUtilService.addModelingSubmission(otherExercise, ParticipationFactory.generateModelingSubmission("{}", true),
                TEST_PREFIX + "student2");
        Result otherResult = participationUtilService.addResultToSubmission(AssessmentType.MANUAL, ZonedDateTime.now(), otherSubmission, TEST_PREFIX + "tutor2", List.of());
        Feedback otherFeedback = new Feedback().credits(3.0).type(FeedbackType.MANUAL_UNREFERENCED);
        otherFeedback.setDetailText("detail of the other result");
        participationUtilService.addFeedbackToResult(otherFeedback, otherResult);

        ModelingAssessmentDTO body = new ModelingAssessmentDTO(
                List.of(new FeedbackDTO(otherFeedback.getId(), "text", "detail", false, null, 5.0, true, FeedbackType.MANUAL_UNREFERENCED, null, null, null)), null);
        request.putAndExpectError("/api/modeling/modeling-submissions/" + submission.getId() + "/results/" + result.getId() + "/assessment?submit=false", body,
                HttpStatus.BAD_REQUEST, "feedbackIdMismatch");

        assertThat(scalar("SELECT f.result.id FROM Feedback f WHERE f.id = :id", otherFeedback.getId())).as("the feedback stays on the result that owns it")
                .isEqualTo(otherResult.getId());
        assertThat(scalar("SELECT f.detailText FROM Feedback f WHERE f.id = :id", otherFeedback.getId())).as("its detail text is unchanged")
                .isEqualTo("detail of the other result");
    }

    private void enroll(String login, Course course, CourseRole role) {
        userUtilService.enrollUserInCourse(userUtilService.getUserByLogin(login), course, role);
    }

    /**
     * Reads a single value through a fresh persistence context, so that a stored row is compared and not a cached entity.
     *
     * @param jpql the query selecting one value, with one parameter named id
     * @param id   the id to look up
     * @return the value, or null if no row matches
     */
    private Object scalar(String jpql, Object id) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            return entityManager.createQuery(jpql).setParameter("id", id).getSingleResult();
        }
        catch (NoResultException noResult) {
            return null;
        }
        finally {
            entityManager.close();
        }
    }

    private record Fixture(String url, long participationId, long submissionId, long resultId, long otherResultId, long otherFeedbackId, TextSubmission otherSubmission) {
    }

    /**
     * Builds a result the tutor may assess in one course, and a second result with a long feedback text in a course the tutor has no role in.
     *
     * @param exerciseTitle      the title of the exercise the tutor assesses
     * @param otherExerciseTitle the title of the exercise in the other course
     * @return the ids of both results and the url of the assessment endpoint
     */
    private Fixture setUpTextFixture(String exerciseTitle, String otherExerciseTitle) {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise(exerciseTitle);
        TextExercise exercise = ExerciseUtilService.findTextExerciseWithTitle(course.getExercises(), exerciseTitle);
        enroll(TEST_PREFIX + "tutor1", course, CourseRole.TEACHING_ASSISTANT);
        enroll(TEST_PREFIX + "tutor3", course, CourseRole.TEACHING_ASSISTANT);
        TextSubmission submission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(exercise,
                ParticipationFactory.generateTextSubmission("some submission", Language.ENGLISH, true), TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        Course otherCourse = textExerciseUtilService.addCourseWithOneReleasedTextExercise(otherExerciseTitle);
        TextExercise otherExercise = ExerciseUtilService.findTextExerciseWithTitle(otherCourse.getExercises(), otherExerciseTitle);
        enroll(TEST_PREFIX + "tutor2", otherCourse, CourseRole.TEACHING_ASSISTANT);
        enroll(TEST_PREFIX + "student2", otherCourse, CourseRole.STUDENT);
        TextSubmission otherSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(otherExercise,
                ParticipationFactory.generateTextSubmission(OTHER_SUBMISSION_TEXT, Language.ENGLISH, true), TEST_PREFIX + "student2", TEST_PREFIX + "tutor2");
        Result otherResult = otherSubmission.getLatestResult();
        Feedback otherFeedback = new Feedback().credits(2.0).type(FeedbackType.MANUAL_UNREFERENCED);
        otherFeedback.setDetailText(OTHER_LONG_TEXT);
        participationUtilService.addFeedbackToResult(otherFeedback, otherResult);
        assertThat(otherFeedback.getHasLongFeedbackText()).as("setup: the other feedback owns a long feedback text").isTrue();

        String url = "/api/text/participations/" + submission.getParticipation().getId() + "/results/" + submission.getLatestResult().getId() + "/text-assessment";
        return new Fixture(url, submission.getParticipation().getId(), submission.getId(), submission.getLatestResult().getId(), otherResult.getId(), otherFeedback.getId(),
                otherSubmission);
    }
}
