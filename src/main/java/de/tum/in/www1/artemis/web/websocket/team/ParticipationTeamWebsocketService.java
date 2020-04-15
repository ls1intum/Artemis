package de.tum.in.www1.artemis.web.websocket.team;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.TextExerciseService;
import de.tum.in.www1.artemis.service.TextSubmissionService;
import de.tum.in.www1.artemis.service.UserService;

@Controller
public class ParticipationTeamWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationTeamWebsocketService.class);

    private final SimpMessageSendingOperations messagingTemplate;

    private final SimpUserRegistry simpUserRegistry;

    private final Map<String, String> destinationTracker = new HashMap<>();

    private final UserService userService;

    private final ParticipationService participationService;

    private final TextExerciseService textExerciseService;

    private final TextSubmissionService textSubmissionService;

    public ParticipationTeamWebsocketService(SimpMessageSendingOperations messagingTemplate, SimpUserRegistry simpUserRegistry, ParticipationService participationService,
            TextExerciseService textExerciseService, TextSubmissionService textSubmissionService, UserService userService) {
        this.messagingTemplate = messagingTemplate;
        this.simpUserRegistry = simpUserRegistry;
        this.participationService = participationService;
        this.textExerciseService = textExerciseService;
        this.textSubmissionService = textSubmissionService;
        this.userService = userService;
    }

    /**
     * Called when a user subscribes to the destination specified in the subscribe mapping
     * <p>
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
        sendOnlineTeamMembers(destination);
    }

    /**
     * Called by a user to trigger the sending of the online team members list to all subscribers
     *
     * @param participationId id of participation
     */
    @MessageMapping("/topic/participations/{participationId}/team/trigger")
    public void triggerSend(@DestinationVariable Long participationId) {
        sendOnlineTeamMembers(getDestination(participationId));
    }

    @MessageMapping("/topic/participations/{participationId}/team/text-submissions/update")
    public void updateTextSubmission(@DestinationVariable Long participationId, @Payload TextSubmission textSubmission, Principal principal) {
        // Without this, custom jpa repository methods don't work in websocket channel.
        SecurityUtils.setAuthorizationObject();

        final StudentParticipation participation = participationService.findOneStudentParticipation(participationId);

        // user must belong to the team who owns the participation in order to update a text submission
        if (!participation.isOwnedBy(principal.getName())) {
            return;
        }

        final User user = userService.getUserWithGroupsAndAuthorities(principal.getName());
        final TextExercise textExercise = textExerciseService.findOne(participation.getExercise().getId());

        textSubmission = textSubmissionService.handleTextSubmission(textSubmission, textExercise, principal);
        // TODO: filter out sensitive details?

        messagingTemplate.convertAndSend(getDestination(participationId, "/text-submissions"), textSubmission);
    }

    /**
     * Sends out a list of user logins of team students that are online to all team members
     *
     * @param destination websocket topic to which to send the list of online users
     */
    private void sendOnlineTeamMembers(String destination) {
        final List<String> userLogins = getSubscriberPrincipals(destination);
        messagingTemplate.convertAndSend(destination, userLogins);
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
            List<String> userLogins = getSubscriberPrincipals(destination, sessionId);
            messagingTemplate.convertAndSend(destination, userLogins);
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
     * @return list of principals / logins
     */
    private List<String> getSubscriberPrincipals(String destination, String exceptSessionID) {
        return simpUserRegistry.findSubscriptions(s -> s.getDestination().equals(destination)).stream().map(SimpSubscription::getSession)
                .filter(simpSession -> !simpSession.getId().equals(exceptSessionID)).map(SimpSession::getUser).map(SimpUser::getName).collect(Collectors.toList());
    }

    private List<String> getSubscriberPrincipals(String destination) {
        return getSubscriberPrincipals(destination, null);
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
}
