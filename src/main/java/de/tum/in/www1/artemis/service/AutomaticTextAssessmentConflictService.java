package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.repository.FeedbackConflictRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.service.connectors.athene.TextAssessmentConflictService;
import de.tum.in.www1.artemis.service.dto.FeedbackConflictResponseDTO;
import de.tum.in.www1.artemis.service.dto.TextFeedbackConflictRequestDTO;

@Service
@Profile("athene")
public class AutomaticTextAssessmentConflictService {

    private final Logger log = LoggerFactory.getLogger(AutomaticTextAssessmentConflictService.class);

    private final FeedbackConflictRepository feedbackConflictRepository;

    private final FeedbackRepository feedbackRepository;

    private final TextBlockRepository textBlockRepository;

    private final TextAssessmentConflictService textAssessmentConflictService;

    public AutomaticTextAssessmentConflictService(FeedbackConflictRepository feedbackConflictRepository, FeedbackRepository feedbackRepository,
            TextBlockRepository textBlockRepository, TextAssessmentConflictService textAssessmentConflictService) {
        this.feedbackConflictRepository = feedbackConflictRepository;
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
    public void asyncCheckFeedbackConsistency(Set<TextBlock> textBlocks, List<Feedback> feedbackList, long exerciseId) {
        // Null blocks are passed in some test cases
        if (textBlocks == null || feedbackList == null || textBlocks.isEmpty()) {
            return;
        }

        // remove the feedback that does not belong to any text block
        feedbackList.removeIf(f -> !f.hasReference());

        // If text block doesn't have a cluster id don't create an object
        List<TextFeedbackConflictRequestDTO> textFeedbackConflictRequestDTOS = feedbackList.stream().flatMap(feedback -> {
            Optional<TextBlock> textBlock = textBlockRepository
                    .findById(textBlocks.stream().filter(block -> block.getId().equals(feedback.getReference())).findFirst().get().getId());
            if (textBlock.isPresent() && textBlock.get().getCluster() != null && feedback.getDetailText() != null) {
                return Stream.of(new TextFeedbackConflictRequestDTO(textBlock.get().getId(), textBlock.get().getText(), textBlock.get().getCluster().getId(), feedback.getId(),
                        feedback.getDetailText(), feedback.getCredits()));
            }
            else {
                return Stream.empty();
            }
        }).toList();

        if (textFeedbackConflictRequestDTOS.isEmpty()) {
            return;
        }

        // remote service call to athene
        final List<FeedbackConflictResponseDTO> feedbackConflictResponseDTOS;
        try {
            feedbackConflictResponseDTOS = textAssessmentConflictService.checkFeedbackConsistencies(textFeedbackConflictRequestDTOS, exerciseId, 0);
        }
        catch (NetworkingError networkingError) {
            log.error(networkingError.getMessage(), networkingError);
            return;
        }

        // create an array to store conflicts
        List<FeedbackConflict> feedbackConflicts = new ArrayList<>();

        // look for new conflicts
        // Athene may find conflicts with feedback ids that are not in the feedback repository anymore. So check for them. (May happen if the feedback is deleted in Artemis but
        // already stored in Athene)
        feedbackConflictResponseDTOS.forEach(conflict -> {
            Optional<Feedback> firstFeedback = feedbackRepository.findById(conflict.getFirstFeedbackId());
            Optional<Feedback> secondFeedback = feedbackRepository.findById(conflict.getSecondFeedbackId());
            List<FeedbackConflict> storedConflicts = this.feedbackConflictRepository.findConflictsOrDiscardedOnesByFirstAndSecondFeedback(conflict.getFirstFeedbackId(),
                    conflict.getSecondFeedbackId());
            // if the found conflict is present but its type has changed, update it
            if (!storedConflicts.isEmpty() && !storedConflicts.get(0).getType().equals(conflict.getType())) {
                storedConflicts.get(0).setType(conflict.getType());
                feedbackConflicts.add(storedConflicts.get(0));
            }

            // new conflict
            if (firstFeedback.isPresent() && secondFeedback.isPresent() && storedConflicts.isEmpty()) {
                FeedbackConflict feedbackConflict = new FeedbackConflict();
                feedbackConflict.setConflict(true);
                feedbackConflict.setFirstFeedback(firstFeedback.get());
                feedbackConflict.setSecondFeedback(secondFeedback.get());
                feedbackConflict.setType(conflict.getType());
                feedbackConflict.setCreatedAt(ZonedDateTime.now());
                feedbackConflict.setDiscard(false);
                feedbackConflicts.add(feedbackConflict);
            }
        });

        // find solved conflicts and add them to list
        feedbackConflicts.addAll(this.findSolvedConflictsInResponse(textFeedbackConflictRequestDTOS, feedbackConflictResponseDTOS));

        feedbackConflictRepository.saveAll(feedbackConflicts);
    }

    /**
     * Finds and returns the submissions which have the conflicting feedback with the passed feedback
     *
     * @param feedbackId - passed feedback id
     * @return Set of text submissions
     */
    public Set<TextSubmission> getConflictingSubmissions(long feedbackId) {
        List<FeedbackConflict> feedbackConflicts = this.feedbackConflictRepository.findAllWithEagerFeedbackResultAndSubmissionByFeedbackId(feedbackId);
        Set<TextSubmission> textSubmissionSet = feedbackConflicts.stream().map(conflict -> {
            if (conflict.getFirstFeedback().getId() == feedbackId) {
                TextSubmission textSubmission = (TextSubmission) conflict.getSecondFeedback().getResult().getSubmission();
                textSubmission.setResults(List.of(conflict.getSecondFeedback().getResult()));
                return textSubmission;
            }
            else {
                TextSubmission textSubmission = (TextSubmission) conflict.getFirstFeedback().getResult().getSubmission();
                textSubmission.setResults(List.of(conflict.getFirstFeedback().getResult()));
                return textSubmission;
            }
        }).collect(Collectors.toSet());
        final var allTextBlocks = textBlockRepository.findAllBySubmissionIdIn(textSubmissionSet.stream().map(TextSubmission::getId).collect(Collectors.toSet()));
        final var textBlockGroupedBySubmissionId = allTextBlocks.stream().collect(Collectors.groupingBy(block -> block.getSubmission().getId(), Collectors.toSet()));
        textSubmissionSet.forEach(textSubmission -> textSubmission.setBlocks(textBlockGroupedBySubmissionId.get(textSubmission.getId())));
        return textSubmissionSet;
    }

    /**
     * Set feedbackConflict as solved. Done by user marking the conflict as solved.
     *
     * @param feedbackConflict - feedbackConflict to set as solved
     */
    public void solveFeedbackConflict(FeedbackConflict feedbackConflict) {
        feedbackConflict.setSolvedAt(ZonedDateTime.now());
        feedbackConflict.setConflict(false);
        feedbackConflict.setDiscard(true);
        this.feedbackConflictRepository.save(feedbackConflict);
    }

    /**
     * Searches if the feedback that are sent to Athene already have conflicts in the database(storedConflicts),
     * If the stored conflicts are not returned from Athene after the consistency check, it means that they are solved and set as solved.
     *
     * @param textFeedbackConflictRequestDTOS the list sent to Athene for check
     * @param feedbackConflictResponseDTOS returned list with found conflicts.
     * @return solved conflicts
     */
    private List<FeedbackConflict> findSolvedConflictsInResponse(List<TextFeedbackConflictRequestDTO> textFeedbackConflictRequestDTOS,
            List<FeedbackConflictResponseDTO> feedbackConflictResponseDTOS) {
        List<Long> feedbackIds = textFeedbackConflictRequestDTOS.stream().map(TextFeedbackConflictRequestDTO::getFeedbackId).toList();
        List<FeedbackConflict> storedConflicts = this.feedbackConflictRepository.findAllConflictsByFeedbackList(feedbackIds);

        storedConflicts.forEach(conflict -> {
            boolean isPresent = feedbackConflictResponseDTOS.stream().anyMatch(newConflicts -> (newConflicts.getFirstFeedbackId() == conflict.getFirstFeedback().getId()
                    && newConflicts.getSecondFeedbackId() == conflict.getSecondFeedback().getId())
                    || (newConflicts.getFirstFeedbackId() == conflict.getSecondFeedback().getId() && newConflicts.getSecondFeedbackId() == conflict.getFirstFeedback().getId()));
            if (!isPresent) {
                conflict.setConflict(false);
                conflict.setSolvedAt(ZonedDateTime.now());
            }
        });
        // remove the ones that are already in the database.
        storedConflicts.removeIf(FeedbackConflict::getConflict);

        return storedConflicts;
    }
}
