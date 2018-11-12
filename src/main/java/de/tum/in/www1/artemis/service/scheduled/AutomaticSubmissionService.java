package de.tum.in.www1.artemis.service.scheduled;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.ModelingSubmissionService;
import de.tum.in.www1.artemis.service.ParticipationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/*
 * This service handles automatic submission after exercises ended. Currently it is only used for modeling exercises.
 * It manages a hash map with submissions, which stores not submitted submissions. It checks periodically, whether
 * the exercise (the submission belongs to) has ended yet and submits it automatically if that's the case.
 */
@Service
public class AutomaticSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticSubmissionService.class);

    // Map<exerciseId, Map<username, submission>
    // TODO I think we can get rid of this hash map and rather manipulate the existing database objects directly.
    // This would also prevent problems when the server is restarted during a modeling exercise
    private static Map<Long, Map<String, Submission>> submissionHashMap = new ConcurrentHashMap<>();

    private static ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
    static {
        threadPoolTaskScheduler.setThreadNamePrefix("AutomaticSubmission");
        threadPoolTaskScheduler.setPoolSize(3);
        threadPoolTaskScheduler.initialize();
    }

    private ScheduledFuture scheduledFuture;

    private final ExerciseService exerciseService;
    private final ParticipationService participationService;
    private final ModelingSubmissionService modelingSubmissionService;
    private final ModelingSubmissionRepository modelingSubmissionRepository;
    private final SimpMessageSendingOperations messagingTemplate;

    public AutomaticSubmissionService(ExerciseService exerciseService,
                                      ParticipationService participationService,
                                      ModelingSubmissionService modelingSubmissionService,
                                      ModelingSubmissionRepository modelingSubmissionRepository,
                                      SimpMessageSendingOperations messagingTemplate) {
        this.exerciseService = exerciseService;
        this.participationService = participationService;
        this.modelingSubmissionService = modelingSubmissionService;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * start scheduler
     */
    public void startSchedule(long delayInMillis) {
        log.info("AutomaticSubmissionService was started to run repeatedly with {} second gaps.", delayInMillis / 1000.0);
        scheduledFuture = threadPoolTaskScheduler.scheduleWithFixedDelay(this::run, delayInMillis);
    }

    /**
     * stop scheduler (doesn't interrupt if running)
     */
    public void stopSchedule() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }

    /**
     * add a submission to the submissionHashMap
     *
     * @param exerciseId     the exerciseId of the exercise the submission belongs to (first Key)
     * @param username       the username of the user, who submitted the submission (second Key)
     * @param submission     the submission, which should be added (Value)
     */
    public static void updateSubmission(Long exerciseId, String username, Submission submission) {

        if (submission != null && exerciseId != null && username != null) {
            // check if there is already a submission with the same exercise
            if (!submissionHashMap.containsKey(exerciseId)) {
                submissionHashMap.put(exerciseId, new ConcurrentHashMap<>());
            }
            submissionHashMap.get(exerciseId).put(username, submission);
        }
    }

    private void run() {
        // global try-catch for error logging
        try {
            long start = System.currentTimeMillis();

            // update Participations if the submission was submitted or if the exercise has ended and save them to Database (DB Write)
            for (long exerciseId : submissionHashMap.keySet()) {

                Exercise exercise = exerciseService.findOne(exerciseId);
                // check if exercise has been deleted
                if (exercise == null) {
                    submissionHashMap.remove(exerciseId);
                    continue;
                }

                // if exercise has ended, all submissions will be processed => we can remove the inner HashMap for this exercise
                // if exercise hasn't ended, some submissions (those that are not submitted) will stay in HashMap => keep inner HashMap
                Map<String, Submission> submissions;
                if (exercise.isEnded()) {
                    log.debug("exercise {} ended", exercise.getId());
                    submissions = submissionHashMap.remove(exerciseId);
                } else {
                    submissions = submissionHashMap.get(exerciseId);
                }

                int num = handleSubmissions(exercise, submissions);

                if (num > 0) {
                    log.info("Processed {} submissions after {} ms in exercise {}", num, System.currentTimeMillis() - start, exercise.getTitle());
                }
            }
        } catch (Exception e) {
            log.error("Exception in AutomaticSubmissionService:\n{}", e.getMessage());
        }
    }

    /**
     * check if the user submitted the submission or if the exercise has ended:
     * if true: -> Update Participation and save to Database (DB Write)
     * Remove processed Submissions from SubmissionHashMap
     *
     * @param exercise          the exercise which should be checked
     * @param userSubmissionMap a Map with all submissions for the given exercise mapped by the username
     * @return the number of updated participations
     */
    private int handleSubmissions(Exercise exercise, Map<String, Submission> userSubmissionMap) {
        int counter = 0;

        for (String username : userSubmissionMap.keySet()) {
            try {
                // first case: the user submitted the submission
                if (userSubmissionMap.get(username).isSubmitted()) {
                    Submission submission = userSubmissionMap.remove(username);
                    log.debug("user submitted model {}", submission.getId());
                    if (submission.getType() == null) {
                        submission.setType(SubmissionType.MANUAL);
                    }

                    // Update Participation and save to Database (DB Write)
                    // Remove processed Submissions from SubmissionHashMap
                    submission = updateParticipation(submission);
                    if (submission != null) {
                        counter++;
                    }
                // second case: the exercise has ended
                } else if (exercise.isEnded()) {
                    Submission submission = userSubmissionMap.remove(username);
                    log.debug("exercise ended for submission {}", submission.getId());
                    Participation participation = submission.getParticipation();
                    // retrieve submission from the submission's participation to solve lazy loading problem
                    // without it, changes in the submission are not reflected in the participation's submissions
                    for (Submission participationSubmission : participation.getSubmissions()) {
                        if (participationSubmission.equals(submission)) {
                            submission = participationSubmission;
                            break;
                        }
                    }
                    submission.setSubmitted(true);
                    submission.setType(SubmissionType.TIMEOUT);
                    submission.setSubmissionDate(ZonedDateTime.now());

                    // TODO: we should check if there is a result for the submission. If this is not the case, we should create one

                    // Update Participation and save to Database (DB Write)
                    // Remove processed Submissions from SubmissionHashMap
                    submission = updateParticipation(submission);
                    if (submission != null) {
                        messagingTemplate.convertAndSendToUser(username, "/topic/modelingSubmission/" + submission.getId(), submission);
                        counter++;
                    }
                }
            } catch (Exception e) {
                log.error("Exception in handleSubmissions() for {} in exercise {}:\n{}", username, exercise.getId(), e.getMessage());
            }
        }

        return counter;
    }

    /**
     * Updates the participation for a given submission.
     * The participation is set to FINISHED.
     * Currently only handles modeling submissions.
     *
     * @param submission    the submission for which the participation should be updated for
     * @return submission if updating participation successful, otherwise null
     */
    private Submission updateParticipation(Submission submission) {
        if (submission != null) {
            if (submission instanceof ModelingSubmission) {
                // manually trigger notifyCompass for modelingSubmission to update compass
                ModelingSubmission modelingSubmission = (ModelingSubmission) submission;
                Participation participation = modelingSubmission.getParticipation();
                if (participation == null) {
                    log.error("The modeling submission {} has no participation.", modelingSubmission);
                    return null;
                }
                Exercise exercise = participation.getExercise();
                if (exercise instanceof ModelingExercise) {
                    // notify compass about new submission
                    modelingSubmissionService.notifyCompass(modelingSubmission, (ModelingExercise) exercise);
                    // check if compass could assess automatically
                    modelingSubmissionService.checkAutomaticResult(modelingSubmission);
                    String model = modelingSubmission.getModel();
                    // persist modeling submission in DB
                    modelingSubmission = modelingSubmissionRepository.save(modelingSubmission);
                    modelingSubmission.setModel(model);
                    // set participation state to finished and persist it
                    participation.setInitializationState(InitializationState.FINISHED);
                    participationService.save(participation);
                    // return modeling submission with model and result
                    return modelingSubmission;
                } else {
                    log.error("The exercise {} belonging the modeling submission {} is not a ModelingExercise.", exercise.getId(), submission.getId());
                    return null;
                }
            }

            /* TODO: add support for other submission types, e.g.
             * if (submission instanceof TextSubmission) {
             *
             * }
             */
        }
        log.error("Updating the participation for submission {} failed.", submission.getId());
        return null;
    }
}
