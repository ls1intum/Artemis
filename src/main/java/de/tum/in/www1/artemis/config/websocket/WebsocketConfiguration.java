package de.tum.in.www1.artemis.config.websocket;

import static de.tum.in.www1.artemis.service.WebsocketMessagingService.*;
import static de.tum.in.www1.artemis.web.websocket.team.ParticipationTeamWebsocketService.*;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.*;
import java.util.regex.Pattern;

import javax.servlet.http.Cookie;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
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
import org.springframework.web.util.WebUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterators;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.jwt.JWTFilter;
import de.tum.in.www1.artemis.security.jwt.TokenProvider;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.validation.InetSocketAddressValidator;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Configuration
// See https://stackoverflow.com/a/34337731/3802758
public class WebsocketConfiguration extends DelegatingWebSocketMessageBrokerConfiguration {

    private final Logger log = LoggerFactory.getLogger(WebsocketConfiguration.class);

    private static final Pattern EXAM_TOPIC_PATTERN = Pattern.compile("^/topic/exams/(\\d+)/.+$");

    public static final String IP_ADDRESS = "IP_ADDRESS";

    private final ObjectMapper objectMapper;

    private final TokenProvider tokenProvider;

    private final TaskScheduler messageBrokerTaskScheduler;

    private final StudentParticipationRepository studentParticipationRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;

    private final ExamRepository examRepository;

    // Split the addresses by comma
    @Value("#{'${spring.websocket.broker.addresses}'.split(',')}")
    private List<String> brokerAddresses;

    @Value("${spring.websocket.broker.username}")
    private String brokerUsername;

    @Value("${spring.websocket.broker.password}")
    private String brokerPassword;

    public WebsocketConfiguration(MappingJackson2HttpMessageConverter springMvcJacksonConverter, TaskScheduler messageBrokerTaskScheduler, TokenProvider tokenProvider,
            StudentParticipationRepository studentParticipationRepository, AuthorizationCheckService authorizationCheckService, ExerciseRepository exerciseRepository,
            UserRepository userRepository, ExamRepository examRepository) {
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
        this.tokenProvider = tokenProvider;
        this.studentParticipationRepository = studentParticipationRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.examRepository = examRepository;
    }

    @Override
    protected void configureMessageBroker(@NotNull MessageBrokerRegistry config) {
        // Try to create a TCP client that will connect to the message broker (or the message brokers if multiple exists).
        // If tcpClient is null, there is no valid address specified in the config. This could be due to a development setup or a mistake in the config.
        TcpOperations<byte[]> tcpClient = createTcpClient();
        if (tcpClient != null) {
            log.info("Enabling StompBrokerRelay for WebSocket messages using {}", String.join(", ", brokerAddresses));
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
                    // Set the TCP client to the one generated above
                    .setTcpClient(tcpClient);
        }
        else {
            log.info("Did NOT enable StompBrokerRelay for WebSocket messages");
            config.enableSimpleBroker("/topic").setHeartbeatValue(new long[] { 10000, 20000 }).setTaskScheduler(messageBrokerTaskScheduler);
        }
    }

    /**
     * Create a TCP client that will connect to the broker defined in the config.
     * If multiple brokers are configured, the client will connect to the first one and fail over to the next one in case a broker goes down.
     * If the last broker goes down, the first one is retried.
     * Also see https://github.com/spring-projects/spring-framework/issues/17057 and
     * https://docs.spring.io/spring/docs/current/spring-framework-reference/web.html#websocket-stomp-handle-broker-relay-configure
     * @return a TCP client with a round-robin use
     */
    private ReactorNettyTcpClient<byte[]> createTcpClient() {
        final List<InetSocketAddress> brokerAddressList = brokerAddresses.stream().map(InetSocketAddressValidator::getValidAddress).filter(Optional::isPresent).map(Optional::get)
                .toList();

        // Return null if no valid addresses can be found. This is e.g. due to an invalid config or a development setup without a broker.
        if (!brokerAddressList.isEmpty()) {
            // This provides a round-robin use of brokers, we only want to use the fallback broker if the primary broker fails, so we have the same order of brokers in all nodes
            var addressIterator = Iterators.cycle(brokerAddressList);
            return new ReactorNettyTcpClient<>(client -> client.remoteAddress(addressIterator::next), new StompReactorNettyCodec());
        }
        return null;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        DefaultHandshakeHandler handshakeHandler = defaultHandshakeHandler();
        // NOTE: by setting a WebSocketTransportHandler we disable http poll, http stream and other exotic workarounds and only support real websocket connections.
        // nowadays, all modern browsers support websockets and workarounds are not necessary anymore and might only lead to problems
        WebSocketTransportHandler webSocketTransportHandler = new WebSocketTransportHandler(handshakeHandler);
        registry.addEndpoint("/websocket/tracker").setAllowedOriginPatterns("*").withSockJS().setTransportHandlers(webSocketTransportHandler)
                .setInterceptors(httpSessionHandshakeInterceptor());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new TopicSubscriptionInterceptor());
    }

    @NotNull
    @Override
    protected MappingJackson2MessageConverter createJacksonConverter() {
        // NOTE: We need to adapt the default messageConverter for WebSocket messages
        // with a messageConverter that uses the same ObjectMapper that our REST endpoints use.
        // This gives us consistency in how specific data types are serialized (e.g. timestamps)
        MappingJackson2MessageConverter converter = super.createJacksonConverter();
        converter.setObjectMapper(objectMapper);
        return converter;
    }

    /**
     * @return initialize the handshake interceptor stores the remote IP address before handshake
     */
    @Bean
    public HandshakeInterceptor httpSessionHandshakeInterceptor() {
        return new HandshakeInterceptor() {

            @Override
            public boolean beforeHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler,
                    @NotNull Map<String, Object> attributes) {
                if (request instanceof ServletServerHttpRequest servletRequest) {
                    attributes.put(IP_ADDRESS, servletRequest.getRemoteAddress());
                    Cookie jwtCookie = WebUtils.getCookie(servletRequest.getServletRequest(), JWTFilter.JWT_COOKIE_NAME);
                    return JWTFilter.isJwtCookieValid(tokenProvider, jwtCookie);
                }
                return false;
            }

            @Override
            public void afterHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, Exception exception) {
                if (exception != null) {
                    log.warn("Exception occurred in WS.afterHandshake", exception);
                }
            }
        };
    }

    private DefaultHandshakeHandler defaultHandshakeHandler() {
        return new DefaultHandshakeHandler() {

            @Override
            protected Principal determineUser(@NotNull ServerHttpRequest request, @NotNull WebSocketHandler wsHandler, @NotNull Map<String, Object> attributes) {
                Principal principal = request.getPrincipal();
                if (principal == null) {
                    Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority(Role.ANONYMOUS.getAuthority()));
                    principal = new AnonymousAuthenticationToken("WebsocketConfiguration", "anonymous", authorities);
                }
                log.debug("determineUser: {}", principal);
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
        public Message<?> preSend(@NotNull Message<?> message, @NotNull MessageChannel channel) {
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
         *
         * @param principal User principal of the user who wants to subscribe
         * @param destination Destination topic to which the user wants to subscribe
         * @return flag whether subscription is allowed
         */
        private boolean allowSubscription(Principal principal, String destination) {
            if (isParticipationTeamDestination(destination)) {
                Long participationId = getParticipationIdFromDestination(destination);
                return isParticipationOwnedByUser(principal, participationId);
            }
            if (isNonPersonalExerciseResultDestination(destination)) {
                Long exerciseId = getExerciseIdFromNonPersonalExerciseResultDestination(destination);

                // TODO: Is it right that TAs are not allowed to subscribe to exam exercises?
                Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
                if (exercise.isExamExercise()) {
                    return isUserInstructorOrHigherForExercise(principal, exercise);
                }
                else {
                    return isUserTAOrHigherForExercise(principal, exercise);
                }
            }

            var examId = getExamIdFromExamRootDestination(destination);
            if (examId.isPresent()) {
                var exam = examRepository.findByIdElseThrow(examId.get());
                User user = userRepository.getUserWithGroupsAndAuthorities(principal.getName());
                return authorizationCheckService.isAtLeastInstructorInCourse(exam.getCourse(), user);
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
        StudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        return participation.isOwnedBy(principal.getName());
    }

    private boolean isUserInstructorOrHigherForExercise(Principal principal, Exercise exercise) {
        User user = userRepository.getUserWithGroupsAndAuthorities(principal.getName());
        return authorizationCheckService.isAtLeastInstructorInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user);
    }

    private boolean isUserTAOrHigherForExercise(Principal principal, Exercise exercise) {
        User user = userRepository.getUserWithGroupsAndAuthorities(principal.getName());
        return authorizationCheckService.isAtLeastTeachingAssistantForExercise(exercise, user);
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
