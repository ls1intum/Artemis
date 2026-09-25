package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.exercise.web.ExerciseWebsocketTopics.TEAM_MODELING_SUBMISSIONS;
import static de.tum.cit.aet.artemis.exercise.web.ExerciseWebsocketTopics.TEAM_ONLINE_STUDENTS;
import static de.tum.cit.aet.artemis.exercise.web.ExerciseWebsocketTopics.TEAM_TEXT_SUBMISSIONS;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopic;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionPatchDTO;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionPatchPayloadDTO;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionSyncPayloadDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamModelingSubmissionUpdateDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamTextSubmissionDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamTextSubmissionUpdateDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.modeling.api.ModelingSubmissionApi;
import de.tum.cit.aet.artemis.modeling.config.ModelingApiNotPresentException;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.dto.OnlineTeamStudentDTO;
import de.tum.cit.aet.artemis.text.api.TextSubmissionApi;
import de.tum.cit.aet.artemis.text.config.TextApiNotPresentException;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

@Controller
@Profile(PROFILE_CORE)
@Lazy
public class ParticipationTeamWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationTeamWebsocketService.class);

    private final WebsocketMessagingService websocketMessagingService;

    private final SimpUserRegistry simpUserRegistry;

    private final UserRepository userRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExerciseRepository exerciseRepository;

    private final Optional<TextSubmissionApi> textSubmissionApi;

    private final Optional<ModelingSubmissionApi> modelingSubmissionApi;

    private final DistributedDataProvider distributedDataProvider;

    // TODO: Follow-Up: move this into a separate service that contains all Hazelcast related data structures

    /** Always access using the getter to ensure that the map is initialized **/
    @Nullable
    private DistributedMap<String, String> destinationTracker;

    /** Always access using the getter to ensure that the map is initialized **/
    @Nullable
    private DistributedMap<String, Instant> lastTypingTracker;

    /** Always access using the getter to ensure that the map is initialized **/
    @Nullable
    private DistributedMap<String, Instant> lastActionTracker;

    public ParticipationTeamWebsocketService(WebsocketMessagingService websocketMessagingService, SimpUserRegistry simpUserRegistry, UserRepository userRepository,
            StudentParticipationRepository studentParticipationRepository, ExerciseRepository exerciseRepository, Optional<TextSubmissionApi> textSubmissionApi,
            Optional<ModelingSubmissionApi> modelingSubmissionApi, DistributedDataProvider distributedDataProvider) {
        this.websocketMessagingService = websocketMessagingService;
        this.simpUserRegistry = simpUserRegistry;
        this.userRepository = userRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.exerciseRepository = exerciseRepository;
        this.textSubmissionApi = textSubmissionApi;
        this.modelingSubmissionApi = modelingSubmissionApi;
        this.distributedDataProvider = distributedDataProvider;
    }

    /**
     * Lazy Init: Returns the last typing tracker map which keeps track of the last typing date for each user in a participation.
     * This is used to determine which team members are currently typing.
     *
     * @return the destination tracker map
     */
    public DistributedMap<String, Instant> getLastTypingTracker() {
        if (this.lastTypingTracker == null) {
            this.lastTypingTracker = this.distributedDataProvider.getMap("lastTypingTracker");
        }
        return lastTypingTracker;
    }

    /**
     * Lazy Init: Returns the last action tracker map which keeps track of the last action date for each user in a participation.
     * This is used to send out the list of online team members when a user subscribes or unsubscribes.
     *
     * @return the last action tracker map
     */
    private DistributedMap<String, Instant> getLastActionTracker() {
        if (this.lastActionTracker == null) {
            this.lastActionTracker = this.distributedDataProvider.getMap("lastActionTracker");
        }
        return lastActionTracker;
    }

    /**
     * Lazy Init: Returns the destination tracker map which keeps track of the destination that each session is subscribed to.
     * This is used to send out the list of online team members when a user subscribes or unsubscribes.
     *
     * @return the destination tracker map
     */
    public DistributedMap<String, String> getDestinationTracker() {
        if (this.destinationTracker == null) {
            this.destinationTracker = this.distributedDataProvider.getMap("destinationTracker");
        }
        return destinationTracker;
    }

    // only used for testing purposes, could be moved to a test utility class
    public void clearDestinationTracker() {
        this.getDestinationTracker().clear();
    }

    /**
     * Called for subscription events, including frames Spring enqueued but later rejected. Only an authorized subscription to the team topic of a participation
     * ({@link ExerciseWebsocketTopics#TEAM_ONLINE_STUDENTS}) announces the subscriber to the rest of the team.
     *
     * @param event session subscribe event
     */
    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor stompHeaderAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = stompHeaderAccessor.getDestination();
        Principal principal = event.getUser();
        SimpUser user = principal != null ? simpUserRegistry.getUser(principal.getName()) : null;
        String sessionId = stompHeaderAccessor.getSessionId();
        SimpSession session = user != null && sessionId != null ? user.getSession(sessionId) : null;
        // The local registry runs first and independently checks the topic's access rule.
        boolean authorized = session != null && session.getSubscriptions().stream()
                .anyMatch(subscription -> subscription.getId().equals(stompHeaderAccessor.getSubscriptionId()) && subscription.getDestination().equals(destination));
        if (authorized && destination != null) {
            TEAM_ONLINE_STUDENTS.match(destination).ifPresent(variables -> subscribe(Long.parseLong(variables.get("participationId")), stompHeaderAccessor));
        }
    }

    /**
     * Called when a user subscribes to the team topic of a participation.
     * <p>
     * We have to keep track of the destination that this session belongs to since it is
     * needed on unsubscribe and disconnect but is not available there.
     *
     * @param participationId     id of participation
     * @param stompHeaderAccessor header from STOMP frame
     */
    public void subscribe(long participationId, StompHeaderAccessor stompHeaderAccessor) {
        getDestinationTracker().put(stompHeaderAccessor.getSessionId(), TEAM_ONLINE_STUDENTS.at(participationId).value());
        sendOnlineTeamStudents(participationId);
    }

    /**
     * Called by a team member to trigger the sending of the online team members list to all subscribers
     *
     * @param participationId id of participation
     * @param principal       principal of the user who sends the message
     */
    @MessageMapping("/participations/{participationId}/team/trigger")
    public void triggerSendOnlineTeamStudents(@DestinationVariable Long participationId, Principal principal) {
        if (!isMemberOfParticipation(participationId, principal)) {
            return;
        }
        sendOnlineTeamStudents(participationId);
    }

    /**
     * Called by a team member once they start to type or edit the content of a submission
     * Updates the user's last typing date using websockets and broadcasts the list of online team members
     *
     * @param participationId id of participation which is being worked on
     * @param principal       principal of user who is working on the submission
     */
    @MessageMapping("/participations/{participationId}/team/typing")
    public void startTyping(@DestinationVariable Long participationId, Principal principal) {
        if (!isMemberOfParticipation(participationId, principal)) {
            return;
        }
        updateValue(getLastTypingTracker(), participationId, principal.getName());
        sendOnlineTeamStudents(participationId);
    }

    private boolean isMemberOfParticipation(long participationId, Principal principal) {
        return principal != null && studentParticipationRepository.existsByIdAndParticipatingStudentLogin(participationId, principal.getName());
    }

    /**
     * Called by a student of a team to update the modeling submission of the team for their participation
     *
     * @param participationId id of participation
     * @param update          updated modeling submission
     * @param principal       principal of user who wants to update the text submission
     */
    @MessageMapping("/participations/{participationId}/team/modeling-submissions/update")
    public void updateModelingSubmission(@DestinationVariable Long participationId, @Payload TeamModelingSubmissionUpdateDTO update, Principal principal) {
        long start = System.currentTimeMillis();
        ModelingSubmission modelingSubmission = new ModelingSubmission();
        modelingSubmission.setId(update.id());
        modelingSubmission.setModel(update.model());
        modelingSubmission.setExplanationText(update.explanationText());
        modelingSubmission.setSubmitted(Boolean.TRUE.equals(update.submitted()));
        updateSubmission(participationId, modelingSubmission, principal, TEAM_MODELING_SUBMISSIONS, null);
        log.debug("Websocket endpoint updateModelingSubmission took {}ms for submission with id {}", System.currentTimeMillis() - start, update.id());
    }

    /**
     * Called by a student of a team to update the modeling submission of the team for their participation
     *
     * @param participationId id of participation
     * @param submissionPatch patch to be applied to modeling submission
     * @param principal       principal of user who wants to update the text submission
     */
    @MessageMapping("/participations/{participationId}/team/modeling-submissions/patch")
    public void patchModelingSubmission(@DestinationVariable Long participationId, @Payload SubmissionPatchDTO submissionPatch, Principal principal) {
        long start = System.currentTimeMillis();
        patchSubmission(participationId, submissionPatch, principal, TEAM_MODELING_SUBMISSIONS);
        log.debug("Websocket endpoint patchModelingSubmission took {}ms", System.currentTimeMillis() - start);
    }

    /**
     * Called by a student of a team to update the text submission of the team for their participation
     *
     * @param participationId id of participation
     * @param update          updated text submission
     * @param principal       principal of user who wants to update the text submission
     */
    @MessageMapping("/participations/{participationId}/team/text-submissions/update")
    public void updateTextSubmission(@DestinationVariable Long participationId, @Payload TeamTextSubmissionUpdateDTO update, Principal principal) {
        long start = System.currentTimeMillis();
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.setId(update.id());
        textSubmission.setText(update.text());
        textSubmission.setLanguage(update.language());
        textSubmission.setSubmitted(Boolean.TRUE.equals(update.submitted()));
        updateSubmission(participationId, textSubmission, principal, TEAM_TEXT_SUBMISSIONS, update);
        log.debug("Websocket endpoint updateTextSubmission took {}ms for submission with id {}", System.currentTimeMillis() - start, update.id());
    }

    /**
     * Updates a modeling or text submission
     *
     * @param participationId id of participation
     * @param submission      updated modeling text submission
     * @param principal       principal of user who wants to update the submission
     * @param topic           the team topic where to send the new submission
     * @param textUpdate      the text update to broadcast to the teammates, or null when the teammates are not synced
     */
    private void updateSubmission(@DestinationVariable Long participationId, Submission submission, Principal principal, WebsocketTopic topic,
            @Nullable TeamTextSubmissionUpdateDTO textUpdate) {
        // The websocket message carries a real principal, which this keeps; it only stands in if one is missing.
        SecurityUtils.setAuthorizationObject();

        final StudentParticipation participation = studentParticipationRepository.findByIdWithEagerTeamStudentsElseThrow(participationId);

        // user must belong to the team who owns the participation in order to update a submission
        if (!participation.isOwnedBy(principal.getName())) {
            return;
        }

        final User user = userRepository.getUserWithAuthorities(principal.getName());
        final Exercise exercise = exerciseRepository.findByIdElseThrow(participation.getExercise().getId());
        // Only team exercises sync through this endpoint. It consults none of the exam gates the REST save applies, so an
        // individual or exam participation must not be saved here.
        if (!exercise.isTeamMode() || exercise.isExamExercise()) {
            return;
        }

        if (submission instanceof ModelingSubmission modelingSubmission && exercise instanceof ModelingExercise modelingExercise) {
            ModelingSubmissionApi api = modelingSubmissionApi.orElseThrow(() -> new ModelingApiNotPresentException(ModelingSubmissionApi.class));
            submission = api.handleModelingSubmission(modelingSubmission, modelingExercise, user);
            // The save wrote the foreign key from an id, so the saved submission carries no participation. Both the
            // filtering below and the teammates' payload read one, and this handler loaded it with its team above.
            submission.setParticipation(participation);
            api.hideDetails(submission, user);
        }
        else if (submission instanceof TextSubmission textSubmission && exercise instanceof TextExercise textExercise) {
            TextSubmissionApi api = textSubmissionApi.orElseThrow(() -> new TextApiNotPresentException(TextSubmissionApi.class));
            submission = api.handleTextSubmission(textSubmission, textExercise, user);
            submission.setParticipation(participation);
            api.hideDetails(submission, user);
        }
        else {
            throw new IllegalArgumentException("Submission type '" + submission.getType() + "' not allowed.");
        }

        if (textUpdate != null) {
            // update the last action date for the user and send out list of team members
            updateValue(getLastActionTracker(), participationId, principal.getName());
            sendOnlineTeamStudents(participationId);

            SubmissionSyncPayloadDTO payload = new SubmissionSyncPayloadDTO(TeamTextSubmissionDTO.of(submission), UserNameDTO.of(user));
            websocketMessagingService.sendMessage(topic.at(participationId), payload);
        }
    }

    /**
     * Called by a student for updating a shared submission being collaborated on by the whole team.
     *
     * @param participationId id of participation
     * @param submissionPatch patch to be applied to submission (changes made by calling student)
     * @param principal       principal of user who wants to update the submission
     * @param topic           the team topic where to send the new submission
     */
    private void patchSubmission(Long participationId, SubmissionPatchDTO submissionPatch, Principal principal, WebsocketTopic topic) {
        // The websocket message carries a real principal, which this keeps; it only stands in if one is missing.
        SecurityUtils.setAuthorizationObject();

        // user must belong to the team who owns the participation in order to update a submission
        boolean isValidUser = studentParticipationRepository.existsByIdAndParticipatingStudentLogin(participationId, principal.getName());

        if (!isValidUser) {
            return;
        }

        // update the last action date for the user and send out list of team members
        updateValue(getLastActionTracker(), participationId, principal.getName());
        sendOnlineTeamStudents(participationId);

        SubmissionPatchPayloadDTO payload = new SubmissionPatchPayloadDTO(submissionPatch, principal.getName());
        websocketMessagingService.sendMessage(topic.at(participationId), payload);
    }

    /**
     * Sends out a list of online team students to all members of the team
     *
     * @param participationId id of participation for which to send out the list
     * @param exceptSessionID session id that should be ignored (optional)
     */
    private void sendOnlineTeamStudents(Long participationId, String exceptSessionID) {
        final var destination = TEAM_ONLINE_STUDENTS.at(participationId);

        final List<OnlineTeamStudentDTO> onlineTeamStudents = getSubscriberPrincipals(destination.value(), exceptSessionID).stream()
                .map(login -> new OnlineTeamStudentDTO(login, getValue(getLastTypingTracker(), participationId, login), getLastActionTracker().get(participationId + "-" + login)))
                .toList();

        websocketMessagingService.sendMessage(destination, onlineTeamStudents);
    }

    private void sendOnlineTeamStudents(Long participationId) {
        sendOnlineTeamStudents(participationId, null);
    }

    /**
     * Called when a user unsubscribes (e.g. when they navigate to a different part of the app, is normally called in ngOnDestroy on the client side).
     *
     * @param event session unsubscribe event
     */
    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        unsubscribe(StompHeaderAccessor.wrap(event.getMessage()).getSessionId());
    }

    /**
     * Called when a user disconnects (e.g. when they go offline or to a different website).
     *
     * @param event session disconnect event
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        unsubscribe(event.getSessionId());
    }

    /**
     * Since this method is called for any sort of unsubscribe or disconnect event, it first needs to be checked whether this event is relevant at all
     * for this particular service which is the case if the session id was tracked by the destinationTracker
     * The list of subscribed users - explicitly excluding the session that is about to be destroyed - is send to all subscribers.
     * Note: Since a single user can have multiple sessions for a single destination (e.g. by having two open tabs), the user list might not change at all.
     *
     * @param sessionId id of the sessions which is unsubscribing
     */
    public void unsubscribe(String sessionId) {
        // check if Hazelcast is still active, before invoking this
        try {
            if (distributedDataProvider.isInstanceRunning()) {
                Optional.ofNullable(getDestinationTracker().get(sessionId)).ifPresent(destination -> {
                    getDestinationTracker().remove(sessionId);
                    TEAM_ONLINE_STUDENTS.match(destination).ifPresent(variables -> sendOnlineTeamStudents(Long.parseLong(variables.get("participationId")), sessionId));
                });
            }
        }
        catch (RuntimeException e) {
            log.error("Failed to unsubscribe as the distributed data provider is unavailable");
        }
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

    private void updateValue(DistributedMap<String, Instant> map, long participationId, String username) {
        map.put(participationId + "-" + username, Instant.now());
    }

    private Instant getValue(DistributedMap<String, Instant> map, long participationId, String username) {
        return map.get(participationId + "-" + username);
    }
}
