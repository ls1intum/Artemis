package de.tum.in.www1.artemis.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackConflictType;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.TextAssessmentConflictService;
import de.tum.in.www1.artemis.service.dto.FeedbackConflictResponseDTO;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;

public class AutomaticFeedbackConflictServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    FeedbackConflictRepository feedbackConflictRepository;

    @Autowired
    FeedbackRepository feedbackRepository;

    @Autowired
    TextClusterRepository textClusterRepository;

    @Autowired
    TextBlockRepository textBlockRepository;

    @Autowired
    TextSubmissionRepository textSubmissionRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    DatabaseUtilService database;

    AutomaticTextAssessmentConflictService automaticTextAssessmentConflictService;

    TextAssessmentConflictService textAssessmentConflictService;

    TextExercise textExercise;

    @BeforeEach
    public void init() {
        database.addUsers(2, 1, 0);
        textExercise = (TextExercise) database.addCourseWithOneFinishedTextExercise().getExercises().iterator().next();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    /**
     * Creates two text submissions with text blocks and feedback, adds text blocks to a cluster.
     * Mocks TextAssessmentConflictService class to not to connect to remote Athene service
     * Then checks if the text assessment conflicts are created and stored correctly.
     * @throws NetworkingError - it never throws an error since the TextAssessmentConflictService is a mock class
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void createFeedbackConflicts() throws NetworkingError {
        TextSubmission textSubmission1 = ModelFactory.generateTextSubmission("first text submission", Language.ENGLISH, true);
        TextSubmission textSubmission2 = ModelFactory.generateTextSubmission("second text submission", Language.ENGLISH, true);
        database.addTextSubmission(textExercise, textSubmission1, "student1");
        database.addTextSubmission(textExercise, textSubmission2, "student1");

        final TextCluster cluster = new TextCluster().exercise(textExercise);
        textClusterRepository.save(cluster);

        final TextBlock textBlock1 = new TextBlock().startIndex(0).endIndex(21).automatic().cluster(cluster);
        final TextBlock textBlock2 = new TextBlock().startIndex(0).endIndex(22).automatic().cluster(cluster);

        database.addTextBlocksToTextSubmission(List.of(textBlock1), textSubmission1);
        database.addTextBlocksToTextSubmission(List.of(textBlock2), textSubmission2);

        cluster.blocks(List.of(textBlock1, textBlock2));
        textClusterRepository.save(cluster);

        final Feedback feedback1 = new Feedback().detailText("Good answer").credits(1D).reference(textBlock1.getId());
        final Feedback feedback2 = new Feedback().detailText("Good answer").credits(2D).reference(textBlock2.getId());

        database.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission1, "student1", "tutor1", List.of(feedback1));
        database.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission2, "student2", "tutor1", List.of(feedback2));

        textAssessmentConflictService = mock(TextAssessmentConflictService.class);
        when(textAssessmentConflictService.checkFeedbackConsistencies(any(), anyLong(), anyInt())).thenReturn(createRemoteServiceResponse(feedback1, feedback2));

        automaticTextAssessmentConflictService = new AutomaticTextAssessmentConflictService(feedbackConflictRepository, feedbackRepository, textBlockRepository,
                textAssessmentConflictService);

        automaticTextAssessmentConflictService.asyncCheckFeedbackConsistency(List.of(textBlock1), new ArrayList<>(Collections.singletonList(feedback1)), textExercise.getId());

        verify(textAssessmentConflictService, timeout(100).times(1)).checkFeedbackConsistencies(any(), anyLong(), anyInt());

        assertThat(feedbackConflictRepository.findAll(), hasSize(1));
        assertThat(feedbackConflictRepository.findAll().get(0).getFirstFeedback(), either(is(feedback1)).or(is(feedback2)));
        assertThat(feedbackConflictRepository.findAll().get(0).getSecondFeedback(), either(is(feedback1)).or(is(feedback2)));

    }

    /**
     * Creates and stores a Text Assessment Conflict in the database.
     * Then sends a conflict with same feedback ids and different conflict type.
     * Checks if the conflict type in the database has changed.
     * @throws NetworkingError - it never throws an error since the TextAssessmentConflictService is a mock class
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void changedFeedbackConflictsType() throws NetworkingError {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("text submission", Language.ENGLISH, true);
        database.addTextSubmission(textExercise, textSubmission, "student1");

        final TextCluster cluster = new TextCluster().exercise(textExercise);
        textClusterRepository.save(cluster);

        final TextBlock textBlock = new TextBlock().startIndex(0).endIndex(15).automatic().cluster(cluster);
        database.addTextBlocksToTextSubmission(List.of(textBlock), textSubmission);

        final Feedback feedback1 = new Feedback().detailText("Good answer").credits(1D).reference(textBlock.getId());
        final Feedback feedback2 = new Feedback().detailText("Bad answer").credits(2D);
        database.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission, "student1", "tutor1", List.of(feedback1, feedback2));

        FeedbackConflict feedbackConflict = ModelFactory.generateFeedbackConflictBetweenFeedbacks(feedback1, feedback2);
        feedbackConflict.setType(FeedbackConflictType.INCONSISTENT_COMMENT);
        feedbackConflictRepository.save(feedbackConflict);

        textAssessmentConflictService = mock(TextAssessmentConflictService.class);
        when(textAssessmentConflictService.checkFeedbackConsistencies(any(), anyLong(), anyInt())).thenReturn(createRemoteServiceResponse(feedback1, feedback2));

        automaticTextAssessmentConflictService = new AutomaticTextAssessmentConflictService(feedbackConflictRepository, feedbackRepository, textBlockRepository,
                textAssessmentConflictService);

        automaticTextAssessmentConflictService.asyncCheckFeedbackConsistency(List.of(textBlock), new ArrayList<>(Collections.singletonList(feedback1)), textExercise.getId());

        assertThat(feedbackConflictRepository.findAll(), hasSize(1));
        assertThat(feedbackConflictRepository.findAll().get(0).getFirstFeedback(), is(feedback1));
        assertThat(feedbackConflictRepository.findAll().get(0).getSecondFeedback(), is(feedback2));
        assertThat(feedbackConflictRepository.findAll().get(0).getType(), is(FeedbackConflictType.INCONSISTENT_SCORE));
    }

    /**
     * Creates and stores a Text Assessment Conflict in the database.
     * Then a same feedback is sent to the conflict checking class.
     * Empty list is returned from the mock object. (meaning: no conflicts have found)
     * Checks if the conflict set as solved in the database.
     * @throws NetworkingError - it never throws an error since the TextAssessmentConflictService is a mock class
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void solveFeedbackConflicts() throws NetworkingError {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("text submission", Language.ENGLISH, true);
        database.addTextSubmission(textExercise, textSubmission, "student1");

        final TextCluster cluster = new TextCluster().exercise(textExercise);
        textClusterRepository.save(cluster);

        final TextBlock textBlock = new TextBlock().startIndex(0).endIndex(15).automatic().cluster(cluster);
        database.addTextBlocksToTextSubmission(List.of(textBlock), textSubmission);

        final Feedback feedback1 = new Feedback().detailText("Good answer").credits(1D).reference(textBlock.getId());
        final Feedback feedback2 = new Feedback().detailText("Bad answer").credits(2D);
        database.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission, "student1", "tutor1", List.of(feedback1, feedback2));

        FeedbackConflict feedbackConflict = ModelFactory.generateFeedbackConflictBetweenFeedbacks(feedback1, feedback2);
        feedbackConflictRepository.save(feedbackConflict);

        textAssessmentConflictService = mock(TextAssessmentConflictService.class);
        when(textAssessmentConflictService.checkFeedbackConsistencies(any(), anyLong(), anyInt())).thenReturn(List.of());

        automaticTextAssessmentConflictService = new AutomaticTextAssessmentConflictService(feedbackConflictRepository, feedbackRepository, textBlockRepository,
                textAssessmentConflictService);

        automaticTextAssessmentConflictService.asyncCheckFeedbackConsistency(List.of(textBlock), new ArrayList<>(List.of(feedback1, feedback2)), textExercise.getId());

        assertThat(feedbackConflictRepository.findAll(), hasSize(1));
        assertThat(feedbackConflictRepository.findAll().get(0).getFirstFeedback(), is(feedback1));
        assertThat(feedbackConflictRepository.findAll().get(0).getSecondFeedback(), is(feedback2));
        assertThat(feedbackConflictRepository.findAll().get(0).getConflict(), is(Boolean.FALSE));
        assertThat(feedbackConflictRepository.findAll().get(0).getSolvedAt(), is(notNullValue()));
    }

    /**
     * Checks if deletion of submission delete the text assessment conflicts from the database.
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testSubmissionDelete() {
        TextSubmission textSubmission = this.createTextSubmissionWithResultFeedbackAndConflicts();
        textSubmissionRepository.deleteById(textSubmission.getId());
        assertThat(feedbackConflictRepository.findAll(), hasSize(0));
    }

    /**
     * Checks if deletion of a result delete the text assessment conflicts from the database.
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testResultDelete() {
        TextSubmission textSubmission = this.createTextSubmissionWithResultFeedbackAndConflicts();
        resultRepository.deleteById(textSubmission.getResult().getId());
        assertThat(feedbackConflictRepository.findAll(), hasSize(0));
    }

    /**
     * Checks if deletion of feedback delete the text assessment conflicts from the database.
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testFeedbackDelete() {
        this.createTextSubmissionWithResultFeedbackAndConflicts();
        feedbackRepository.deleteAll();
        assertThat(feedbackConflictRepository.findAll(), hasSize(0));
    }

    /**
     * Checks the deletion of text assessment conflicts do not cause deletion of feedback.
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testFeedbackConflictDelete() {
        this.createTextSubmissionWithResultFeedbackAndConflicts();
        feedbackConflictRepository.deleteAll();
        assertThat(feedbackRepository.findAll(), hasSize(2));
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
        database.addTextSubmission(textExercise, textSubmission, "student1");

        final Feedback feedback1 = new Feedback().detailText("Good answer").credits(1D);
        final Feedback feedback2 = new Feedback().detailText("Bad answer").credits(2D);
        database.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission, "student1", "tutor1", List.of(feedback1, feedback2));

        FeedbackConflict feedbackConflict = ModelFactory.generateFeedbackConflictBetweenFeedbacks(feedback1, feedback2);
        feedbackConflictRepository.save(feedbackConflict);

        return textSubmission;
    }

}
