package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.service.scheduled.AutomaticSubmissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Service
@Transactional
public class TextSubmissionService {

    private final TextSubmissionRepository textSubmissionRepository;
    private final ParticipationRepository participationRepository;

    public TextSubmissionService(TextSubmissionRepository textSubmissionRepository,
                                 ParticipationRepository participationRepository) {
        this.textSubmissionRepository = textSubmissionRepository;
        this.participationRepository = participationRepository;
    }

    /**
     * Saves the given submission and the corresponding model and creates the result if necessary.
     * Furthermore, the submission is added to the AutomaticSubmissionService if not submitted yet.
     * Is used for creating and updating text submissions.
     * If it is used for a submit action, Compass is notified about the new model.
     * Rolls back if inserting fails - occurs for concurrent createTextSubmission() calls.
     *
     * @param textSubmission the submission to notifyCompass
     * @param textExercise the exercise to notifyCompass in
     * @param participation the participation where the result should be saved
     * @return the textSubmission entity
     */
    @Transactional(rollbackFor = Exception.class)
    public TextSubmission save(TextSubmission textSubmission, TextExercise textExercise, Participation participation) {
        // update submission properties
        textSubmission.setSubmissionDate(ZonedDateTime.now());
        textSubmission.setType(SubmissionType.MANUAL);

        boolean isExampleSubmission = textSubmission.isExampleSubmission() != null && textSubmission.isExampleSubmission();

        // Example submissions do not have a participation
        if (!isExampleSubmission) {
            textSubmission.setParticipation(participation);
        }
        textSubmission = textSubmissionRepository.save(textSubmission);

        // All the following are useful only for real submissions
        if (!isExampleSubmission) {
            participation.addSubmissions(textSubmission);

            User user = participation.getStudent();

            if (textSubmission.isSubmitted()) {
                participation.setInitializationState(InitializationState.FINISHED);
            } else if (textExercise.getDueDate() != null && !textExercise.isEnded()) {
                // save submission to HashMap if exercise not ended yet
                AutomaticSubmissionService.updateSubmission(textExercise.getId(), user.getLogin(), textSubmission);
            }
            Participation savedParticipation = participationRepository.save(participation);
            if (textSubmission.getId() == null) {
                textSubmission = savedParticipation.findLatestTextSubmission();
            }
        }

        return textSubmission;
    }
}
