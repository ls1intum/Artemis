package de.tum.in.www1.artemis.web.websocket;

import static de.tum.in.www1.artemis.config.Constants.EXERCISE_TOPIC_ROOT;
import static de.tum.in.www1.artemis.config.Constants.NEW_RESULT_TOPIC;

import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ExerciseDateService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.web.rest.dto.ResultDTO;

/**
 * This service is responsible for sending websocket notifications when a new result got created.
 */
@Service
public class ResultWebsocketService {

    private final WebsocketMessagingService websocketMessagingService;

    private final ExamDateService examDateService;

    private final ExerciseDateService exerciseDateService;

    private final AuthorizationCheckService authCheckService;

    public ResultWebsocketService(WebsocketMessagingService websocketMessagingService, ExamDateService examDateService, ExerciseDateService exerciseDateService,
            AuthorizationCheckService authCheckService) {
        this.websocketMessagingService = websocketMessagingService;
        this.examDateService = examDateService;
        this.exerciseDateService = exerciseDateService;
        this.authCheckService = authCheckService;
    }

    /**
     * Broadcast a new result to the client.
     *
     * @param participation the id is used in the destination (so that only clients who have subscribed the specific participation will receive the result)
     * @param result        the new result that should be sent to the client. It typically includes feedback, its participation will be cut off here to reduce the payload size.
     *                          As the participation is already known to the client, we do not need to send it. This also cuts of the exercise (including the potentially huge
     *                          problem statement and the course with all potential attributes
     */
    public void broadcastNewResult(Participation participation, Result result) {
        if (participation instanceof StudentParticipation studentParticipation) {
            broadcastNewResultToParticipants(studentParticipation, result);
        }

        // Send to tutors, instructors and admins
        websocketMessagingService.sendMessage(getNonPersonalExerciseResultDestination(participation.getExercise().getId()), ResultDTO.of(result));
    }

    private void broadcastNewResultToParticipants(StudentParticipation studentParticipation, Result result) {
        final Exercise exercise = studentParticipation.getExercise();
        boolean isWorkingPeriodOver;
        if (exercise.isExamExercise()) {
            isWorkingPeriodOver = examDateService.isExerciseWorkingPeriodOver(exercise, studentParticipation);
        }
        else {
            isWorkingPeriodOver = exerciseDateService.isAfterLatestDueDate(exercise);
        }
        // Don't send students results after the exam ended
        boolean isAfterExamEnd = isWorkingPeriodOver && exercise.isExamExercise() && !exercise.getExamViaExerciseGroupOrCourseMember().isTestExam();
        // If the assessment due date is not over yet, do not send manual feedback to students!
        boolean isAutomaticAssessmentOrDueDateOver = AssessmentType.AUTOMATIC == result.getAssessmentType() || exercise.getAssessmentDueDate() == null
                || ZonedDateTime.now().isAfter(exercise.getAssessmentDueDate());

        if (isAutomaticAssessmentOrDueDateOver && !isAfterExamEnd) {
            var students = studentParticipation.getStudents();

            var resultDTO = ResultDTO.of(result);
            students.stream().filter(student -> authCheckService.isAtLeastTeachingAssistantForExercise(exercise, student))
                    .forEach(user -> websocketMessagingService.sendMessageToUser(user.getLogin(), NEW_RESULT_TOPIC, resultDTO));

            var filteredFeedback = result.createFilteredFeedbacks(!isWorkingPeriodOver);
            var filteredFeedbackResultDTO = ResultDTO.of(result, filteredFeedback);

            students.stream().filter(student -> !authCheckService.isAtLeastTeachingAssistantForExercise(exercise, student))
                    .forEach(user -> websocketMessagingService.sendMessageToUser(user.getLogin(), NEW_RESULT_TOPIC, filteredFeedbackResultDTO));
        }
    }

    /**
     * Returns true if the given destination is a 'non-personal' exercise result subscription.
     * Only teaching assistants, instructors and admins should be allowed to subscribe to this topic.
     *
     * @param destination Websocket destination topic which to check
     * @return flag whether the destination is a 'non-personal' exercise result subscription
     */
    public static boolean isNonPersonalExerciseResultDestination(String destination) {
        return getExerciseIdFromNonPersonalExerciseResultDestination(destination) != null;
    }

    /**
     * Returns the exercise id from the destination route
     *
     * @param destination Websocket destination topic from which to extract the exercise id
     * @return exercise id
     */
    public static Long getExerciseIdFromNonPersonalExerciseResultDestination(String destination) {
        Pattern pattern = Pattern.compile("^" + getNonPersonalExerciseResultDestination("(\\d*)"));
        Matcher matcher = pattern.matcher(destination);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private static String getNonPersonalExerciseResultDestination(long exerciseId) {
        return getNonPersonalExerciseResultDestination(String.valueOf(exerciseId));
    }

    private static String getNonPersonalExerciseResultDestination(String exerciseId) {
        return EXERCISE_TOPIC_ROOT + exerciseId + "/newResults";
    }
}
