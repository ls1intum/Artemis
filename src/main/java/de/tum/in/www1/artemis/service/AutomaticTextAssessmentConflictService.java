package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.toList;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextAssessmentConflict;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.TextAssessmentConflictRepository;
import de.tum.in.www1.artemis.service.connectors.TextAssessmentConflictService;
import de.tum.in.www1.artemis.service.dto.TextAssessmentConflictRequestDTO;
import de.tum.in.www1.artemis.service.dto.TextAssessmentConflictResponseDTO;

@Service
@Profile("automaticText")
public class AutomaticTextAssessmentConflictService {

    private final TextAssessmentConflictRepository textAssessmentConflictRepository;

    private final FeedbackRepository feedbackRepository;

    private final TextAssessmentConflictService textAssessmentConflictService;

    public AutomaticTextAssessmentConflictService(TextAssessmentConflictRepository textAssessmentConflictRepository, FeedbackRepository feedbackRepository,
            TextAssessmentConflictService textAssessmentConflictService) {
        this.textAssessmentConflictRepository = textAssessmentConflictRepository;
        this.feedbackRepository = feedbackRepository;
        this.textAssessmentConflictService = textAssessmentConflictService;
    }

    /**
     *  This function asynchronously calls remote Athene service to check feedback consistency for the assessed submission.
     *  The call is made if the automatic assessments are enabled and the passed text blocks belong to any cluster.
     *
     * @param textBlocks - all text blocks in the text assessment
     * @param feedbackList - all feedback in the text assessment
     * @param exerciseId - exercise id of the assessed text exercise
     */
    @Async
    public void asyncCheckFeedbackConsistency(List<TextBlock> textBlocks, List<Feedback> feedbackList, long exerciseId) {
        // remove the feedback that does not belong to any text block
        feedbackList.removeIf(f -> !f.hasReference());

        // Create TextAssessmentConflictRequestDTO objects that are used in service calls
        List<TextAssessmentConflictRequestDTO> textAssessmentConflictRequestDTOS = feedbackList.stream().map(feedback -> {
            TextBlock b = textBlocks.stream().filter(block -> block.getId().equals(feedback.getReference())).findFirst().get();
            return new TextAssessmentConflictRequestDTO(b.getId(), b.getText(), b.getCluster().getId(), feedback.getId(), feedback.getDetailText(), feedback.getCredits());
        }).collect(toList());

        // remove the objects that have no text block cluster id
        textAssessmentConflictRequestDTOS.removeIf(t -> t.getClusterId() == null);

        if (textAssessmentConflictRequestDTOS.isEmpty()) {
            return;
        }

        // remote service call to athene
        final List<TextAssessmentConflictResponseDTO> conflicts;
        try {
            conflicts = textAssessmentConflictService.checkFeedbackConsistencies(textAssessmentConflictRequestDTOS, exerciseId, 0);
        }
        catch (NetworkingError networkingError) {
            networkingError.printStackTrace();
            return;
        }

        // If there are conflicts save them in the TextAssessmentConflictRepository
        // Athene may find conflicts with feedback ids that are not in the feedback repository any more. So check for them. (May happen if the feedback is deleted in Artemis but
        // already stored in Athene)
        if (!conflicts.isEmpty()) {
            List<TextAssessmentConflict> textAssessmentConflicts = new ArrayList<>();
            conflicts.forEach(conflict -> {
                Optional<Feedback> firstFeedback = feedbackRepository.findById(conflict.getFirstFeedbackId());
                Optional<Feedback> secondFeedback = feedbackRepository.findById(conflict.getSecondFeedbackId());
                if (firstFeedback.isPresent() && secondFeedback.isPresent()) {
                    TextAssessmentConflict textAssessmentConflict = new TextAssessmentConflict();
                    textAssessmentConflict.setConflict(true);
                    textAssessmentConflict.setFirstFeedback(firstFeedback.get());
                    textAssessmentConflict.setSecondFeedback(secondFeedback.get());
                    textAssessmentConflict.setType(conflict.getType());
                    textAssessmentConflict.setCreated_at(ZonedDateTime.now());
                    textAssessmentConflicts.add(textAssessmentConflict);
                }
            });
            textAssessmentConflictRepository.saveAll(textAssessmentConflicts);
        }
    }
}
