package de.tum.in.www1.artemis.service.scheduled;

import java.text.DecimalFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ModelingSubmissionService;
import de.tum.in.www1.artemis.service.ParticipationService;

/*
 * This service handles automatic submission after exercises ended. Currently it is only used for modeling exercises. It manages a hash map with submissions, which stores not
 * submitted submissions. It checks periodically, whether the exercise (the submission belongs to) has ended yet and submits it automatically if that's the case.
 */
@Service
public class AutomaticSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticSubmissionService.class);

    // TODO Rework this service to manipulate unsubmitted submission objects for text and modeling exercises in the database directly without the use of hash maps
    // 1) using a cron job, we get all text submissions and modeling submissions from the database with submitted = false
    // 2) we check for each submission whether the corresponding exercise has finished (i.e. due date < now)
    // 3a) if yes, we set the submission to submitted = true (without changing the submission date). We also set submissionType to TIMEOUT
    // 3b) if no, we ignore the submission
    // ==> This will prevent problems when the server is restarted during a modeling / text exercise

    private final ParticipationService participationService;

    private final ModelingSubmissionService modelingSubmissionService;

    private final SubmissionRepository submissionRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    public AutomaticSubmissionService(ParticipationService participationService, ModelingSubmissionService modelingSubmissionService, SubmissionRepository submissionRepository,
            SimpMessageSendingOperations messagingTemplate) {
        this.participationService = participationService;
        this.modelingSubmissionService = modelingSubmissionService;
        this.submissionRepository = submissionRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Check for every un-submitted modeling and text submission if the corresponding exercise has finished (i.e. due date < now) and the submission was saved before the exercise
     * due date.
     * - If yes, we set the submission to submitted = true (without changing the submission date) and the submissionType to TIMEOUT. We also set the initialization state of the
     * corresponding participation to FINISHED.
     * - If no, we ignore the submission. This is executed every night at 1:00:00 am by the cron job.
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void run() {
        // global try-catch for error logging
        try {
            // used for calculating the elapsed time
            long start = System.nanoTime();

            List<Submission> unsubmittedSubmissions = submissionRepository.findAllUnsubmittedModelingAndTextSubmissions();
            for (Submission unsubmittedSubmission : unsubmittedSubmissions) {

                Exercise exercise = unsubmittedSubmission.getParticipation().getExercise();

                if (!exercise.isEnded() || unsubmittedSubmission.getSubmissionDate() == null || unsubmittedSubmission.getSubmissionDate().isAfter(exercise.getDueDate())) {
                    continue;
                }

                unsubmittedSubmission.setSubmitted(true);
                unsubmittedSubmission.setType(SubmissionType.TIMEOUT);

                updateParticipation(unsubmittedSubmission);

                submissionRepository.save(unsubmittedSubmission);

                if (unsubmittedSubmission instanceof ModelingSubmission) {
                    notifyCompassAboutNewModelingSubmission((ModelingSubmission) unsubmittedSubmission, (ModelingExercise) exercise);
                }

                StudentParticipation studentParticipation = (StudentParticipation) unsubmittedSubmission.getParticipation();
                String username = studentParticipation.getStudent().getLogin();
                if (unsubmittedSubmission instanceof ModelingSubmission) {
                    messagingTemplate.convertAndSendToUser(username, "/topic/modelingSubmission/" + unsubmittedSubmission.getId(), unsubmittedSubmission);
                }
                if (unsubmittedSubmission instanceof TextSubmission) {
                    messagingTemplate.convertAndSendToUser(username, "/topic/textSubmission/" + unsubmittedSubmission.getId(), unsubmittedSubmission);
                }
            }

            // used for calculating the elapsed time
            long end = System.nanoTime();
            // calculate elapsed time in seconds and create log message
            double elapsedTimeInSeconds = (double) (end - start) / 1000000000.0;
            DecimalFormat df = new DecimalFormat("#.##");
            log.info("Checked {} submissions in {} seconds for automatic submit.", unsubmittedSubmissions.size(), df.format(elapsedTimeInSeconds));
        }
        catch (Exception ex) {
            log.error("Exception in AutomaticSubmissionService:\n{}", ex.getMessage(), ex);
        }
    }

    /**
     * Updates the participation for a given submission. The participation is set to FINISHED.
     *
     * @param submission the submission for which the participation should be updated for
     * @return submission if updating participation successful, otherwise null
     */
    private Submission updateParticipation(Submission submission) {
        if (submission != null) {
            StudentParticipation studentParticipation = (StudentParticipation) submission.getParticipation();
            if (studentParticipation == null) {
                log.error("The submission {} has no participation.", submission);
                return null;
            }

            studentParticipation.setInitializationState(InitializationState.FINISHED);

            participationService.save(studentParticipation);

            return submission;
        }
        return null;
    }

    /**
     * Notify Compass about the new modeling submission. Compass will then try to automatically assess the submission.
     *
     * @param modelingSubmission the new modeling submission Compass should be notified about
     * @param modelingExercise   the modeling exercise to which the submission belongs to
     */
    private void notifyCompassAboutNewModelingSubmission(ModelingSubmission modelingSubmission, ModelingExercise modelingExercise) {
        try {
            // set Authentication object to prevent authentication error "Authentication object cannot be null" - see JavaDoc of setAuthorizationObject method for further details
            SecurityUtils.setAuthorizationObject();
            modelingSubmissionService.notifyCompass(modelingSubmission, modelingExercise);
        }
        catch (Exception ex) {
            log.error("Exception while notifying Compass about a new (automatic) submission:\n{}", ex.getMessage(), ex);
        }
    }
}
