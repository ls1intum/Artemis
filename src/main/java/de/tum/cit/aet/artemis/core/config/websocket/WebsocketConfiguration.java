package de.tum.cit.aet.artemis.core.config.websocket;

import static de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService.getExerciseIdFromNonPersonalExerciseResultDestination;
import static de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService.isNonPersonalExerciseResultDestination;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.exercise.web.ParticipationTeamWebsocketService.getParticipationIdFromDestination;
import static de.tum.cit.aet.artemis.exercise.web.ParticipationTeamWebsocketService.isParticipationTeamDestination;
import static de.tum.cit.aet.artemis.localci.service.LocalCIWebsocketMessagingService.isBuildAgentDestination;
import static de.tum.cit.aet.artemis.localci.service.LocalCIWebsocketMessagingService.isBuildJobAdminDestination;
import static de.tum.cit.aet.artemis.localci.service.LocalCIWebsocketMessagingService.isBuildJobCourseDestination;
import static de.tum.cit.aet.artemis.localci.service.LocalCIWebsocketMessagingService.isBuildQueueAdminDestination;
import static de.tum.cit.aet.artemis.localci.service.LocalCIWebsocketMessagingService.isBuildQueueCourseDestination;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompReactorNettyCodec;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.tcp.TcpOperations;
import org.springframework.messaging.tcp.reactor.ReactorNettyTcpClient;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.DelegatingWebSocketMessageBrokerConfiguration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.sockjs.transport.handler.WebSocketTransportHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.config.InetSocketAddressValidator;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.jwt.JWTFilter;
import de.tum.cit.aet.artemis.core.security.jwt.JwtWithSource;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exam.api.StudentExamApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismCaseApi;
import de.tum.cit.aet.artemis.quiz.repository.QuizBatchRepository;

@Profile(PROFILE_CORE)
@Configuration
// We cannot make this lazy as the client then fails to subscribe to team participation topics
@Lazy(value = false)
// See https://stackoverflow.com/a/34337731/3802758
public class WebsocketConfiguration extends DelegatingWebSocketMessageBrokerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WebsocketConfiguration.class);

    private static final Pattern EXAM_TOPIC_PATTERN = Pattern.compile("^/topic/exams/(\\d+)/.+$");

    /**
     * The signals that a student started or submitted an exam ({@code /topic/exam/{examId}/started|submitted}), which the exam overview of the course staff counts.
     */
    private static final Pattern EXAM_PROGRESS_TOPIC_PATTERN = Pattern.compile("^/topic/exam/(\\d+)/.+$");

    private static final Pattern EXERCISE_SYNCHRONIZATION_TOPIC_PATTERN = Pattern.compile("^/topic/exercises/(\\d+)/synchronization$");

    /**
     * The only destinations that can be subscribed: topics made of plain path segments. This excludes the wildcards of the simple broker ({@code *}, {@code ?}, {@code {}})
     * and of an external broker ({@code *}, {@code #}, {@code >}), and the queue syntax of an external broker ({@code address::queue}), which would all reach other topics
     * than the one that was checked.
     */
    private static final Pattern SUBSCRIPTION_DESTINATION_PATTERN = Pattern.compile("^/(?:user/)?topic/[A-Za-z0-9._/-]+$");

    /**
     * Destinations that only the server uses: the broadcasts between the nodes when an external broker is configured, and the destinations a user destination of a
     * session resolves to ({@code <destination>-user<sessionId>}).
     */
    private static final Pattern BROKER_INTERNAL_DESTINATION_PATTERN = Pattern.compile("^/topic/(unresolved-user|user-registry)$|-user[^/]*$");

    private static final String USER_TOPIC_PREFIX = "/topic/user/";

    private static final Pattern USER_TOPIC_PATTERN = Pattern.compile("^/topic/user/(\\d+)/.+$");

    private static final String ADMIN_TOPIC_PREFIX = "/topic/admin/";

    private static final Pattern COURSE_WIDE_POSTS_PATTERN = Pattern.compile("^/topic/(?:communication|metis)/courses/(\\d+)$");

    private static final Pattern PLAGIARISM_CASE_POSTS_PATTERN = Pattern.compile("^/topic/(?:communication|metis)/plagiarismCase/(\\d+)$");

    private static final Pattern STUDENT_EXAM_EVENTS_PATTERN = Pattern.compile("^/topic/exam-participation/studentExam/(\\d+)/events$");

    private static final Pattern EXAM_EVENTS_PATTERN = Pattern.compile("^/topic/exam-participation/exam/(\\d+)/events$");

    private static final Pattern COURSE_QUIZ_EXERCISES_PATTERN = Pattern.compile("^/topic/courses/(\\d+)/quizExercises$");

    private static final Pattern QUIZ_BATCH_PATTERN = Pattern.compile("^/topic/courses/\\d+/quizExercises/(\\d+)$");

    private static final Pattern QUIZ_STATISTICS_PATTERN = Pattern.compile("^/topic/statistic/(\\d+)$");

    private static final Pattern EXERCISE_BUILDS_PATTERN = Pattern.compile("^/topic/exercise/(\\d+)/(?:newSubmissions|submissionProcessing)$");

    private static final Pattern PROGRAMMING_EXERCISE_STAFF_PATTERN = Pattern
            .compile("^/topic/programming-exercises/(\\d+)/(?:test-cases|test-cases-changed|all-builds-triggered)$");

    private static final Pattern PLAGIARISM_CHECK_PATTERN = Pattern.compile("^/topic/(?:programming|text)-exercises/(\\d+)/plagiarism-check$");

    private static final Pattern COURSE_OPERATION_PROGRESS_PATTERN = Pattern.compile("^/topic/courses/(\\d+)/operation-progress$");

    private static final Pattern COURSE_ARCHIVE_PATTERN = Pattern.compile("^/topic/courses/(\\d+)/export-course$");

    private static final Pattern LECTURE_PROCESSING_STATE_PATTERN = Pattern.compile("^/topic/lectures/(\\d+)/unit-processing-state$");

    private static final Pattern ORCHESTRATION_SUMMARY_PATTERN = Pattern.compile("^/topic/atlas/orchestrator/(\\d+)$");

    /**
     * The Android app sends the answers of a live quiz to this destination and waits for the receipt of the broker. The server has not read these messages for a long
     * time, so they are accepted but nobody may subscribe to this topic, see {@link #QUIZ_SUBMISSION_TOPIC_PREFIX}.
     */
    private static final Pattern QUIZ_SUBMISSION_MESSAGE_PATTERN = Pattern.compile("^/topic/quizExercise/\\d+/submission$");

    private static final String QUIZ_SUBMISSION_TOPIC_PREFIX = "/topic/quizExercise/";

    /**
     * The destinations clients send team synchronization messages to, handled by {@code ParticipationTeamWebsocketService}.
     */
    private static final Pattern TEAM_MESSAGE_PATTERN = Pattern
            .compile("^/topic/participations/(\\d+)/team/(trigger|typing|modeling-submissions/update|modeling-submissions/patch|text-submissions/update)$");

    public static final String IP_ADDRESS = "IP_ADDRESS";

    private final ObjectMapper objectMapper;

    private final TokenProvider tokenProvider;

    private final TaskScheduler messageBrokerTaskScheduler;

    private final StudentParticipationRepository studentParticipationRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ExerciseRepository exerciseRepository;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    // Resolved on the first subscription rather than injected: this class is eager, and these beans are not needed before somebody subscribes
    private final ObjectProvider<UserRepository> userRepository;

    private final ObjectProvider<QuizBatchRepository> quizBatchRepository;

    private final ObjectProvider<StudentExamApi> studentExamApi;

    private final ObjectProvider<PlagiarismCaseApi> plagiarismCaseApi;

    private final ObjectProvider<SimpUserRegistry> simpUserRegistry;

    // Split the addresses by comma
    @Value("#{'${spring.websocket.broker.addresses}'.split(',')}")
    private List<String> brokerAddresses;

    @Value("${spring.websocket.broker.username}")
    private String brokerUsername;

    @Value("${spring.websocket.broker.password}")
    private String brokerPassword;

    public WebsocketConfiguration(MappingJackson2HttpMessageConverter springMvcJacksonConverter, TaskScheduler messageBrokerTaskScheduler, TokenProvider tokenProvider,
            StudentParticipationRepository studentParticipationRepository, AuthorizationCheckService authorizationCheckService, ExerciseRepository exerciseRepository,
            Optional<ExamRepositoryApi> examRepositoryApi, ObjectProvider<UserRepository> userRepository, ObjectProvider<QuizBatchRepository> quizBatchRepository,
            ObjectProvider<StudentExamApi> studentExamApi, ObjectProvider<PlagiarismCaseApi> plagiarismCaseApi, ObjectProvider<SimpUserRegistry> simpUserRegistry) {
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
        this.tokenProvider = tokenProvider;
        this.studentParticipationRepository = studentParticipationRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.exerciseRepository = exerciseRepository;
        this.examRepositoryApi = examRepositoryApi;
        this.userRepository = userRepository;
        this.quizBatchRepository = quizBatchRepository;
        this.studentExamApi = studentExamApi;
        this.plagiarismCaseApi = plagiarismCaseApi;
        this.simpUserRegistry = simpUserRegistry;
    }

    @Override
    protected void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        // Try to create a TCP client that will connect to the message broker (or the message brokers if multiple exists).
        // If tcpClient is null, there is no valid address specified in the config. This could be due to a development setup or a mistake in the config.
        TcpOperations<byte[]> tcpClient = websocketBrokerTcpClientSupplier().get();
        if (tcpClient != null) {
            log.debug("Enabling StompBrokerRelay for WebSocket messages using {}", String.join(", ", brokerAddresses));
            config
                    // Enable the relay for "/topic"
                    .enableStompBrokerRelay("/topic")
                    // Messages that could not be sent to a user (as they are not connected to this server) will be forwarded to "/topic/unresolved-user"
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
                    .setTcpClient(tcpClient)
                    // Use the custom task scheduler for the heartbeat messages
                    .setTaskScheduler(messageBrokerTaskScheduler);
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
    private TcpOperations<byte[]> createTcpClient() {
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
            log.debug("STOMP relay connecting to broker[{}] {}:{}", i, addr.getHostString(), addr.getPort());
            return addr;
        }), new StompReactorNettyCodec());
    }

    @Bean(name = "websocketBrokerTcpClientSupplier")
    public Supplier<TcpOperations<byte[]>> websocketBrokerTcpClientSupplier() {
        return this::createTcpClient;
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
        registration.taskExecutor(createExecutor("ws-inbound-"));
    }

    @Override
    protected void configureClientOutboundChannel(ChannelRegistration registration) {
        int cores = Runtime.getRuntime().availableProcessors();
        registration.taskExecutor(createExecutor("ws-outbound-"));
    }

    /**
     * Creates and configures a thread pool executor for websocket message handling.
     *
     * @param threadNamePrefix the prefix to use for the executor thread names, distinguishing inbound and outbound channels
     * @return a configured {@link ThreadPoolTaskExecutor} ready for websocket channel registration
     */
    public ThreadPoolTaskExecutor createExecutor(String threadNamePrefix) {
        int cores = Runtime.getRuntime().availableProcessors();
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(cores * 2);
        exec.setMaxPoolSize(cores * 4); // allow short bursts with more threads
        exec.setQueueCapacity(10_000);
        exec.setKeepAliveSeconds(60);
        exec.setThreadNamePrefix(threadNamePrefix);
        exec.initialize();
        return exec;
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(15_000)           // ms – disconnect if we can’t send within 15s
                .setSendBufferSizeLimit(512 * 1024) // bytes – per-session buffer limit
                .setTimeToFirstMessage(20_000);     // give clients 20s to send first frame
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
                    // If the user is not found (e.g. because they are not logged in), they should not be able to subscribe to these topics
                    log.warn("An error occurred while subscribing user {} to destination {}: {}", principal != null ? principal.getName() : "null", destination, e.getMessage());
                    return null;
                }
                catch (RuntimeException e) {
                    // e.g. an id that does not fit into a long; the subscription is rejected, the connection stays open
                    log.warn("Could not check the subscription of {} to {}: {}", principal != null ? principal.getName() : "null", destination, e.getMessage());
                    return null;
                }
            }
            else if (SimpMessageType.MESSAGE.equals(headerAccessor.getMessageType())) {
                boolean allowed;
                try {
                    // Clients send SEND frames. A MESSAGE frame is only ever sent by the server, but the broker would forward one from a client as well.
                    allowed = StompCommand.SEND.equals(headerAccessor.getCommand()) && allowSend(principal, destination, headerAccessor.getSessionId());
                }
                catch (RuntimeException e) {
                    log.warn("Could not check the message of {} to {}: {}", principal != null ? principal.getName() : "null", destination, e.getMessage());
                    allowed = false;
                }
                if (!allowed) {
                    // Without this, the broker would forward the message to every subscriber of the destination
                    log.warn("Dropped a message of {} to {}", principal != null ? principal.getName() : "anonymous", destination);
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

            if (destination == null || !SUBSCRIPTION_DESTINATION_PATTERN.matcher(destination).matches() || BROKER_INTERNAL_DESTINATION_PATTERN.matcher(destination).find()) {
                // A subscription has to name exactly one topic that the server sends to
                return false;
            }

            final var login = principal.getName();

            if (destination.startsWith(USER_TOPIC_PREFIX)) {
                // A personal topic is only ever subscribed by the user whose id it contains
                return isOwnUserTopic(login, destination);
            }

            if (destination.startsWith(QUIZ_SUBMISSION_TOPIC_PREFIX)) {
                // The server sends nothing here; a subscriber would only receive the quiz answers other students send
                return false;
            }

            if (destination.startsWith(ADMIN_TOPIC_PREFIX)) {
                // All administrator topics, including the details of a single build agent
                return authorizationCheckService.isAdmin(login);
            }

            if (isBuildQueueAdminDestination(destination) || isBuildAgentDestination(destination) || isBuildJobAdminDestination(destination)) {
                return authorizationCheckService.isAdmin(login);
            }

            Optional<Long> courseId = isBuildQueueCourseDestination(destination);
            if (courseId.isPresent()) {
                return authorizationCheckService.isAtLeastInstructorInCourse(login, courseId.get());
            }

            Optional<Long> buildJobCourseId = isBuildJobCourseDestination(destination);
            if (buildJobCourseId.isPresent()) {
                return authorizationCheckService.isAtLeastInstructorInCourse(login, buildJobCourseId.get());
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

            var synchronizationExerciseId = getExerciseIdFromSynchronizationDestination(destination);
            if (synchronizationExerciseId.isPresent()) {
                return authorizationCheckService.isAtLeastEditorInExercise(login, synchronizationExerciseId.get());
            }

            return allowTopicWithCourseData(login, destination).orElse(true);
        }

        /**
         * Checks the topics that carry course, exam or exercise data, for the topics not handled above.
         *
         * @param login       the login of the subscriber
         * @param destination the destination of the subscription
         * @return whether the subscription is allowed, or empty if the destination is none of these topics
         */
        private Optional<Boolean> allowTopicWithCourseData(String login, String destination) {
            Matcher matcher;
            if ((matcher = COURSE_WIDE_POSTS_PATTERN.matcher(destination)).matches() || (matcher = COURSE_QUIZ_EXERCISES_PATTERN.matcher(destination)).matches()) {
                return Optional.of(userRepository.getObject().isAtLeastStudentInCourse(login, id(matcher)));
            }
            if ((matcher = PLAGIARISM_CASE_POSTS_PATTERN.matcher(destination)).matches()) {
                return Optional.of(isPartyOfPlagiarismCase(login, id(matcher)));
            }
            if ((matcher = STUDENT_EXAM_EVENTS_PATTERN.matcher(destination)).matches()) {
                StudentExamApi api = studentExamApi.getIfAvailable();
                return Optional.of(api != null && api.isOwnerOfStudentExam(id(matcher), login));
            }
            if ((matcher = EXAM_EVENTS_PATTERN.matcher(destination)).matches()) {
                long examId = id(matcher);
                StudentExamApi api = studentExamApi.getIfAvailable();
                return Optional.of(api != null && api.hasStudentExamInExam(examId, login) || isInstructorOfExam(login, examId));
            }
            if ((matcher = EXAM_PROGRESS_TOPIC_PATTERN.matcher(destination)).matches()) {
                return Optional.of(isAtLeastTutorOfExam(login, id(matcher)));
            }
            if ((matcher = QUIZ_BATCH_PATTERN.matcher(destination)).matches()) {
                return Optional.of(mayFollowQuizBatch(login, id(matcher)));
            }
            if ((matcher = QUIZ_STATISTICS_PATTERN.matcher(destination)).matches() || (matcher = PROGRAMMING_EXERCISE_STAFF_PATTERN.matcher(destination)).matches()) {
                return Optional.of(authorizationCheckService.isAtLeastTeachingAssistantInExercise(login, id(matcher)));
            }
            if ((matcher = EXERCISE_BUILDS_PATTERN.matcher(destination)).matches()) {
                // template and solution builds: tutors of a course exercise, editors of an exam exercise
                long exerciseId = id(matcher);
                boolean allowed = exerciseRepository.isExamExercise(exerciseId) ? authorizationCheckService.isAtLeastEditorInExercise(login, exerciseId)
                        : authorizationCheckService.isAtLeastTeachingAssistantInExercise(login, exerciseId);
                return Optional.of(allowed);
            }
            if ((matcher = PLAGIARISM_CHECK_PATTERN.matcher(destination)).matches()) {
                return Optional.of(authorizationCheckService.isAtLeastEditorInExercise(login, id(matcher)));
            }
            if ((matcher = COURSE_OPERATION_PROGRESS_PATTERN.matcher(destination)).matches() || (matcher = ORCHESTRATION_SUMMARY_PATTERN.matcher(destination)).matches()) {
                return Optional.of(authorizationCheckService.isAtLeastTeachingAssistantInCourse(login, id(matcher)));
            }
            if ((matcher = COURSE_ARCHIVE_PATTERN.matcher(destination)).matches()) {
                return Optional.of(authorizationCheckService.isAtLeastInstructorInCourse(login, id(matcher)));
            }
            if ((matcher = LECTURE_PROCESSING_STATE_PATTERN.matcher(destination)).matches()) {
                return Optional.of(userRepository.getObject().isAtLeastEditorInLecture(login, id(matcher)));
            }
            return Optional.empty();
        }

        /**
         * Clients may only send the messages the server handles. Without this check, the broker would forward any message sent to a topic to all its subscribers.
         *
         * @param principal   the sender
         * @param destination the destination of the message
         * @param sessionId   the websocket session of the sender
         * @return whether the message may pass
         */
        private boolean allowSend(@Nullable Principal principal, @Nullable String destination, @Nullable String sessionId) {
            if (principal == null || destination == null) {
                return false;
            }
            Matcher team = TEAM_MESSAGE_PATTERN.matcher(destination);
            if (team.matches()) {
                // Submission updates and patches check the team membership in their handlers; the online list and typing indicator do not
                boolean checkedByHandler = team.group(2).startsWith("modeling-submissions") || team.group(2).startsWith("text-submissions");
                return checkedByHandler || studentParticipationRepository.existsByIdAndParticipatingStudentLogin(id(team), principal.getName());
            }
            if (QUIZ_SUBMISSION_MESSAGE_PATTERN.matcher(destination).matches()) {
                return true;
            }
            var synchronizationExerciseId = getExerciseIdFromSynchronizationDestination(destination);
            if (synchronizationExerciseId.isPresent()) {
                // Editors synchronize through the broker. Subscribing to this topic already required the editor role, so the subscription is checked first.
                return isSubscribed(principal.getName(), sessionId, destination)
                        || authorizationCheckService.isAtLeastEditorInExercise(principal.getName(), synchronizationExerciseId.get());
            }
            return false;
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
     * Returns whether the given destination is a personal topic of the user with the given login, e.g. {@code /topic/user/{userId}/notifications/conversations}. The id
     * is compared as text, so a destination whose id has leading zeros or does not fit into a long never matches.
     */
    private boolean isOwnUserTopic(String login, String destination) {
        Matcher matcher = USER_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            return false;
        }
        String requestedUserId = matcher.group(1);
        return userRepository.getObject().findIdByLogin(login).map(String::valueOf).filter(requestedUserId::equals).isPresent();
    }

    /**
     * The student or team a plagiarism case is about, and the instructors of its course.
     */
    private boolean isPartyOfPlagiarismCase(String login, long plagiarismCaseId) {
        PlagiarismCaseApi api = plagiarismCaseApi.getIfAvailable();
        if (api == null) {
            return false;
        }
        return api.isStudentOrTeamMemberOfPlagiarismCase(plagiarismCaseId, login)
                || api.findCourseIdOfPlagiarismCase(plagiarismCaseId).filter(courseId -> authorizationCheckService.isAtLeastInstructorInCourse(login, courseId)).isPresent();
    }

    private boolean isInstructorOfExam(String login, long examId) {
        return authorizationCheckService.isAtLeastInstructorInCourse(login, findCourseIdOfExam(examId));
    }

    private boolean isAtLeastTutorOfExam(String login, long examId) {
        return authorizationCheckService.isAtLeastTeachingAssistantInCourse(login, findCourseIdOfExam(examId));
    }

    private long findCourseIdOfExam(long examId) {
        ExamRepositoryApi api = examRepositoryApi.orElseThrow(() -> new ExamApiNotPresentException(ExamRepositoryApi.class));
        return api.findByIdElseThrow(examId).getCourse().getId();
    }

    /**
     * The students who joined a batch of a batched quiz receive its questions when the batch starts, and tutors may follow it as well. Students of a synchronized quiz
     * also wait on the topic of its single batch, although the server announces the start of such a quiz on the course topic.
     */
    private boolean mayFollowQuizBatch(String login, long quizBatchId) {
        QuizBatchRepository repository = quizBatchRepository.getObject();
        if (repository.existsByIdAndJoinedStudentLogin(quizBatchId, login)) {
            return true;
        }
        Optional<Long> quizExerciseId = repository.findQuizExerciseIdById(quizBatchId);
        if (quizExerciseId.isEmpty()) {
            return false;
        }
        if (repository.existsByIdAndSynchronizedQuizExercise(quizBatchId)) {
            return userRepository.getObject().isAtLeastStudentInExercise(login, quizExerciseId.get());
        }
        return authorizationCheckService.isAtLeastTeachingAssistantInExercise(login, quizExerciseId.get());
    }

    private boolean isSubscribed(String login, @Nullable String sessionId, String destination) {
        SimpUser user = simpUserRegistry.getObject().getUser(login);
        SimpSession session = user != null && sessionId != null ? user.getSession(sessionId) : null;
        return session != null && session.getSubscriptions().stream().anyMatch(subscription -> destination.equals(subscription.getDestination()));
    }

    private static long id(Matcher matcher) {
        return Long.parseLong(matcher.group(1));
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

    /**
     * Returns the exercise id if the given destination belongs to the exercise synchronization topic.
     *
     * @param destination websocket destination topic to inspect
     * @return an optional containing the exercise id for synchronization topics; empty otherwise
     */
    public static Optional<Long> getExerciseIdFromSynchronizationDestination(String destination) {
        var matcher = EXERCISE_SYNCHRONIZATION_TOPIC_PATTERN.matcher(destination);
        if (matcher.matches()) {
            return Optional.of(Long.valueOf(matcher.group(1)));
        }
        return Optional.empty();
    }
}
