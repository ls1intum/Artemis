package de.tum.cit.aet.artemis.core.config.websocket;

import static de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService.getExerciseIdFromNonPersonalExerciseResultDestination;
import static de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService.isNonPersonalExerciseResultDestination;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.exercise.web.ParticipationTeamWebsocketService.getParticipationIdFromDestination;
import static de.tum.cit.aet.artemis.exercise.web.ParticipationTeamWebsocketService.isParticipationTeamDestination;
import static de.tum.cit.aet.artemis.programming.service.localci.LocalCIWebsocketMessagingService.isBuildAgentDestination;
import static de.tum.cit.aet.artemis.programming.service.localci.LocalCIWebsocketMessagingService.isBuildQueueAdminDestination;
import static de.tum.cit.aet.artemis.programming.service.localci.LocalCIWebsocketMessagingService.isBuildQueueCourseDestination;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompReactorNettyCodec;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.tcp.TcpOperations;
import org.springframework.messaging.tcp.reactor.ReactorNettyTcpClient;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.DelegatingWebSocketMessageBrokerConfiguration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.sockjs.transport.handler.WebSocketTransportHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.config.InetSocketAddressValidator;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.jwt.JWTFilter;
import de.tum.cit.aet.artemis.core.security.jwt.JwtWithSource;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;

@Profile(PROFILE_CORE)
@Configuration
// We cannot make this lazy as the client then fails to subscribe to team participation topics
@Lazy(value = false)
// See https://stackoverflow.com/a/34337731/3802758
public class WebsocketConfiguration extends DelegatingWebSocketMessageBrokerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WebsocketConfiguration.class);

    private static final Pattern EXAM_TOPIC_PATTERN = Pattern.compile("^/topic/exams/(\\d+)/.+$");

    public static final String IP_ADDRESS = "IP_ADDRESS";

    private final ObjectMapper objectMapper;

    private final TokenProvider tokenProvider;

    private final TaskScheduler messageBrokerTaskScheduler;

    private final StudentParticipationRepository studentParticipationRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ExerciseRepository exerciseRepository;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    // Split the addresses by comma
    @Value("#{'${spring.websocket.broker.addresses}'.split(',')}")
    private List<String> brokerAddresses;

    @Value("${spring.websocket.broker.username}")
    private String brokerUsername;

    @Value("${spring.websocket.broker.password}")
    private String brokerPassword;

    public WebsocketConfiguration(MappingJackson2HttpMessageConverter springMvcJacksonConverter, TaskScheduler messageBrokerTaskScheduler, TokenProvider tokenProvider,
            StudentParticipationRepository studentParticipationRepository, AuthorizationCheckService authorizationCheckService, ExerciseRepository exerciseRepository,
            Optional<ExamRepositoryApi> examRepositoryApi) {
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
        this.tokenProvider = tokenProvider;
        this.studentParticipationRepository = studentParticipationRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.exerciseRepository = exerciseRepository;
        this.examRepositoryApi = examRepositoryApi;
    }

    @Override
    protected void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        // Try to create a TCP client that will connect to the message broker (or the message brokers if multiple exists).
        // If tcpClient is null, there is no valid address specified in the config. This could be due to a development setup or a mistake in the config.
        TcpOperations<byte[]> tcpClient = createTcpClient();
        if (tcpClient != null) {
            log.debug("Enabling StompBrokerRelay for WebSocket messages using {}", String.join(", ", brokerAddresses));
            config
                    // Enable the relay for "/topic"
                    .enableStompBrokerRelay("/topic")
                    // Messages that could not be sent to a user (as he is not connected to this server) will be forwarded to "/topic/unresolved-user"
                    .setUserDestinationBroadcast("/topic/unresolved-user")
                    // Information about connected users will be sent to "/topic/user-registry"
                    .setUserRegistryBroadcast("/topic/user-registry")
                    // Set client username and password to the one loaded from the config
                    .setClientLogin(brokerUsername).setClientPasscode(brokerPassword)
                    // Set system username and password to the one loaded from the config
                    .setSystemLogin(brokerUsername).setSystemPasscode(brokerPassword)
                    // Set the same heartbeat as in the client (websocket-service.ts) to detect broken connections
                    .setSystemHeartbeatReceiveInterval(10_000)
                    // Set the same heartbeat as in the client (websocket-service.ts) to detect broken connections
                    .setSystemHeartbeatSendInterval(10_000)
                    // Set the TCP client to the one generated above
                    .setTcpClient(tcpClient);
        }
        else {
            log.info("Did NOT enable StompBrokerRelay for WebSocket messages. Use simple integrated broker instead.");

            // @formatter:off
            config.enableSimpleBroker("/topic")
                // Set the same heartbeat as in the client (websocket-service.ts) to detect broken connections
                .setHeartbeatValue(new long[] { 10_000, 10_000 })
                // Use the custom task scheduler for the heartbeat messages
                .setTaskScheduler(messageBrokerTaskScheduler);
            // @formatter:on
        }
    }

    @Override
    protected boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        GzipMessageConverter gzipMessageConverter = new GzipMessageConverter(objectMapper);
        messageConverters.add(gzipMessageConverter);
        return false;
    }

    /**
     * Create a TCP client that will connect to the broker defined in the config.
     * If multiple brokers are configured, the client will connect to the first one and fail over to the next one in case a broker goes down.
     * If the last broker goes down, the first one is retried.
     * Also see <a href="https://github.com/spring-projects/spring-framework/issues/17057">...</a> and
     * <a href="https://docs.spring.io/spring/docs/current/spring-framework-reference/web.html#websocket-stomp-handle-broker-relay-configure">...</a>
     *
     * @return a TCP client with a round-robin use
     */
    private ReactorNettyTcpClient<byte[]> createTcpClient() {
        final List<InetSocketAddress> brokerAddressList = brokerAddresses.stream().map(InetSocketAddressValidator::getValidAddress).flatMap(Optional::stream).toList();

        // Return null if no valid addresses can be found. This is e.g. due to an invalid config or a development setup without a broker.
        if (brokerAddressList.isEmpty()) {
            return null;
        }

        // === Single broker: always connect to this one ===
        if (brokerAddressList.size() == 1) {
            final InetSocketAddress addr = brokerAddressList.getFirst();
            return new ReactorNettyTcpClient<>(addr.getHostString(), addr.getPort(), new StompReactorNettyCodec());
        }

        // === Multiple brokers: thread-safe round robin ===
        AtomicInteger index = new AtomicInteger(0);

        return new ReactorNettyTcpClient<>(client -> client.remoteAddress(() -> {
            int i = Math.floorMod(index.getAndIncrement(), brokerAddressList.size());
            InetSocketAddress addr = brokerAddressList.get(i);
            log.info("STOMP relay connecting to broker[{}] {}:{}", i, addr.getHostString(), addr.getPort());
            return addr;
        }), new StompReactorNettyCodec());
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        DefaultHandshakeHandler handshakeHandler = defaultHandshakeHandler();
        WebSocketTransportHandler webSocketTransportHandler = new WebSocketTransportHandler(handshakeHandler);
        // @formatter:off
        registry
            // NOTE: clients can connect using sockjs via 'ws://{artemis-url}/websocket' or without sockjs using 'ws://{artemis-url}/websocket/websocket'
            .addEndpoint("/websocket")
            .setAllowedOriginPatterns("*")
            // TODO: in the future, we should deactivate the option to connect with sockjs, because this is not needed any more
            .withSockJS()
            .setTransportHandlers(webSocketTransportHandler)
            .setInterceptors(httpSessionHandshakeInterceptor());
        // @formatter:on
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new TopicSubscriptionInterceptor());
    }

    @NonNull
    @Override
    protected MappingJackson2MessageConverter createJacksonConverter() {
        return new GzipMessageConverter(objectMapper);
    }

    /**
     * @return initialize the handshake interceptor stores the remote IP address before handshake
     */
    @Bean
    public HandshakeInterceptor httpSessionHandshakeInterceptor() {
        return new HandshakeInterceptor() {

            @Override
            public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response, @NonNull WebSocketHandler wsHandler,
                    @NonNull Map<String, Object> attributes) {
                log.debug("beforeHandshake: {}, {}, {}", request, response, wsHandler);
                if (request instanceof ServletServerHttpRequest servletRequest) {
                    try {
                        attributes.put(IP_ADDRESS, servletRequest.getRemoteAddress());

                        JwtWithSource jwtWithSource = JWTFilter.extractValidJwt(servletRequest.getServletRequest(), tokenProvider);
                        return jwtWithSource != null;
                    }
                    catch (IllegalArgumentException e) {
                        response.setStatusCode(HttpStatusCode.valueOf(400));
                        return false;
                    }
                }
                return false;
            }

            @Override
            public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response, @NonNull WebSocketHandler wsHandler, Exception exception) {
                log.debug("afterHandshake: {}, {}, {}", request, response, wsHandler);
                if (exception != null) {
                    log.warn("Exception occurred in WS.afterHandshake", exception);
                }
            }
        };
    }

    private DefaultHandshakeHandler defaultHandshakeHandler() {
        return new DefaultHandshakeHandler() {

            @Override
            protected Principal determineUser(@NonNull ServerHttpRequest request, @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
                Principal principal = request.getPrincipal();
                log.debug("determineUser: {}", principal);
                if (principal == null) {
                    Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority(Role.ANONYMOUS.getAuthority()));
                    principal = new AnonymousAuthenticationToken("WebsocketConfiguration", "anonymous", authorities);
                }
                return principal;
            }
        };
    }

    public class TopicSubscriptionInterceptor implements ChannelInterceptor {

        /**
         * Method is called before the user's message is sent to the controller
         *
         * @param message Message that the websocket client is sending (e.g. SUBSCRIBE, MESSAGE, UNSUBSCRIBE)
         * @param channel Current message channel
         * @return message that gets sent along further
         */
        @Override
        public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
            log.debug("preSend: {}, channel: {}", message, channel);
            StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(message);
            Principal principal = headerAccessor.getUser();
            String destination = headerAccessor.getDestination();

            if (StompCommand.SUBSCRIBE.equals(headerAccessor.getCommand())) {
                try {
                    if (!allowSubscription(principal, destination)) {
                        logUnauthorizedDestinationAccess(principal, destination);
                        return null; // erase the forbidden SUBSCRIBE command the user was trying to send
                    }
                }
                catch (EntityNotFoundException e) {
                    // If the user is not found (e.g. because he is not logged in), he should not be able to subscribe to these topics
                    log.warn("An error occurred while subscribing user {} to destination {}: {}", principal != null ? principal.getName() : "null", destination, e.getMessage());
                    return null;
                }
            }

            return message;
        }

        /**
         * Returns whether the subscription of the given principal to the given destination is permitted
         * Database calls should be avoided as much as possible in this method.
         * Only for very specific topics, database calls are allowed.
         *
         * @param principal   User principal of the user who wants to subscribe
         * @param destination Destination topic to which the user wants to subscribe
         * @return flag whether subscription is allowed
         */
        private boolean allowSubscription(@Nullable Principal principal, String destination) {
            log.debug("{} wants to subscribe to {}", principal != null ? principal.getName() : "Anonymous", destination);
            /*
             * IMPORTANT: Avoid database calls in this method as much as possible (e.g. checking if the user
             * is an instructor in a course)
             * This method is called for every subscription request, so it should be as fast as possible.
             * If you need to do a database call, make sure to first check if the destination is valid for your specific
             * use case.
             */
            if (principal == null) {
                log.warn("Anonymous user tried to access the protected topic: {}", destination);
                return false;
            }

            final var login = principal.getName();

            if (isBuildQueueAdminDestination(destination) || isBuildAgentDestination(destination)) {
                return authorizationCheckService.isAdmin(login);
            }

            Optional<Long> courseId = isBuildQueueCourseDestination(destination);
            if (courseId.isPresent()) {
                return authorizationCheckService.isAtLeastInstructorInCourse(login, courseId.get());
            }

            if (isParticipationTeamDestination(destination)) {
                Long participationId = getParticipationIdFromDestination(destination);
                return isParticipationOwnedByUser(principal, participationId);
            }
            if (isNonPersonalExerciseResultDestination(destination)) {
                final long exerciseId = getExerciseIdFromNonPersonalExerciseResultDestination(destination).orElseThrow();

                // TODO: Is it right that TAs are not allowed to subscribe to exam exercises?
                if (exerciseRepository.isExamExercise(exerciseId)) {
                    return authorizationCheckService.isAtLeastInstructorInExercise(login, exerciseId);
                }
                else {
                    return authorizationCheckService.isAtLeastTeachingAssistantInExercise(login, exerciseId);
                }
            }

            var examId = getExamIdFromExamRootDestination(destination);
            if (examId.isPresent()) {
                ExamRepositoryApi api = examRepositoryApi.orElseThrow(() -> new ExamApiNotPresentException(ExamRepositoryApi.class));
                var exam = api.findByIdElseThrow(examId.get());
                return authorizationCheckService.isAtLeastInstructorInCourse(login, exam.getCourse().getId());
            }
            return true;
        }

        private void logUnauthorizedDestinationAccess(Principal principal, String destination) {
            if (principal == null) {
                log.warn("Anonymous user tried to access the protected topic: {}", destination);
            }
            else {
                log.warn("User with login '{}' tried to access the protected topic: {}", principal.getName(), destination);
            }
        }
    }

    private boolean isParticipationOwnedByUser(Principal principal, Long participationId) {
        StudentParticipation participation = studentParticipationRepository.findByIdWithEagerTeamStudentsElseThrow(participationId);
        return participation.isOwnedBy(principal.getName());
    }

    /**
     * Returns the exam id if the given destination belongs to a topic for a whole exam.
     * Only instructors and admins should be allowed to subscribe to this topic.
     *
     * @param destination Websocket destination topic which to check
     * @return an optional that contains the exam id if this is a topic for a whole exam; an empty optional otherwise
     */
    public static Optional<Long> getExamIdFromExamRootDestination(String destination) {
        var matcher = EXAM_TOPIC_PATTERN.matcher(destination);
        if (matcher.matches()) {
            return Optional.of(Long.valueOf(matcher.group(1)));
        }
        return Optional.empty();
    }
}
