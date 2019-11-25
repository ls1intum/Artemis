package de.tum.in.www1.artemis.service;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class TextSubmissionService extends SubmissionService<TextSubmission, TextSubmissionRepository> {

    private final Optional<TextAssessmentQueueService> textAssessmentQueueService;

    public TextSubmissionService(TextSubmissionRepository textSubmissionRepository, SubmissionRepository submissionRepository,
            StudentParticipationRepository studentParticipationRepository, ParticipationService participationService, ResultRepository resultRepository, UserService userService,
            Optional<TextAssessmentQueueService> textAssessmentQueueService, SimpMessageSendingOperations messagingTemplate, AuthorizationCheckService authCheckService,
            ResultService resultService) {
        super(submissionRepository, userService, authCheckService, resultRepository, participationService, messagingTemplate, studentParticipationRepository,
                textSubmissionRepository, resultService);
        this.textAssessmentQueueService = textAssessmentQueueService;
    }

    /**
     * Handles text submissions sent from the client and saves them in the database.
     *
     * @param textSubmission the text submission that should be saved
     * @param textExercise   the corresponding text exercise
     * @param principal      the user principal
     * @return the saved text submission
     */
    public TextSubmission handleTextSubmission(TextSubmission textSubmission, TextExercise textExercise, Principal principal) {
        if (textSubmission.isExampleSubmission() == Boolean.TRUE) {
            textSubmission = save(textSubmission);
        }
        else {
            textSubmission = save(textSubmission, textExercise, principal.getName(), TextSubmission.class);
        }
        return textSubmission;
    }

    /**
     * The same as `save()`, but without participation, is used by example submission, which aren't linked to any participation
     *
     * @param textSubmission the submission to notifyCompass
     * @return the textSubmission entity
     */
    @Transactional(rollbackFor = Exception.class)
    public TextSubmission save(TextSubmission textSubmission) {
        textSubmission.setSubmissionDate(ZonedDateTime.now());
        textSubmission.setType(SubmissionType.MANUAL);

        // Rebuild connection between result and submission, if it has been lost, because hibernate needs it
        if (textSubmission.getResult() != null && textSubmission.getResult().getSubmission() == null) {
            textSubmission.getResult().setSubmission(textSubmission);
        }

        textSubmission = genericSubmissionRepository.save(textSubmission);

        return textSubmission;
    }

    /**
     * Given an exercise id, find a random text submission for that exercise which still doesn't have any manual result. No manual result means that no user has started an
     * assessment for the corresponding submission yet.
     *
     * @param textExercise the exercise for which we want to retrieve a submission without manual result
     * @return a textSubmission without any manual result or an empty Optional if no submission without manual result could be found
     */
    public Optional<TextSubmission> getSubmissionWithoutManualResult(TextExercise textExercise) {
        if (textExercise.isAutomaticAssessmentEnabled() && textAssessmentQueueService.isPresent()) {
            return textAssessmentQueueService.get().getProposedTextSubmission(textExercise);
        }
        return super.getSubmissionWithoutManualResult(textExercise, TextSubmission.class);
    }

    /**
     * Return all TextSubmission which are the latest TextSubmission of a Participation and doesn't have a Result so far
     * The corresponding TextBlocks and Participations are retrieved from the database
     * @param exercise Exercise for which all assessed submissions should be retrieved
     * @return List of all TextSubmission which aren't assessed at the Moment, but need assessment in the future.
     *
     */
    public List<TextSubmission> getAllOpenTextSubmissions(TextExercise exercise) {
        return genericSubmissionRepository.findByParticipation_ExerciseIdAndResultIsNullAndSubmittedIsTrue(exercise.getId()).stream()
                .filter(tS -> tS.getParticipation().findLatestSubmission().isPresent() && tS == tS.getParticipation().findLatestSubmission().get()).collect(Collectors.toList());
    }

    /**
     * Get a text submission of the given exercise that still needs to be assessed and lock the submission to prevent other tutors from receiving and assessing it.
     *
     * @param textExercise the exercise the submission should belong to
     * @return a locked modeling submission that needs an assessment
     */
    public TextSubmission getLockedTextSubmissionWithoutResult(TextExercise textExercise) {
        TextSubmission textSubmission = getSubmissionWithoutManualResult(textExercise)
                .orElseThrow(() -> new EntityNotFoundException("Text submission for exercise " + textExercise.getId() + " could not be found"));
        super.lockSubmission(textSubmission);
        return textSubmission;
    }
}
