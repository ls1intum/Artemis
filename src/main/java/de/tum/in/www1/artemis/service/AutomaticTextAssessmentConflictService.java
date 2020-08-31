package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.TextAssessmentConflictRepository;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.service.connectors.TextAssessmentConflictService;
import de.tum.in.www1.artemis.service.dto.TextAssessmentConflictRequestDTO;
import de.tum.in.www1.artemis.service.dto.TextAssessmentConflictResponseDTO;

@Service
@Profile("automaticText")
public class AutomaticTextAssessmentConflictService {

    private final Logger log = LoggerFactory.getLogger(AutomaticTextAssessmentConflictService.class);

    private final TextAssessmentConflictRepository textAssessmentConflictRepository;

    private final FeedbackRepository feedbackRepository;

    private final TextBlockRepository textBlockRepository;

    private final TextAssessmentConflictService textAssessmentConflictService;

    public AutomaticTextAssessmentConflictService(TextAssessmentConflictRepository textAssessmentConflictRepository, FeedbackRepository feedbackRepository,
            TextBlockRepository textBlockRepository, TextAssessmentConflictService textAssessmentConflictService) {
        this.textAssessmentConflictRepository = textAssessmentConflictRepository;
        this.feedbackRepository = feedbackRepository;
        this.textBlockRepository = textBlockRepository;
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

        // If text block doesn't have a cluster id don't create an object
        List<TextAssessmentConflictRequestDTO> textAssessmentConflictRequestDTOS = feedbackList.stream().flatMap(feedback -> {
            Optional<TextBlock> textBlock = textBlockRepository
                    .findById(textBlocks.stream().filter(block -> block.getId().equals(feedback.getReference())).findFirst().get().getId());
            if (textBlock.isPresent() && textBlock.get().getCluster() != null) {
                return Stream.of(new TextAssessmentConflictRequestDTO(textBlock.get().getId(), textBlock.get().getText(), textBlock.get().getCluster().getId(), feedback.getId(),
                        feedback.getDetailText(), feedback.getCredits()));
            }
            else {
                return Stream.empty();
            }
        }).collect(toList());

        if (textAssessmentConflictRequestDTOS.isEmpty()) {
            return;
        }

        // remote service call to athene
        final List<TextAssessmentConflictResponseDTO> conflicts;
        try {
            conflicts = textAssessmentConflictService.checkFeedbackConsistencies(textAssessmentConflictRequestDTOS, exerciseId, 0);
        }
        catch (NetworkingError networkingError) {
            log.error(networkingError.getMessage(), networkingError);
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
                    textAssessmentConflict.setCreatedAt(ZonedDateTime.now());
                    textAssessmentConflicts.add(textAssessmentConflict);
                }
            });
            textAssessmentConflictRepository.saveAll(textAssessmentConflicts);
        }
    }

    /**
     * Finds and returns the submissions which have the conflicting feedback with the feedback of passed result
     *
     * @param result - A result object to find its feedback
     * @return Set of text submissions
     */
    public Set<TextSubmission> getConflictingSubmissions(Result result) {
        List<Long> feedbackIdList = result.getFeedbacks().stream().map(Feedback::getId).collect(toList());
        List<TextAssessmentConflict> textAssessmentConflicts = this.textAssessmentConflictRepository.findAllByFeedback(feedbackIdList);
        Set<TextSubmission> textSubmissionSet1 = textAssessmentConflicts.stream().map(conflict -> (TextSubmission) conflict.getFirstFeedback().getResult().getSubmission())
                .collect(toSet());
        Set<TextSubmission> textSubmissionSet2 = textAssessmentConflicts.stream().map(conflict -> (TextSubmission) conflict.getSecondFeedback().getResult().getSubmission())
                .collect(toSet());
        textSubmissionSet1.addAll(textSubmissionSet2);
        textSubmissionSet1.removeIf(textSubmission -> textSubmission.equals(result.getSubmission()));
        return textSubmissionSet1;
    }
}
