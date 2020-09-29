package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.toList;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.AssessmentConflict;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.repository.AssessmentConflictRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.service.connectors.TextAssessmentConflictService;
import de.tum.in.www1.artemis.service.dto.AssessmentConflictResponseDTO;
import de.tum.in.www1.artemis.service.dto.TextAssessmentConflictRequestDTO;

@Service
@Profile("automaticText")
public class AutomaticTextAssessmentConflictService {

    private final Logger log = LoggerFactory.getLogger(AutomaticTextAssessmentConflictService.class);

    private final AssessmentConflictRepository assessmentConflictRepository;

    private final FeedbackRepository feedbackRepository;

    private final TextBlockRepository textBlockRepository;

    private final TextAssessmentConflictService textAssessmentConflictService;

    public AutomaticTextAssessmentConflictService(AssessmentConflictRepository assessmentConflictRepository, FeedbackRepository feedbackRepository,
            TextBlockRepository textBlockRepository, TextAssessmentConflictService textAssessmentConflictService) {
        this.assessmentConflictRepository = assessmentConflictRepository;
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
        final List<AssessmentConflictResponseDTO> assessmentConflictResponseDTOS;
        try {
            assessmentConflictResponseDTOS = textAssessmentConflictService.checkFeedbackConsistencies(textAssessmentConflictRequestDTOS, exerciseId, 0);
        }
        catch (NetworkingError networkingError) {
            log.error(networkingError.getMessage(), networkingError);
            return;
        }

        // create an array to store conflicts
        List<AssessmentConflict> assessmentConflicts = new ArrayList<>();

        // look for new conflicts
        // Athene may find conflicts with feedback ids that are not in the feedback repository any more. So check for them. (May happen if the feedback is deleted in Artemis but
        // already stored in Athene)
        assessmentConflictResponseDTOS.forEach(conflict -> {
            Optional<Feedback> firstFeedback = feedbackRepository.findById(conflict.getFirstFeedbackId());
            Optional<Feedback> secondFeedback = feedbackRepository.findById(conflict.getSecondFeedbackId());
            List<AssessmentConflict> storedConflicts = this.assessmentConflictRepository.findByFirstAndSecondFeedback(conflict.getFirstFeedbackId(),
                    conflict.getSecondFeedbackId());
            // if the found conflict is present but its type has changed, update it
            if (!storedConflicts.isEmpty() && !storedConflicts.get(0).getType().equals(conflict.getType())) {
                storedConflicts.get(0).setType(conflict.getType());
                assessmentConflicts.add(storedConflicts.get(0));
            }

            // new conflict
            if (firstFeedback.isPresent() && secondFeedback.isPresent() && storedConflicts.isEmpty()) {
                AssessmentConflict assessmentConflict = new AssessmentConflict();
                assessmentConflict.setConflict(true);
                assessmentConflict.setFirstFeedback(firstFeedback.get());
                assessmentConflict.setSecondFeedback(secondFeedback.get());
                assessmentConflict.setType(conflict.getType());
                assessmentConflict.setCreatedAt(ZonedDateTime.now());
                assessmentConflicts.add(assessmentConflict);
            }
        });

        // find solved conflicts and add them to list
        assessmentConflicts.addAll(this.findSolvedConflicts(textAssessmentConflictRequestDTOS, assessmentConflictResponseDTOS));

        assessmentConflictRepository.saveAll(assessmentConflicts);
    }

    /**
     * Searches if the feedback that are sent to Athene already have conflicts in the database(storedConflicts),
     * If the stored conflicts are not returned from Athene after the consistency check, it means that they are solved and set as solved.
     *
     * @param textAssessmentConflictRequestDTOS the list sent to Athene for check
     * @param assessmentConflictResponseDTOS returned list with found conflicts.
     * @return solved conflicts
     */
    private List<AssessmentConflict> findSolvedConflicts(List<TextAssessmentConflictRequestDTO> textAssessmentConflictRequestDTOS,
            List<AssessmentConflictResponseDTO> assessmentConflictResponseDTOS) {
        List<Long> feedbackIds = textAssessmentConflictRequestDTOS.stream().map(TextAssessmentConflictRequestDTO::getFeedbackId).collect(toList());
        List<AssessmentConflict> storedConflicts = this.assessmentConflictRepository.findAllByFeedbackList(feedbackIds);

        storedConflicts.forEach(conflict -> {
            boolean isPresent = assessmentConflictResponseDTOS.stream().anyMatch(newConflicts -> (newConflicts.getFirstFeedbackId() == conflict.getFirstFeedback().getId()
                    && newConflicts.getSecondFeedbackId() == conflict.getSecondFeedback().getId())
                    || (newConflicts.getFirstFeedbackId() == conflict.getSecondFeedback().getId() && newConflicts.getSecondFeedbackId() == conflict.getFirstFeedback().getId()));
            if (!isPresent) {
                conflict.setConflict(false);
                conflict.setSolvedAt(ZonedDateTime.now());
            }
        });
        // remove the ones that are already in the database.
        storedConflicts.removeIf(AssessmentConflict::getConflict);

        return storedConflicts;
    }
}
