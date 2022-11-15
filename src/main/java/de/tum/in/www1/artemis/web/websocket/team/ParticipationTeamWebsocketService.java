package de.tum.in.www1.artemis.web.websocket.team;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import com.hazelcast.core.HazelcastInstance;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ModelingSubmissionService;
import de.tum.in.www1.artemis.service.TextSubmissionService;
import de.tum.in.www1.artemis.web.websocket.dto.OnlineTeamStudentDTO;
import de.tum.in.www1.artemis.web.websocket.dto.SubmissionSyncPayload;

@Controller
public class ParticipationTeamWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationTeamWebsocketService.class);

    private final SimpMessageSendingOperations messagingTemplate;

    private final SimpUserRegistry simpUserRegistry;

    private final Map<String, String> destinationTracker;

    private final Map<String, Instant> lastTypingTracker;

    private final Map<String, Instant> lastActionTracker;

    private final UserRepository userRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExerciseRepository exerciseRepository;

    private final TextSubmissionService textSubmissionService;

    private final ModelingSubmissionService modelingSubmissionService;

    public ParticipationTeamWebsocketService(SimpMessageSendingOperations messagingTemplate, SimpUserRegistry simpUserRegistry, UserRepository userRepository,
            StudentParticipationRepository studentParticipationRepository, ExerciseRepository exerciseRepository, TextSubmissionService textSubmissionService,
            ModelingSubmissionService modelingSubmissionService, HazelcastInstance hazelcastInstance) {
        this.messagingTemplate = messagingTemplate;
        this.simpUserRegistry = simpUserRegistry;
        this.userRepository = userRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.exerciseRepository = exerciseRepository;
        this.textSubmissionService = textSubmissionService;
        this.modelingSubmissionService = modelingSubmissionService;

        // participationId-username -> timestamp
        this.lastTypingTracker = hazelcastInstance.getMap("lastTypingTracker");
        // participationId-username -> timestamp
        this.lastActionTracker = hazelcastInstance.getMap("lastActionTracker");
        // sessionId -> destination
        this.destinationTracker = hazelcastInstance.getMap("destinationTracker");
    }

    /**
     * Called when a user subscribes to the destination specified in the subscribe mapping
     *
     * We have to keep track of the destination that this session belongs to since it is
     * needed on unsubscribe and disconnect but is not available there.
     *
     * @param participationId     id of participation
     * @param stompHeaderAccessor header from STOMP frame
     */
    @SubscribeMapping("/topic/participations/{participationId}/team")
    public void subscribe(@DestinationVariable Long participationId, StompHeaderAccessor stompHeaderAccessor) {
        final String destination = getDestination(participationId);
        destinationTracker.put(stompHeaderAccessor.getSessionId(), destination);
        sendOnlineTeamStudents(participationId);
    }

    /**
     * Called by a user to trigger the sending of the online team members list to all subscribers
     *
     * @param participationId id of participation
     */
    @MessageMapping("/topic/participations/{participationId}/team/trigger")
    public void triggerSendOnlineTeamStudents(@DestinationVariable Long participationId) {
        sendOnlineTeamStudents(participationId);
    }

    /**
     * Called by a user once he starts to type or edit the content of a submission
     * Updates the user's last typing date in the websocket and broadcasts the list of online team members
     *
     * @param participationId id of participation which is being worked on
     * @param principal       principal of user who is working on the submission
     */
    @MessageMapping("/topic/participations/{participationId}/team/typing")
    public void startTyping(@DestinationVariable Long participationId, Principal principal) {
        updateValue(lastTypingTracker, participationId, principal.getName());
        sendOnlineTeamStudents(participationId);
    }

    /**
     * Called by a student of a team to update the modeling submission of the team for their participation
     *
     * @param participationId    id of participation
     * @param modelingSubmission updated modeling submission
     * @param principal          principal of user who wants to update the text submission
     */
    @MessageMapping("/topic/participations/{participationId}/team/modeling-submissions/update")
    public void updateModelingSubmission(@DestinationVariable Long participationId, @Payload ModelingSubmission modelingSubmission, Principal principal) {
        long start = System.currentTimeMillis();
        updateSubmission(participationId, modelingSubmission, principal, "/modeling-submissions");
        log.info("Websocket endpoint updateModelingSubmission took {}ms for submission with id {}", System.currentTimeMillis() - start, modelingSubmission.getId());
    }

    /**
     * Called by a student of a team to update the text submission of the team for their participation
     *
     * @param participationId id of participation
     * @param textSubmission  updated text submission
     * @param principal       principal of user who wants to update the text submission
     */
    @MessageMapping("/topic/participations/{participationId}/team/text-submissions/update")
    public void updateTextSubmission(@DestinationVariable Long participationId, @Payload TextSubmission textSubmission, Principal principal) {
        long start = System.currentTimeMillis();
        updateSubmission(participationId, textSubmission, principal, "/text-submissions");
        log.info("Websocket endpoint updateTextSubmission took {}ms for submission with id {}", System.currentTimeMillis() - start, textSubmission.getId());
    }

    /**
     * Updates a modeling or text submission
     *
     * @param participationId id of participation
     * @param submission      updated modeling text submission
     * @param principal       principal of user who wants to update the submission
     * @param topicPath       path of websocket destination topic where to send the new submission
     */
    private void updateSubmission(@DestinationVariable Long participationId, @Payload Submission submission, Principal principal, String topicPath) {
        // Without this, custom jpa repository methods don't work in websocket channel.
        SecurityUtils.setAuthorizationObject();

        final StudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);

        // user must belong to the team who owns the participation in order to update a submission
        if (!participation.isOwnedBy(principal.getName())) {
            return;
        }

        final User user = userRepository.getUserWithGroupsAndAuthorities(principal.getName());
        final Exercise exercise = exerciseRepository.findByIdElseThrow(participation.getExercise().getId());

        if (submission instanceof ModelingSubmission modelingSubmission && exercise instanceof ModelingExercise modelingExercise) {
            submission = modelingSubmissionService.handleModelingSubmission(modelingSubmission, modelingExercise, user);
            modelingSubmissionService.hideDetails(submission, user);
        }
        else if (submission instanceof TextSubmission textSubmission && exercise instanceof TextExercise textExercise) {
            submission = textSubmissionService.handleTextSubmission(textSubmission, textExercise, user);
            textSubmissionService.hideDetails(submission, user);
        }
        else {
            throw new IllegalArgumentException("Submission type '" + submission.getType() + "' not allowed.");
        }

        // update the last action date for the user and send out list of team members
        updateValue(lastActionTracker, participationId, principal.getName());
        sendOnlineTeamStudents(participationId);

        SubmissionSyncPayload payload = new SubmissionSyncPayload(submission, user);
        messagingTemplate.convertAndSend(getDestination(participationId, topicPath), payload);
    }

    /**
     * Sends out a list of online team students to all members of the team
     *
     * @param participationId id of participation for which to send out the list
     * @param exceptSessionID session id that should be ignored (optional)
     */
    private void sendOnlineTeamStudents(Long participationId, String exceptSessionID) {
        final String destination = getDestination(participationId);

        final List<OnlineTeamStudentDTO> onlineTeamStudents = getSubscriberPrincipals(destination, exceptSessionID).stream()
                .map(login -> new OnlineTeamStudentDTO(login, getValue(lastTypingTracker, participationId, login), lastActionTracker.get(participationId + "-" + login))).toList();

        messagingTemplate.convertAndSend(destination, onlineTeamStudents);
    }

    private void sendOnlineTeamStudents(Long participationId) {
        sendOnlineTeamStudents(participationId, null);
    }

    /**
     * Called when a user unsubscribes (e.g. when he navigates to a different part of the app, is normally called in ngOnDestroy on the client side).
     *
     * @param event session unsubscribe event
     */
    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        unsubscribe(StompHeaderAccessor.wrap(event.getMessage()).getSessionId());
    }

    /**
     * Called when a user disconnects (e.g. when he goes offline or to a different website).
     *
     * @param event session disconnect event
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        unsubscribe(event.getSessionId());
    }

    /**
     * Since this method is called for any sort of unsubscribe or disconnect event, it first needs to be checked whether this event is relevant at all
     * for this particular service which is the case if the session id was tracked by the destinationTracker.
     * The list of subscribed users - explicitly excluding the session that is about to be destroyed - is send to all subscribers.
     * Note: Since a single user can have multiple sessions for a single destination (e.g. by having two open tabs), the user list might not change at all.
     *
     * @param sessionId id of the sessions which is unsubscribing
     */
    public void unsubscribe(String sessionId) {
        Optional.ofNullable(destinationTracker.get(sessionId)).ifPresent(destination -> {
            Long participationId = getParticipationIdFromDestination(destination);
            sendOnlineTeamStudents(participationId, sessionId);
            destinationTracker.remove(sessionId);
        });
    }

    /**
     * Finds all subscriptions to a certain destination and returns the corresponding user logins as a list.
     * Optionally, a certain session ID can be excluded from consideration (which is handy for the unsubscribe event listener which is
     * called before the session is actually removed).
     *
     * @param destination     destination/topic for which to get the subscribers
     * @param exceptSessionID session id that should be excluded from subscription sessions
     * @return an unmodifiable list of principals / logins
     */
    private List<String> getSubscriberPrincipals(String destination, String exceptSessionID) {
        return simpUserRegistry.findSubscriptions(subscription -> subscription.getDestination().equals(destination)).stream().map(SimpSubscription::getSession)
                .filter(simpSession -> !simpSession.getId().equals(exceptSessionID)).map(SimpSession::getUser).map(SimpUser::getName).distinct().toList();
    }

    /**
     * Returns true if the given destination should be handled by this service
     *
     * @param destination Websocket destination topic which to check
     * @return flag whether the destination belongs to this controller
     */
    public static boolean isParticipationTeamDestination(String destination) {
        return Optional.ofNullable(getParticipationIdFromDestination(destination)).isPresent();
    }

    /**
     * Returns the participation id from the destination route
     *
     * @param destination Websocket destination topic from which to extract the participation id
     * @return participation id
     */
    public static Long getParticipationIdFromDestination(String destination) {
        Pattern pattern = Pattern.compile("^" + getDestination("(\\d*)"));
        Matcher matcher = pattern.matcher(destination);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private static String getDestination(Long participationId, String path) {
        return getDestination(participationId.toString(), path);
    }

    private static String getDestination(Long participationId) {
        return getDestination(participationId, "");
    }

    private static String getDestination(String participationId, String path) {
        return "/topic/participations/" + participationId + "/team" + path;
    }

    private static String getDestination(String participationId) {
        return getDestination(participationId, "");
    }

    public Map<String, String> getDestinationTracker() {
        return destinationTracker;
    }

    public void clearDestinationTracker() {
        this.destinationTracker.clear();
    }

    private void updateValue(Map<String, Instant> map, long participationId, String username) {
        map.put(participationId + "-" + username, Instant.now());
    }

    private Instant getValue(Map<String, Instant> map, long participationId, String username) {
        return map.get(participationId + "-" + username);
    }
}
