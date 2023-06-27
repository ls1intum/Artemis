package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.AtheneRequestMockProvider;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackConflictType;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.dto.FeedbackConflictResponseDTO;
import de.tum.in.www1.artemis.user.UserUtilService;

class AutomaticFeedbackConflictServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "automaticfeedbackconflict";

    @Autowired
    private FeedbackConflictRepository feedbackConflictRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private TextClusterRepository textClusterRepository;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private AtheneRequestMockProvider atheneRequestMockProvider;

    @Autowired
    private AutomaticTextAssessmentConflictService automaticTextAssessmentConflictService;

    @Autowired
    private ResultService resultService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private TextExercise textExercise;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 0);
        textExercise = (TextExercise) textExerciseUtilService.addCourseWithOneFinishedTextExercise().getExercises().iterator().next();
        atheneRequestMockProvider.enableMockingOfRequests();

        feedbackConflictRepository.deleteAll(); // TODO: This should be improved by better tests that do not rely on an empty repository
    }

    @AfterEach
    void tearDown() throws Exception {
        atheneRequestMockProvider.reset();
        super.resetSpyBeans();
    }

    /**
     * Creates two text submissions with text blocks and feedback, adds text blocks to a cluster.
     * Mocks TextAssessmentConflictService class to not to connect to remote Athene service
     * Then checks if the text assessment conflicts are created and stored correctly.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createFeedbackConflicts() {
        TextSubmission textSubmission1 = ParticipationFactory.generateTextSubmission("first text submission", Language.ENGLISH, true);
        TextSubmission textSubmission2 = ParticipationFactory.generateTextSubmission("second text submission", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmission(textExercise, textSubmission1, TEST_PREFIX + "student1");
        textExerciseUtilService.saveTextSubmission(textExercise, textSubmission2, TEST_PREFIX + "student1");

        final TextCluster cluster = new TextCluster().exercise(textExercise);
        textClusterRepository.save(cluster);

        final TextBlock textBlock1 = new TextBlock().startIndex(0).endIndex(21).automatic().cluster(cluster);
        final TextBlock textBlock2 = new TextBlock().startIndex(0).endIndex(22).automatic().cluster(cluster);

        textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(Set.of(textBlock1), textSubmission1);
        textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(Set.of(textBlock2), textSubmission2);

        cluster.blocks(List.of(textBlock1, textBlock2));
        textClusterRepository.save(cluster);

        Feedback feedback1 = new Feedback().detailText("Good answer").credits(1D).reference(textBlock1.getId());
        Feedback feedback2 = new Feedback().detailText("Good answer").credits(2D).reference(textBlock2.getId());

        textSubmission1 = textExerciseUtilService.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission1, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1", List.of(feedback1));
        textSubmission2 = textExerciseUtilService.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission2, TEST_PREFIX + "student2",
                TEST_PREFIX + "tutor1", List.of(feedback2));

        // important: use the updated feedback that was already saved to the database and not the feedback1 and feedback2 objects
        feedback1 = textSubmission1.getLatestResult().getFeedbacks().get(0);
        feedback2 = textSubmission2.getLatestResult().getFeedbacks().get(0);

        atheneRequestMockProvider.mockFeedbackConsistency(createRemoteServiceResponse(feedback1, feedback2));
        automaticTextAssessmentConflictService.asyncCheckFeedbackConsistency(Set.of(textBlock1), new ArrayList<>(Collections.singletonList(feedback1)), textExercise.getId());

        Feedback finalFeedback = feedback1;
        await().until(() -> feedbackConflictRepository.findAllConflictsByFeedbackList(List.of(finalFeedback.getId())).size() > 0);

        assertThat(feedbackConflictRepository.findAllConflictsByFeedbackList(List.of(feedback1.getId()))).hasSize(1);
    }

    /**
     * Creates and stores a Text Assessment Conflict in the database.
     * Then sends a conflict with same feedback ids and different conflict type.
     * Checks if the conflict type in the database has changed.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void changedFeedbackConflictsType() {
        long feedbackConflictCountBefore = feedbackConflictRepository.count();

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("text submission", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");

        final TextCluster cluster = new TextCluster().exercise(textExercise);
        textClusterRepository.save(cluster);

        final TextBlock textBlock = new TextBlock().startIndex(0).endIndex(15).automatic().cluster(cluster);
        textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(Set.of(textBlock), textSubmission);

        Feedback feedback1 = new Feedback().detailText("Good answer").credits(1D).reference(textBlock.getId());
        Feedback feedback2 = new Feedback().detailText("Bad answer").credits(2D);
        textSubmission = textExerciseUtilService.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1",
                List.of(feedback1, feedback2));

        // important: use the updated feedback that was already saved to the database and not the feedback1 and feedback2 objects
        Feedback updatedFeedback1 = textSubmission.getLatestResult().getFeedbacks().get(0);
        Feedback updatedFeedback2 = textSubmission.getLatestResult().getFeedbacks().get(1);
        FeedbackConflict feedbackConflict = ParticipationFactory.generateFeedbackConflictBetweenFeedbacks(updatedFeedback1, updatedFeedback2);
        feedbackConflict.setType(FeedbackConflictType.INCONSISTENT_COMMENT);
        feedbackConflictRepository.save(feedbackConflict);

        atheneRequestMockProvider.mockFeedbackConsistency(createRemoteServiceResponse(updatedFeedback1, updatedFeedback2));
        automaticTextAssessmentConflictService.asyncCheckFeedbackConsistency(Set.of(textBlock), new ArrayList<>(Collections.singletonList(updatedFeedback1)), textExercise.getId());

        await().until(() -> {
            var newFeedbackConflict = feedbackConflictRepository.findByFirstFeedbackIdAndConflict(updatedFeedback1.getId(), true).iterator().next();
            // wait until the async method finished (type changed)
            return newFeedbackConflict.getType() != FeedbackConflictType.INCONSISTENT_COMMENT;
        });

        assertThat(feedbackConflictRepository.count()).isEqualTo(feedbackConflictCountBefore + 1);
        var newFeedbackConflict = feedbackConflictRepository.findByFirstFeedbackIdAndConflict(updatedFeedback1.getId(), true).iterator().next();
        assertThat(newFeedbackConflict.getFirstFeedback()).isEqualTo(updatedFeedback1);
        assertThat(newFeedbackConflict.getSecondFeedback()).isEqualTo(updatedFeedback2);
        assertThat(newFeedbackConflict.getType()).isEqualTo(FeedbackConflictType.INCONSISTENT_SCORE);
    }

    /**
     * Creates and stores a Text Assessment Conflict in the database.
     * Then a same feedback is sent to the conflict checking class.
     * Empty list is returned from the mock object. (meaning: no conflicts have found)
     * Checks if the conflict set as solved in the database.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void solveFeedbackConflicts() {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("text submission", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");

        final TextCluster cluster = new TextCluster().exercise(textExercise);
        textClusterRepository.save(cluster);

        final TextBlock textBlock = new TextBlock().startIndex(0).endIndex(15).automatic().cluster(cluster);
        textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(Set.of(textBlock), textSubmission);

        Feedback feedback1 = new Feedback().detailText("Good answer").credits(1D).reference(textBlock.getId());
        Feedback feedback2 = new Feedback().detailText("Bad answer").credits(2D);
        textSubmission = textExerciseUtilService.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1",
                List.of(feedback1, feedback2));

        // important: use the updated feedback that was already saved to the database and not the feedback1 and feedback2 objects
        feedback1 = textSubmission.getLatestResult().getFeedbacks().get(0);
        feedback2 = textSubmission.getLatestResult().getFeedbacks().get(1);
        FeedbackConflict feedbackConflict = ParticipationFactory.generateFeedbackConflictBetweenFeedbacks(feedback1, feedback2);
        feedbackConflictRepository.save(feedbackConflict);

        atheneRequestMockProvider.mockFeedbackConsistency(List.of());
        automaticTextAssessmentConflictService.asyncCheckFeedbackConsistency(Set.of(textBlock), new ArrayList<>(List.of(feedback1, feedback2)), textExercise.getId());

        Feedback finalFeedback = feedback1;
        await().until(() -> feedbackConflictRepository.findByFirstFeedbackIdAndConflict(finalFeedback.getId(), false).size() > 0);

        assertThat(feedbackConflictRepository.findByFirstFeedbackIdAndConflict(finalFeedback.getId(), false)).hasSize(1);

        var returnedFeedbackConflict = feedbackConflictRepository.findByFirstFeedbackIdAndConflict(finalFeedback.getId(), false).get(0);
        assertThat(returnedFeedbackConflict.getFirstFeedback()).isEqualTo(feedback1);
        assertThat(returnedFeedbackConflict.getSecondFeedback()).isEqualTo(feedback2);
        assertThat(returnedFeedbackConflict.getConflict()).isFalse();
        assertThat(returnedFeedbackConflict.getSolvedAt()).isNotNull();
    }

    /**
     * Checks if deletion of a result delete the text assessment conflicts from the database.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testResultDelete() {
        TextSubmission textSubmission = createTextSubmissionWithResultFeedbackAndConflicts();

        assertThat(feedbackConflictRepository.findAllConflictsByFeedbackList(List.of(textSubmission.getLatestResult().getFeedbacks().get(0).getId()))).isNotEmpty();

        resultService.deleteResult(textSubmission.getLatestResult(), true);

        assertThat(feedbackConflictRepository.findAllConflictsByFeedbackList(List.of(textSubmission.getLatestResult().getFeedbacks().get(0).getId()))).isEmpty();
    }

    /**
     * Checks the deletion of text assessment conflicts do not cause deletion of feedback.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testFeedbackConflictDelete() {
        var textSubmission = createTextSubmissionWithResultFeedbackAndConflicts();
        feedbackConflictRepository.deleteAll();
        var feedbacks = feedbackRepository.findByResult(textSubmission.getLatestResult());
        assertThat(feedbacks).hasSize(2);
    }

    private List<FeedbackConflictResponseDTO> createRemoteServiceResponse(Feedback firstFeedback, Feedback secondFeedback) {
        FeedbackConflictResponseDTO feedbackConflictResponseDTO = new FeedbackConflictResponseDTO();
        feedbackConflictResponseDTO.setFirstFeedbackId(firstFeedback.getId());
        feedbackConflictResponseDTO.setSecondFeedbackId(secondFeedback.getId());
        feedbackConflictResponseDTO.setType(FeedbackConflictType.INCONSISTENT_SCORE);
        return List.of(feedbackConflictResponseDTO);
    }

    private TextSubmission createTextSubmissionWithResultFeedbackAndConflicts() {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("text submission", Language.ENGLISH, true);
        final Feedback feedback1 = new Feedback().detailText("Good answer").credits(1D);
        final Feedback feedback2 = new Feedback().detailText("Bad answer").credits(2D);
        textSubmission = textExerciseUtilService.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1",
                List.of(feedback1, feedback2));

        // important: use the updated feedback that was already saved to the database and not the feedback1 and feedback2 objects
        FeedbackConflict feedbackConflict = ParticipationFactory.generateFeedbackConflictBetweenFeedbacks(textSubmission.getLatestResult().getFeedbacks().get(0),
                textSubmission.getLatestResult().getFeedbacks().get(1));
        feedbackConflictRepository.save(feedbackConflict);
        return textSubmission;
    }

}
