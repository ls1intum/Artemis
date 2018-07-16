package de.tum.in.www1.artemis.service;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ParticipationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class QuizScheduleService {

    private static final Logger log = LoggerFactory.getLogger(QuizScheduleService.class);

    private static Map<Long, Map<String, Submission>> submissionHashMap = new ConcurrentHashMap<>();
    private static Map<Long, Map<String, Participation>> participationHashMap = new ConcurrentHashMap<>();
    private static Map<Long, Set<Result>> resultHashMap = new ConcurrentHashMap<>();
    private static Map<Long, ScheduledFuture> quizStartSchedules = new ConcurrentHashMap<>();

    private static ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
    static {
        threadPoolTaskScheduler.setThreadNamePrefix("QuizScheduler");
        threadPoolTaskScheduler.setPoolSize(3);
        threadPoolTaskScheduler.initialize();
    }

    private ScheduledFuture scheduledFuture;

    private final SimpMessageSendingOperations messagingTemplate;
    private final ParticipationRepository participationRepository;
    private final UserService userService;
    private final QuizExerciseService quizExerciseService;
    private final StatisticService statisticService;
    private final ExerciseService exerciseService;
    private final ResultRepository resultRepository;
    private final ModelingSubmissionService modelingSubmissionService;

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

    /**
     * add a result to resultHashMap for a statistic-update
     *
     * @param quizId the quizId of the quiz the result belongs to (first Key)
     * @param result the result, which should be added
     */
    public static void addResultToStatistic(Long quizId, Result result) {

        if (quizId != null && result != null) {
            //check if there is already a result with the same quiz
            if (!resultHashMap.containsKey(quizId)) {
                resultHashMap.put(quizId, new HashSet<>());
            }
            resultHashMap.get(quizId).add(result);
        }

    }

    /**
     * add a participation to participationHashMap to send them back to the user when the quiz ends
     *
     * @param exerciseId    the exerciseId of the exercise the result belongs to (first Key)
     * @param participation the result, which should be added
     */
    private static void addParticipation(Long exerciseId, Participation participation) {

        if (exerciseId != null && participation != null) {
            //check if there is already a result with the same quiz
            if (!participationHashMap.containsKey(exerciseId)) {
                participationHashMap.put(exerciseId, new ConcurrentHashMap<>());
            }
            participationHashMap.get(exerciseId).put(participation.getStudent().getLogin(), participation);
        }

    }

    /**
     * get a quizSubmission from the submissionHashMap by quizId and username
     *
     * @param quizId   the quizId of the quiz the submission belongs to (first Key)
     * @param username the username of the user, who submitted the submission (second Key)
     * @return the quizSubmission, with the given quizId and username
     * -> return an empty QuizSubmission if there is no quizSubmission
     * -> return null if the quizId or if the username is null
     */
    public static QuizSubmission getQuizSubmission(Long quizId, String username) {

        if (quizId == null || username == null) {
            return null;
        }
        QuizSubmission quizSubmission;
        //check if the the map contains submissions with the quizId
        if (submissionHashMap.containsKey(quizId)) {
            //return the quizSubmission with the username-Key
            quizSubmission = (QuizSubmission) submissionHashMap.get(quizId).get(username);
            if (quizSubmission != null) {
                return quizSubmission;
            }
        }
        //return an empty quizSubmission if the maps contain no mapping for the keys
        return new QuizSubmission().submittedAnswers(new HashSet<>());
    }

    /**
     * get a participation from the participationHashMap by quizId and username
     *
     * @param quizId   the quizId of the quiz, the participation belongs to (first Key)
     * @param username the username of the user, the participation belongs to (second Key)
     * @return the participation with the given quizId and username
     * -> return null if there is no participation
     * -> return null if the quizId or if the username is null
     */
    public static Participation getParticipation(Long quizId, String username) {
        if (quizId == null || username == null) {
            return null;
        }
        //check if the the map contains participations with the quizId
        if (participationHashMap.containsKey(quizId)) {
            //return the participation with the username-Key
            return participationHashMap.get(quizId).get(username);
        }
        //return null if the maps contain no mapping for the keys
        return null;
    }

    public QuizScheduleService(SimpMessageSendingOperations messagingTemplate,
                               ParticipationRepository participationRepository,
                               UserService userService,
                               QuizExerciseService quizExerciseService,
                               StatisticService statisticService,
                               ExerciseService exerciseService,
                               ResultRepository resultRepository,
                               ModelingSubmissionService modelingSubmissionService) {
        this.messagingTemplate = messagingTemplate;
        this.participationRepository = participationRepository;
        this.userService = userService;
        this.quizExerciseService = quizExerciseService;
        this.statisticService = statisticService;
        this.exerciseService = exerciseService;
        this.resultRepository = resultRepository;
        this.modelingSubmissionService = modelingSubmissionService;
    }

    /**
     * start scheduler
     */
    public void startSchedule(long delayInMillis) {
        log.info("QuizScheduleService was started to run repeatedly with {} second gaps.", delayInMillis / 1000.0);
        scheduledFuture = threadPoolTaskScheduler.scheduleWithFixedDelay(this::run, delayInMillis);

        // schedule quiz start for all existing quizzes that are planned to start in the future
        List<QuizExercise> quizExercises = quizExerciseService.findAllPlannedToStartInTheFutureWithQuestions();
        for (QuizExercise quizExercise : quizExercises) {
            scheduleQuizStart(quizExercise);
        }
    }

    /**
     * stop scheduler (doesn't interrupt if running)
     */
    public void stopSchedule() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        for (Long quizId : quizStartSchedules.keySet()) {
            cancelScheduledQuizStart(quizId);
        }
    }

    public void scheduleQuizStart(final QuizExercise quizExercise) {
        // first remove and cancel old scheduledFuture if it exists
        cancelScheduledQuizStart(quizExercise.getId());

        if (quizExercise.isIsPlannedToStart() && quizExercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
            // schedule sending out filtered quiz over websocket
            ScheduledFuture scheduledFuture = threadPoolTaskScheduler.schedule(
                () -> quizExerciseService.sendQuizExerciseToSubscribedClients(quizExercise),
                Date.from(quizExercise.getReleaseDate().toInstant())
            );

            // save scheduled future in HashMap
            quizStartSchedules.put(quizExercise.getId(), scheduledFuture);
        }
    }

    public void cancelScheduledQuizStart(Long quizId) {
        ScheduledFuture scheduledFuture = quizStartSchedules.remove(quizId);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }

    public void clearQuizData(Long quizId) {
        // delete all participation, submission, and result hashmap entries that correspond to this quiz
        participationHashMap.remove(quizId);
        submissionHashMap.remove(quizId);
        resultHashMap.remove(quizId);
    }

    /**
     * 1. Check SubmissionHashMap for new submissions with “isSubmitted() == true”
     * a. Process each Submission (set submissionType to “SubmissionType.MANUAL”) and create Participation and Result and save them to Database (DB Write)
     * b. Remove processed Submissions from SubmissionHashMap and write Participation with Result into ParticipationHashMap and write Result into ResultHashMap
     * 2. If Quiz has ended:
     * a. Process all Submissions in SubmissionHashMap that belong to this quiz
     * i. set “isSubmitted” to “true” and submissionType to “SubmissionType.TIMEOUT”
     * ii. Create Participation and Result and save to Database (DB Write)
     * iii. Remove processed Submissions from SubmissionHashMap and write Participations with Result into ParticipationHashMap and Results into ResultHashMap
     * b. Send out Participations (including QuizExercise and Result) from ParticipationHashMap via WebSocket to each user and remove them from ParticipationHashMap (WebSocket Send)
     * 3. Update Statistics with Results from ResultHashMap (DB Read and DB Write) and remove from ResultHashMap
     * 4. Send out new Statistics over WebSocket (WebSocket Send)
     */
    private void run() {
        // global try-catch for error logging
        try {
            long start = System.currentTimeMillis();

            //create Participations and Results if the submission was submitted or if the quiz has ended and save them to Database (DB Write)
            for (long exerciseId : submissionHashMap.keySet()) {

                Exercise exercise = exerciseService.findOne(exerciseId);
                if (exercise instanceof QuizExercise) {
                    exercise = quizExerciseService.findOneWithQuestions(exerciseId);
                }
                // check if exercise has been deleted
                if (exercise == null) {
                    submissionHashMap.remove(exerciseId);
                    continue;
                }

                // if exercise has ended, all submissions will be processed => we can remove the inner HashMap for this quiz
                // if exercise hasn't ended, some submissions (those that are not submitted) will stay in HashMap => keep inner HashMap
                Map<String, Submission> submissions;
                if (((SchedulableExercise) exercise).isEnded()) {
                    submissions = submissionHashMap.remove(exerciseId);
                } else {
                    submissions = submissionHashMap.get(exerciseId);
                }

                int num = createParticipations(exercise, submissions);

                log.info("Processed {} submissions after {} ms in exercise {}", num, System.currentTimeMillis() - start, exercise.getTitle());
            }

            // Send out Participations from ParticipationHashMap to each user if the quiz has ended
            for (long exerciseId : participationHashMap.keySet()) {

                // get the Quiz without the statistics and questions from the database
                Exercise exercise = exerciseService.findOne(exerciseId);
                // check if quiz has been deleted
                if (exercise == null) {
                    participationHashMap.remove(exerciseId);
                    continue;
                }

                // check if the quiz has ended
                if (((SchedulableExercise) exercise).isEnded()) {
                    // send the participation with containing result and quiz back to the users via websocket
                    //      and remove the participation from the ParticipationHashMap
                    int counter = 0;
                    for (Participation participation : participationHashMap.remove(exerciseId).values()) {
                        if (participation.getStudent() == null || participation.getStudent().getLogin() == null) {
                            log.error("Participation is missing student (or student is missing username): {}", participation);
                            continue;
                        }
                        if (exercise instanceof QuizExercise) {
                            messagingTemplate.convertAndSendToUser(participation.getStudent().getLogin(), "/topic/quizExercise/" + exerciseId + "/participation", participation);
                            counter++;
                        }
                    }
                    log.info("Sent out {} participations after {} ms for quiz {}", counter, System.currentTimeMillis() - start, exercise.getTitle());
                }
            }

            //Update Statistics with Results from ResultHashMap (DB Read and DB Write) and remove from ResultHashMap
            for (long quizId : resultHashMap.keySet()) {

                // get the Quiz with the statistic from the database
                QuizExercise quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(quizId);
                // check if quiz has been deleted
                if (quizExercise == null) {
                    resultHashMap.remove(quizId);
                    continue;
                }

                // update statistic with all results of the quizExercise
                try {
                    statisticService.updateStatistics(resultHashMap.remove(quizId), quizExercise);
                    log.info("Updated statistics after {} ms for quiz {}", System.currentTimeMillis() - start, quizExercise.getTitle());
                } catch (Exception e) {
                    log.error("Exception in StatisticService.updateStatistics():\n{}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Exception in Quiz Schedule:\n{}", e.getMessage());
        }
    }

    /**
     * check if the user submitted the submission or if the exercise has ended:
     * if true: -> Create Participation and Result and save to Database (DB Write)
     * Remove processed Submissions from SubmissionHashMap and write Participations with Result into ParticipationHashMap and Results into ResultHashMap
     *
     * @param exercise          the exercise which should be checked
     * @param userSubmissionMap a Map with all submissions for the given exercise mapped by the username
     * @return the number of created participations
     */
    private int createParticipations(Exercise exercise, Map<String, Submission> userSubmissionMap) {
        int counter = 0;

        for (String username : userSubmissionMap.keySet()) {
            try {
                // first case: the user submitted the quizSubmission
                if (userSubmissionMap.get(username).isSubmitted()) {
                    Submission submission = userSubmissionMap.remove(username);
                    if (submission.getType() == null) {
                        submission.setType(SubmissionType.MANUAL);
                    }

                    if (exercise instanceof QuizExercise) {
                        // Create Participation and Result and save to Database (DB Write)
                        // Remove processed Submissions from SubmissionHashMap and write Participations with Result into ParticipationHashMap and Results into ResultHashMap
                        createParticipationWithResultAndWriteItInHashMaps((QuizExercise) exercise, username, (QuizSubmission) submission);
                    } else if (exercise instanceof ModelingExercise) {
                        // Update Participation and Result and save to Database (DB Write)
                        // Remove processed Submissions from SubmissionHashMap and write Participations into ParticipationHashMap
                        updateParticipation(exercise, submission);
                    }
                    counter++;
                    // second case: the quiz has ended
                } else if (((SchedulableExercise) exercise).isEnded()) {
                    Submission submission = userSubmissionMap.remove(username);
                    submission.setSubmitted(true);
                    submission.setType(SubmissionType.TIMEOUT);
                    submission.setSubmissionDate(ZonedDateTime.now());

                    if (exercise instanceof QuizExercise) {
                        // Create Participation and Result and save to Database (DB Write)
                        // Remove processed Submissions from SubmissionHashMap and write Participations with Result into ParticipationHashMap and Results into ResultHashMap
                        createParticipationWithResultAndWriteItInHashMaps((QuizExercise) exercise, username, (QuizSubmission) submission);
                    } else if (exercise instanceof ModelingExercise) {
                        // Update Participation and Result and save to Database (DB Write)
                        // Remove processed Submissions from SubmissionHashMap and write Participations into ParticipationHashMap
                        Participation participation = updateParticipation(exercise, submission);
                        if (participation != null) {
                            // manually trigger submit for modelingSubmission to update compass
                            ModelingSubmission modelingSubmission = (ModelingSubmission) submission;
                            if (modelingSubmission.getModel() == "") {
                                JsonObject model = modelingSubmissionService.getModel(exercise.getId(), participation.getStudent().getId(), modelingSubmission.getId());
                                modelingSubmission.setModel(model.toString());
                            }
                            modelingSubmissionService.submit(modelingSubmission, (ModelingExercise) exercise);
                        }
                    }
                    counter++;
                }
            } catch (Exception e) {
                log.error("Exception in createParticipations() for {} in exercise {}:\n{}", username, exercise.getId(), e.getMessage());
            }
        }

        return counter;
    }

    /**
     * create Participation and Result if the submission was submitted or if the quiz has ended and save them to Database (DB Write)
     *
     * @param quizExercise   the quizExercise the quizSubmission belongs to
     * @param username       the user, who submitted the quizSubmission
     * @param quizSubmission the quizSubmission, which is used to calculate the Result
     */
    private Participation createParticipationWithResultAndWriteItInHashMaps(QuizExercise quizExercise, String username, QuizSubmission quizSubmission) {

        if (quizExercise != null && username != null && quizSubmission != null) {

            // update submission with score
            quizSubmission.calculateAndUpdateScores(quizExercise);

            //create and save new participation
            Participation participation = new Participation();
            Optional<User> user = userService.getUserByLogin(username);
            if (user.isPresent()) {
                participation.setStudent(user.get());
            }
            //add the quizExercise to the participation
            participation.setExercise(quizExercise);

            // create new result
            Result result = new Result().participation(participation).submission(quizSubmission);
            result.setRated(true);
            result.setCompletionDate(quizSubmission.getSubmissionDate());
            result.setSubmission(quizSubmission);

            // calculate scores and update result and submission accordingly
            quizSubmission.calculateAndUpdateScores(quizExercise);
            result.evaluateSubmission();

            // add result to participation
            participation.addResult(result);
            participation.setInitializationState(ParticipationState.FINISHED);

            //save participation with result and quizSubmission
            participationRepository.save(participation);

            participation.setExercise(quizExercise);
            //add the participation to the participationHashMap for the send out at the end of the quiz
            QuizScheduleService.addParticipation(quizExercise.getId(), participation);
            //add the result of the participation resultHashMap for the statistic-Update
            QuizScheduleService.addResultToStatistic(quizExercise.getId(), result);

            return participation;
        }
        return null;
    }

    private Participation updateParticipation(Exercise exercise, Submission submission) {
        if (submission != null) {
            Optional<Result> optionalResult = resultRepository.findDistinctBySubmissionId(submission.getId());
            if (optionalResult.isPresent()) {
                Result result = optionalResult.get();
                result.setSubmission(submission);
                resultRepository.save(result);

                Participation participation = result.getParticipation();
                participation.setInitializationState(ParticipationState.FINISHED);
                participationRepository.save(participation);

                //add the participation to the participationHashMap
                QuizScheduleService.addParticipation(exercise.getId(), participation);
                return participation;
            }
        }
        return null;
    }
}
