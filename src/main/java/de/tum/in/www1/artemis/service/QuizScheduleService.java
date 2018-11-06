package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.QuizSubmissionRepository;
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

    private static Map<Long, Map<String, QuizSubmission>> submissionHashMap = new ConcurrentHashMap<>();
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
    private final ResultRepository resultRepository;
    private final QuizSubmissionRepository quizSubmissionRepository;
    private final UserService userService;
    private final QuizExerciseService quizExerciseService;
    private final StatisticService statisticService;


    public QuizScheduleService(SimpMessageSendingOperations messagingTemplate,
                               ParticipationRepository participationRepository,
                               ResultRepository resultRepository,
                               QuizSubmissionRepository quizSubmissionRepository,
                               UserService userService,
                               QuizExerciseService quizExerciseService,
                               StatisticService statisticService) {
        this.messagingTemplate = messagingTemplate;
        this.participationRepository = participationRepository;
        this.resultRepository = resultRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.userService = userService;
        this.quizExerciseService = quizExerciseService;
        this.statisticService = statisticService;
    }


    /**
     * add a quizSubmission to the submissionHashMap
     *
     * @param quizId         the quizId of the quiz the submission belongs to (first Key)
     * @param username       the username of the user, who submitted the submission (second Key)
     * @param quizSubmission the quizSubmission, which should be added (Value)
     */
    public static void updateSubmission(Long quizId, String username, QuizSubmission quizSubmission) {

        if (quizSubmission != null && quizId != null && username != null) {
            //check if there is already a quizSubmission with the same quiz
            if (!submissionHashMap.containsKey(quizId)) {
                submissionHashMap.put(quizId, new ConcurrentHashMap<>());
            }
            submissionHashMap.get(quizId).put(username, quizSubmission);
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
     * @param quizId        the quizId of the quiz the result belongs to (first Key)
     * @param participation the result, which should be added
     */
    private static void addParticipation(Long quizId, Participation participation) {

        if (quizId != null && participation != null) {
            //check if there is already a result with the same quiz
            if (!participationHashMap.containsKey(quizId)) {
                participationHashMap.put(quizId, new ConcurrentHashMap<>());
            }
            participationHashMap.get(quizId).put(participation.getStudent().getLogin(), participation);
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
            quizSubmission = submissionHashMap.get(quizId).get(username);
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
            for (long quizId : submissionHashMap.keySet()) {

                QuizExercise quizExercise = quizExerciseService.findOneWithQuestions(quizId);
                // check if quiz has been deleted
                if (quizExercise == null) {
                    submissionHashMap.remove(quizId);
                    continue;
                }

                // if quiz has ended, all submissions will be processed => we can remove the inner HashMap for this quiz
                // if quiz hasn't ended, some submissions (those that are not submitted) will stay in HashMap => keep inner HashMap
                Map<String, QuizSubmission> submissions;
                if (quizExercise.isEnded()) {
                    submissions = submissionHashMap.remove(quizId);
                } else {
                    submissions = submissionHashMap.get(quizId);
                }

                int num = createParticipations(quizExercise, submissions);

                if (num > 0) {
                    log.info("Processed {} submissions after {} ms in quiz {}", num, System.currentTimeMillis() - start, quizExercise.getTitle());
                }
            }

            // Send out Participations from ParticipationHashMap to each user if the quiz has ended
            for (long quizId : participationHashMap.keySet()) {

                // get the Quiz without the statistics and questions from the database
                QuizExercise quizExercise = quizExerciseService.findOne(quizId);
                // check if quiz has been deleted
                if (quizExercise == null) {
                    participationHashMap.remove(quizId);
                    continue;
                }

                // check if the quiz has ended
                if (quizExercise.isEnded()) {
                    // send the participation with containing result and quiz back to the users via websocket
                    //      and remove the participation from the ParticipationHashMap
                    int counter = 0;
                    for (Participation participation : participationHashMap.remove(quizId).values()) {
                        if (participation.getStudent() == null || participation.getStudent().getLogin() == null) {
                            log.error("Participation is missing student (or student is missing username): {}", participation);
                            continue;
                        }
                        messagingTemplate.convertAndSendToUser(participation.getStudent().getLogin(), "/topic/quizExercise/" + quizId + "/participation", participation);
                        counter++;
                    }
                    if (counter > 0) {
                        log.info("Sent out {} participations after {} ms for quiz {}", counter, System.currentTimeMillis() - start, quizExercise.getTitle());
                    }
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
                    log.debug("Updated statistics after {} ms for quiz {}", System.currentTimeMillis() - start, quizExercise.getTitle());
                } catch (Exception e) {
                    log.error("Exception in StatisticService.updateStatistics():\n{}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Exception in Quiz Schedule:\n{}", e.getMessage());
        }
    }

    /**
     * check if the user submitted the submission or if the quiz has ended:
     * if true: -> Create Participation and Result and save to Database (DB Write)
     * Remove processed Submissions from SubmissionHashMap and write Participations with Result into ParticipationHashMap and Results into ResultHashMap
     *
     * @param quizExercise      the quiz which should be checked
     * @param userSubmissionMap a Map with all submissions for the given quizExercise mapped by the username
     * @return the number of created participations
     */
    private int createParticipations(QuizExercise quizExercise, Map<String, QuizSubmission> userSubmissionMap) {
        int counter = 0;

        for (String username : userSubmissionMap.keySet()) {
            try {
                // first case: the user submitted the quizSubmission
                if (userSubmissionMap.get(username).isSubmitted()) {
                    QuizSubmission quizSubmission = userSubmissionMap.remove(username);
                    if (quizSubmission.getType() == null) {
                        quizSubmission.setType(SubmissionType.MANUAL);
                    }

                    // Create Participation and Result and save to Database (DB Write)
                    // Remove processed Submissions from SubmissionHashMap and write Participations with Result into ParticipationHashMap and Results into ResultHashMap
                    createParticipationWithResultAndWriteItInHashMaps(quizExercise, username, quizSubmission);
                    counter++;
                    // second case: the quiz has ended
                } else if (quizExercise.isEnded()) {
                    QuizSubmission quizSubmission = userSubmissionMap.remove(username);
                    quizSubmission.setSubmitted(true);
                    quizSubmission.setType(SubmissionType.TIMEOUT);
                    quizSubmission.setSubmissionDate(ZonedDateTime.now());

                    // Create Participation and Result and save to Database (DB Write)
                    // Remove processed Submissions from SubmissionHashMap and write Participations with Result into ParticipationHashMap and Results into ResultHashMap
                    createParticipationWithResultAndWriteItInHashMaps(quizExercise, username, quizSubmission);
                    counter++;
                }
            } catch (Exception e) {
                log.error("Exception in createParticipations() for {} in quiz {}:\n{}", username, quizExercise.getId(), e.getMessage());
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
            result.setAssessmentType(AssessmentType.AUTOMATIC);
            result.setCompletionDate(quizSubmission.getSubmissionDate());
            result.setSubmission(quizSubmission);

            // calculate scores and update result and submission accordingly
            quizSubmission.calculateAndUpdateScores(quizExercise);
            result.evaluateSubmission();

            // add result to participation
            participation.addResult(result);

            // add submission to participation
            participation.addSubmissions(quizSubmission);

            participation.setInitializationState(InitializationState.FINISHED);
            participation.setExercise(quizExercise);

            //save participation, result and quizSubmission
            participationRepository.save(participation);
            quizSubmissionRepository.save(quizSubmission);
            resultRepository.save(result);

            //add the participation to the participationHashMap for the send out at the end of the quiz
            QuizScheduleService.addParticipation(quizExercise.getId(), participation);
            //add the result of the participation resultHashMap for the statistic-Update
            QuizScheduleService.addResultToStatistic(quizExercise.getId(), result);

            return participation;
        }
        return null;
    }
}
