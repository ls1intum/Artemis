package de.tum.in.www1.artemis.web.websocket.team;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.AbstractSubProtocolEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@Controller
public class ParticipationTeamWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationTeamWebsocketService.class);

    private final SimpMessageSendingOperations messagingTemplate;

    private SimpUserRegistry simpUserRegistry;

    private Map<String, String> destinationTracker = new HashMap<>();

    public ParticipationTeamWebsocketService(SimpMessageSendingOperations messagingTemplate, SimpUserRegistry simpUserRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.simpUserRegistry = simpUserRegistry;
    }

    @SubscribeMapping("/topic/participations/{participationId}/team")
    public void subscribe(@DestinationVariable Long participationId, StompHeaderAccessor stompHeaderAccessor, Principal principal) {
        final String destination = getDestination(participationId);
        destinationTracker.put(stompHeaderAccessor.getSessionId(), destination);
        final List<String> userLogins = getSubscriberPrincipals(destination);
        messagingTemplate.convertAndSend(destination, userLogins);
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        unsubscribe(event);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        unsubscribe(event);
    }

    private void unsubscribe(AbstractSubProtocolEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        Optional.ofNullable(destinationTracker.get(headers.getSessionId())).ifPresent(destination -> {
            Optional.ofNullable(event.getUser()).ifPresent(principal -> {
                List<String> userLogins = getSubscriberPrincipals(destination, principal.getName());
                messagingTemplate.convertAndSend(destination, userLogins);
                destinationTracker.remove(headers.getSessionId());
            });
        });
    }

    private List<String> getSubscriberPrincipals(String destination, String exceptPrincipalName) {
        return simpUserRegistry.findSubscriptions(s -> s.getDestination().equals(destination)).stream().map(SimpSubscription::getSession).map(SimpSession::getUser)
                .map(SimpUser::getName).filter(name -> !name.equals(exceptPrincipalName)).collect(Collectors.toList());
    }

    private List<String> getSubscriberPrincipals(String destination) {
        return getSubscriberPrincipals(destination, null);
    }

    private static String getDestination(Long participationId) {
        return "/topic/participations/" + participationId + "/team";
    }
}
