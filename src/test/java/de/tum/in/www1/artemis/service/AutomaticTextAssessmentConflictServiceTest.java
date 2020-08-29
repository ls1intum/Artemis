package de.tum.in.www1.artemis.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.TextAssessmentConflictType;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.TextAssessmentConflictRepository;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.TextClusterRepository;
import de.tum.in.www1.artemis.service.connectors.TextAssessmentConflictService;
import de.tum.in.www1.artemis.service.dto.TextAssessmentConflictResponseDTO;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;

public class AutomaticTextAssessmentConflictServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    TextAssessmentConflictRepository textAssessmentConflictRepository;

    @Autowired
    FeedbackRepository feedbackRepository;

    @Autowired
    TextClusterRepository textClusterRepository;

    @Autowired
    TextBlockRepository textBlockRepository;

    @Autowired
    DatabaseUtilService database;

    AutomaticTextAssessmentConflictService automaticTextAssessmentConflictService;

    TextAssessmentConflictService textAssessmentConflictService;

    TextExercise textExercise;

    @BeforeEach
    void init() {
        database.addUsers(2, 1, 0);
        textExercise = (TextExercise) database.addCourseWithOneFinishedTextExercise().getExercises().iterator().next();
    }

    @Test
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

        automaticTextAssessmentConflictService = new AutomaticTextAssessmentConflictService(textAssessmentConflictRepository, feedbackRepository, textBlockRepository,
                textAssessmentConflictService);

        automaticTextAssessmentConflictService.asyncCheckFeedbackConsistency(List.of(textBlock1), new ArrayList<>(Collections.singletonList(feedback1)), textExercise.getId());

        verify(textAssessmentConflictService, timeout(100).times(1)).checkFeedbackConsistencies(any(), anyLong(), anyInt());

        assertThat(textAssessmentConflictRepository.findAll(), hasSize(1));
        assertThat(textAssessmentConflictRepository.findAll().get(0).getFirstFeedback(), Matchers.either(is(feedback1)).or(is(feedback2)));
        assertThat(textAssessmentConflictRepository.findAll().get(0).getSecondFeedback(), Matchers.either(is(feedback1)).or(is(feedback2)));
        feedbackRepository.deleteAll();
        assertThat(textAssessmentConflictRepository.findAll(), hasSize(0));
    }

    private List<TextAssessmentConflictResponseDTO> createRemoteServiceResponse(Feedback firstFeedback, Feedback secondFeedback) {
        TextAssessmentConflictResponseDTO textAssessmentConflictResponseDTO = new TextAssessmentConflictResponseDTO();
        textAssessmentConflictResponseDTO.setFirstFeedbackId(firstFeedback.getId());
        textAssessmentConflictResponseDTO.setSecondFeedbackId(secondFeedback.getId());
        textAssessmentConflictResponseDTO.setType(TextAssessmentConflictType.INCONSISTENT_SCORE);
        return List.of(textAssessmentConflictResponseDTO);
    }

}
