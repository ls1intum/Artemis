package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.config.Constants;
import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.domain.enumeration.SubmissionType;
import de.tum.in.www1.exerciseapp.repository.ParticipationRepository;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import de.tum.in.www1.exerciseapp.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class QuizScheduleService {

    private static Map<Long, Map<String, QuizSubmission>> submissionHashMap = new ConcurrentHashMap<>();
    private static Map<Long, Set<Participation>> participationHashMap = new ConcurrentHashMap<>();
    private static Map<Long, Set<Result>> resultHashMap = new ConcurrentHashMap<>();

    private ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
    private ScheduledFuture<?> scheduledFuture;

    private final SimpMessageSendingOperations messagingTemplate;
    private final ParticipationRepository participationRepository;
    private final ResultRepository resultRepository;
    private final UserRepository userRepository;
    private final QuizExerciseService quizExerciseService;
    private final StatisticService statisticService;

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
                participationHashMap.put(quizId, new HashSet<>());
            }
            participationHashMap.get(quizId).add(participation);
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

    public QuizScheduleService(SimpMessageSendingOperations messagingTemplate,
                               ParticipationRepository participationRepository,
                               ResultRepository resultRepository,
                               UserRepository userRepository,
                               QuizExerciseService quizExerciseService,
                               StatisticService statisticService) {
        this.messagingTemplate = messagingTemplate;
        this.participationRepository = participationRepository;
        this.userRepository = userRepository;
        this.resultRepository = resultRepository;
        this.quizExerciseService = quizExerciseService;
        this.statisticService = statisticService;

        threadPoolTaskScheduler.setThreadNamePrefix("QuizScheduler");
        threadPoolTaskScheduler.initialize();
    }

    /**
     * start scheduler
     */
    public void startSchedule() {
        scheduledFuture = threadPoolTaskScheduler.scheduleWithFixedDelay(this::run, 5000);

    }

    /**
     * stop scheduler
     * doen't interrupt if running
     */
    public void stopSchedule() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
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

        //create Participations and Results if the submission was submitted or if the quiz has ended and save them to Database (DB Write)
        for (long quizId : submissionHashMap.keySet()) {

            QuizExercise quizExercise = quizExerciseService.findOneWithQuestions(quizId);

            createParticipations(quizExercise, submissionHashMap.get(quizId));

        }

        // Send out Participations from ParticipationHashMap to each user if the quiz has ended
        for (long quizId : participationHashMap.keySet()) {

            // get the Quiz without the statistics and questions from the database
            QuizExercise quizExercise = quizExerciseService.findOne(quizId);

            // check if the quiz has ended
            if (quizExercise.getRemainingTime() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS < 0) {
                // send the participation with containing result and quiz back to the users via websocket
                //      and remove the participation from the ParticipationHashMap
                for (Participation participation : participationHashMap.remove(quizId)) {
                    messagingTemplate.convertAndSend("/topic/participation/" + participation.getId() + "/newResults", true);
                }
            }

        }
        //Update Statistics with Results from ResultHashMap (DB Read and DB Write) and remove from ResultHashMap
        for (long quizId : resultHashMap.keySet()) {

            // get the Quiz with the statistic from the database
            QuizExercise quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(quizId);
            // update statistic with all results of the quizExercise
            statisticService.updateStatistics(resultHashMap.remove(quizId), quizExercise);

        }


    }

    private void createParticipations(QuizExercise quizExercise, Map<String, QuizSubmission> userSubmissionMap) {

        for (String username : userSubmissionMap.keySet()) {
            if (userSubmissionMap.get(username).isSubmitted()) {
                QuizSubmission quizSubmission = userSubmissionMap.remove(username);
                quizSubmission.setType(SubmissionType.MANUAL);

                createParticipation(quizExercise, username, quizSubmission);
            } else if (quizExercise.getRemainingTime() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS < 0) {
                QuizSubmission quizSubmission = userSubmissionMap.remove(username);
                quizSubmission.setSubmitted(true);
                quizSubmission.setType(SubmissionType.TIMEOUT);

                createParticipation(quizExercise, username, quizSubmission);
            }
        }
    }

    /**
     * create Participation and Result if the submission was submitted or if the quiz has ended and save them to Database (DB Write)
     *
     * @param quizExercise   the quizExercise the quizSubmission belongs to
     * @param username       the user, who submitted the quizSubmission
     * @param quizSubmission the quizSubmission, which is used to calculate the Result
     * @return the created Participation with Result
     */
    private Participation createParticipation(QuizExercise quizExercise, String username, QuizSubmission quizSubmission) {

        if (quizExercise != null && username != null) {

            // update submission
            quizSubmission.calculateAndUpdateScores(quizExercise);

            //create and save new participation
            Participation participation = new Participation();
            participation.setExercise(quizExercise);
            Optional<User> user = userRepository.findOneByLogin(username);
            if (user.isPresent()) {
                participation.setStudent(user.get());
            }
            participation.setExercise(quizExercise);
            participation = participationRepository.save(participation);

            // create and save new result
            Result result = new Result().participation(participation).submission(quizSubmission);
            result.setRated(true);
            result.setCompletionDate(ZonedDateTime.now());
            result.setSubmission(quizSubmission);
            // calculate score and update result accordingly
            result.evaluateSubmission();
            // save result
            resultRepository.save(result);

            // update quizSubmission with CompletionDate
            quizSubmission.setSubmissionDate(result.getCompletionDate());

            //add the quizExercise to the participation
            participation.setExercise(quizExercise);
            participation.addResults(result);
            //add the participation to the participationHashMap for the send out at the end of the quiz
            QuizScheduleService.addParticipation(quizExercise.getId(), participation);
            //add the result of the participation resultHashMap for the statistic-Update
            QuizScheduleService.addResultToStatistic(quizExercise.getId(), result);

            return participation;
        }
        return null;
    }
}
