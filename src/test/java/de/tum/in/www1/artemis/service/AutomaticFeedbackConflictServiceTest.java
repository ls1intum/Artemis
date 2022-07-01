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
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.dto.FeedbackConflictResponseDTO;
import de.tum.in.www1.artemis.util.ModelFactory;

class AutomaticFeedbackConflictServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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

    private TextExercise textExercise;

    @BeforeEach
    void init() {
        database.addUsers(2, 1, 0, 0);
        textExercise = (TextExercise) database.addCourseWithOneFinishedTextExercise().getExercises().iterator().next();
        atheneRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
        atheneRequestMockProvider.reset();
    }

    /**
     * Creates two text submissions with text blocks and feedback, adds text blocks to a cluster.
     * Mocks TextAssessmentConflictService class to not to connect to remote Athene service
     * Then checks if the text assessment conflicts are created and stored correctly.
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void createFeedbackConflicts() {
        TextSubmission textSubmission1 = ModelFactory.generateTextSubmission("first text submission", Language.ENGLISH, true);
        TextSubmission textSubmission2 = ModelFactory.generateTextSubmission("second text submission", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission1, "student1");
        database.saveTextSubmission(textExercise, textSubmission2, "student1");

        final TextCluster cluster = new TextCluster().exercise(textExercise);
        textClusterRepository.save(cluster);

        final TextBlock textBlock1 = new TextBlock().startIndex(0).endIndex(21).automatic().cluster(cluster);
        final TextBlock textBlock2 = new TextBlock().startIndex(0).endIndex(22).automatic().cluster(cluster);

        database.addAndSaveTextBlocksToTextSubmission(Set.of(textBlock1), textSubmission1);
        database.addAndSaveTextBlocksToTextSubmission(Set.of(textBlock2), textSubmission2);

        cluster.blocks(List.of(textBlock1, textBlock2));
        textClusterRepository.save(cluster);

        Feedback feedback1 = new Feedback().detailText("Good answer").credits(1D).reference(textBlock1.getId());
        Feedback feedback2 = new Feedback().detailText("Good answer").credits(2D).reference(textBlock2.getId());

        textSubmission1 = database.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission1, "student1", "tutor1", List.of(feedback1));
        textSubmission2 = database.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission2, "student2", "tutor1", List.of(feedback2));

        // important: use the updated feedback that was already saved to the database and not the feedback1 and feedback2 objects
        feedback1 = textSubmission1.getLatestResult().getFeedbacks().get(0);
        feedback2 = textSubmission2.getLatestResult().getFeedbacks().get(0);

        atheneRequestMockProvider.mockFeedbackConsistency(createRemoteServiceResponse(feedback1, feedback2));
        automaticTextAssessmentConflictService.asyncCheckFeedbackConsistency(Set.of(textBlock1), new ArrayList<>(Collections.singletonList(feedback1)), textExercise.getId());

        await().until(() -> feedbackConflictRepository.count() >= 0);

        assertThat(feedbackConflictRepository.findAll()).hasSize(1);
        assertThat(feedbackConflictRepository.findAll().get(0).getFirstFeedback()).isIn(feedback1, feedback2);
        assertThat(feedbackConflictRepository.findAll().get(0).getSecondFeedback()).isIn(feedback1, feedback2);
    }

    /**
     * Creates and stores a Text Assessment Conflict in the database.
     * Then sends a conflict with same feedback ids and different conflict type.
     * Checks if the conflict type in the database has changed.
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void changedFeedbackConflictsType() {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("text submission", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission, "student1");

        final TextCluster cluster = new TextCluster().exercise(textExercise);
        textClusterRepository.save(cluster);

        final TextBlock textBlock = new TextBlock().startIndex(0).endIndex(15).automatic().cluster(cluster);
        database.addAndSaveTextBlocksToTextSubmission(Set.of(textBlock), textSubmission);

        Feedback feedback1 = new Feedback().detailText("Good answer").credits(1D).reference(textBlock.getId());
        Feedback feedback2 = new Feedback().detailText("Bad answer").credits(2D);
        textSubmission = database.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission, "student1", "tutor1", List.of(feedback1, feedback2));

        // important: use the updated feedback that was already saved to the database and not the feedback1 and feedback2 objects
        feedback1 = textSubmission.getLatestResult().getFeedbacks().get(0);
        feedback2 = textSubmission.getLatestResult().getFeedbacks().get(1);
        FeedbackConflict feedbackConflict = ModelFactory.generateFeedbackConflictBetweenFeedbacks(feedback1, feedback2);
        feedbackConflict.setType(FeedbackConflictType.INCONSISTENT_COMMENT);
        feedbackConflictRepository.save(feedbackConflict);

        atheneRequestMockProvider.mockFeedbackConsistency(createRemoteServiceResponse(feedback1, feedback2));
        automaticTextAssessmentConflictService.asyncCheckFeedbackConsistency(Set.of(textBlock), new ArrayList<>(Collections.singletonList(feedback1)), textExercise.getId());

        await().until(() -> feedbackConflictRepository.count() >= 0);

        assertThat(feedbackConflictRepository.findAll()).hasSize(1);
        assertThat(feedbackConflictRepository.findAll().get(0).getFirstFeedback()).isEqualTo(feedback1);
        assertThat(feedbackConflictRepository.findAll().get(0).getSecondFeedback()).isEqualTo(feedback2);
        assertThat(feedbackConflictRepository.findAll().get(0).getType()).isEqualTo(FeedbackConflictType.INCONSISTENT_SCORE);
    }

    /**
     * Creates and stores a Text Assessment Conflict in the database.
     * Then a same feedback is sent to the conflict checking class.
     * Empty list is returned from the mock object. (meaning: no conflicts have found)
     * Checks if the conflict set as solved in the database.
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void solveFeedbackConflicts() {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("text submission", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission, "student1");

        final TextCluster cluster = new TextCluster().exercise(textExercise);
        textClusterRepository.save(cluster);

        final TextBlock textBlock = new TextBlock().startIndex(0).endIndex(15).automatic().cluster(cluster);
        database.addAndSaveTextBlocksToTextSubmission(Set.of(textBlock), textSubmission);

        Feedback feedback1 = new Feedback().detailText("Good answer").credits(1D).reference(textBlock.getId());
        Feedback feedback2 = new Feedback().detailText("Bad answer").credits(2D);
        textSubmission = database.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission, "student1", "tutor1", List.of(feedback1, feedback2));

        // important: use the updated feedback that was already saved to the database and not the feedback1 and feedback2 objects
        feedback1 = textSubmission.getLatestResult().getFeedbacks().get(0);
        feedback2 = textSubmission.getLatestResult().getFeedbacks().get(1);
        FeedbackConflict feedbackConflict = ModelFactory.generateFeedbackConflictBetweenFeedbacks(feedback1, feedback2);
        feedbackConflictRepository.save(feedbackConflict);

        atheneRequestMockProvider.mockFeedbackConsistency(List.of());
        automaticTextAssessmentConflictService.asyncCheckFeedbackConsistency(Set.of(textBlock), new ArrayList<>(List.of(feedback1, feedback2)), textExercise.getId());

        await().until(() -> feedbackConflictRepository.count() >= 0);

        assertThat(feedbackConflictRepository.findAll()).hasSize(1);
        assertThat(feedbackConflictRepository.findAll().get(0).getFirstFeedback()).isEqualTo(feedback1);
        assertThat(feedbackConflictRepository.findAll().get(0).getSecondFeedback()).isEqualTo(feedback2);
        assertThat(feedbackConflictRepository.findAll().get(0).getConflict()).isFalse();
        assertThat(feedbackConflictRepository.findAll().get(0).getSolvedAt()).isNotNull();
    }

    /**
     * Checks if deletion of submission delete the text assessment conflicts from the database.
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testSubmissionDelete() {
        TextSubmission textSubmission = createTextSubmissionWithResultFeedbackAndConflicts();
        textSubmissionRepository.deleteById(textSubmission.getId());
        assertThat(feedbackConflictRepository.findAll()).isEmpty();
    }

    /**
     * Checks if deletion of a result delete the text assessment conflicts from the database.
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testResultDelete() {
        TextSubmission textSubmission = createTextSubmissionWithResultFeedbackAndConflicts();
        resultRepository.deleteById(textSubmission.getLatestResult().getId());
        assertThat(feedbackConflictRepository.findAll()).isEmpty();
    }

    /**
     * Checks if deletion of feedback delete the text assessment conflicts from the database.
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testFeedbackDelete() {
        this.createTextSubmissionWithResultFeedbackAndConflicts();
        feedbackRepository.deleteAll();
        assertThat(feedbackConflictRepository.findAll()).isEmpty();
    }

    /**
     * Checks the deletion of text assessment conflicts do not cause deletion of feedback.
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testFeedbackConflictDelete() {
        createTextSubmissionWithResultFeedbackAndConflicts();
        feedbackConflictRepository.deleteAll();
        assertThat(feedbackRepository.findAll()).hasSize(2);
    }

    private List<FeedbackConflictResponseDTO> createRemoteServiceResponse(Feedback firstFeedback, Feedback secondFeedback) {
        FeedbackConflictResponseDTO feedbackConflictResponseDTO = new FeedbackConflictResponseDTO();
        feedbackConflictResponseDTO.setFirstFeedbackId(firstFeedback.getId());
        feedbackConflictResponseDTO.setSecondFeedbackId(secondFeedback.getId());
        feedbackConflictResponseDTO.setType(FeedbackConflictType.INCONSISTENT_SCORE);
        return List.of(feedbackConflictResponseDTO);
    }

    private TextSubmission createTextSubmissionWithResultFeedbackAndConflicts() {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("text submission", Language.ENGLISH, true);
        final Feedback feedback1 = new Feedback().detailText("Good answer").credits(1D);
        final Feedback feedback2 = new Feedback().detailText("Bad answer").credits(2D);
        textSubmission = database.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission, "student1", "tutor1", List.of(feedback1, feedback2));

        // important: use the updated feedback that was already saved to the database and not the feedback1 and feedback2 objects
        FeedbackConflict feedbackConflict = ModelFactory.generateFeedbackConflictBetweenFeedbacks(textSubmission.getLatestResult().getFeedbacks().get(0),
                textSubmission.getLatestResult().getFeedbacks().get(1));
        feedbackConflictRepository.save(feedbackConflict);
        return textSubmission;
    }

}
