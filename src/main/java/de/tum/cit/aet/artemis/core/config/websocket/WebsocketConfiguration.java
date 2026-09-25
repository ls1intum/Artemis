package de.tum.cit.aet.artemis.core.config.websocket;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompReactorNettyCodec;
import org.springframework.messaging.tcp.TcpOperations;
import org.springframework.messaging.tcp.reactor.ReactorNettyTcpClient;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.DelegatingWebSocketMessageBrokerConfiguration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.sockjs.transport.handler.WebSocketTransportHandler;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.config.InetSocketAddressValidator;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.jwt.JWTFilter;
import de.tum.cit.aet.artemis.core.security.jwt.JwtWithSource;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketSubscriptionInterceptor;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicRegistry;

@Profile(PROFILE_CORE)
@Configuration
// We cannot make this lazy as the client then fails to subscribe to team participation topics
@Lazy(value = false)
// See https://stackoverflow.com/a/34337731/3802758
public class WebsocketConfiguration extends DelegatingWebSocketMessageBrokerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WebsocketConfiguration.class);

    public static final String IP_ADDRESS = "IP_ADDRESS";

    /**
     * The prefix of the destinations clients send messages to. They are handled by {@code @MessageMapping} methods, which check the permissions of the sender themselves,
     * and never reach the broker, so a client cannot publish to a {@code /topic} destination.
     */
    public static final String APPLICATION_DESTINATION_PREFIX = "/app";

    private final JsonMapper jsonMapper;

    private final TokenProvider tokenProvider;

    private final TaskScheduler messageBrokerTaskScheduler;

    /**
     * Resolved when the first subscription arrives rather than injected: this class is eager, so reaching for the registry directly would pull it and the topic providers
     * with their repositories into the startup graph.
     */
    private final ObjectProvider<WebsocketTopicRegistry> websocketTopicRegistry;

    // Split the addresses by comma
    @Value("#{'${spring.websocket.broker.addresses}'.split(',')}")
    private List<String> brokerAddresses;

    @Value("${spring.websocket.broker.username}")
    private String brokerUsername;

    @Value("${spring.websocket.broker.password}")
    private String brokerPassword;

    public WebsocketConfiguration(JsonMapper jsonMapper, TaskScheduler messageBrokerTaskScheduler, TokenProvider tokenProvider,
            ObjectProvider<WebsocketTopicRegistry> websocketTopicRegistry) {
        this.jsonMapper = jsonMapper;
        this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
        this.tokenProvider = tokenProvider;
        this.websocketTopicRegistry = websocketTopicRegistry;
    }

    @Override
    protected void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        // Client messages only go to the @MessageMapping handlers. Without a prefix they would also reach the broker, which forwards them to every subscriber.
        config.setApplicationDestinationPrefixes(APPLICATION_DESTINATION_PREFIX);
        // The user registry has to know a subscription before other listeners of the subscribe event ask it, e.g. for the online members of a team
        config.setUserRegistryOrder(Ordered.HIGHEST_PRECEDENCE);
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
        GzipMessageConverter gzipMessageConverter = new GzipMessageConverter(jsonMapper);
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
        registry.setErrorHandler(new DroppingAccessDeniedErrorHandler());
    }

    /**
     * Drops a SEND or SUBSCRIBE frame that the frame-level rules in {@link WebsocketSecurityConfiguration} reject, instead of answering with an ERROR frame, which closes
     * the connection. A client that still sends to an old destination, e.g. an open tab from before a deployment, would otherwise lose all its live updates and reconnect
     * over and over. Every other error keeps the default handling.
     */
    private static class DroppingAccessDeniedErrorHandler extends StompSubProtocolErrorHandler {

        @Override
        public @Nullable Message<byte[]> handleClientMessageProcessingError(@Nullable Message<byte[]> clientMessage, @NonNull Throwable exception) {
            StompCommand command = clientMessage != null ? StompHeaderAccessor.wrap(clientMessage).getCommand() : null;
            if ((command == StompCommand.SEND || command == StompCommand.SUBSCRIBE) && isAccessDenied(exception)) {
                log.warn("Dropped a {} frame to {} that the websocket rules do not allow", command, StompHeaderAccessor.wrap(clientMessage).getDestination());
                return null;
            }
            return super.handleClientMessageProcessingError(clientMessage, exception);
        }

        private static boolean isAccessDenied(Throwable exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof AccessDeniedException) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Applies the other configurers first, among them Spring Security's, which authenticates every frame and restricts the message types and destinations a client
        // may use (see WebsocketSecurityConfiguration).
        super.configureClientInboundChannel(registration);
        // Decides which subscriptions are allowed, based on the declared websocket topics (see WebsocketTopic)
        registration.interceptors(new WebsocketSubscriptionInterceptor(websocketTopicRegistry::getObject));
        registration.taskExecutor(createExecutor("ws-inbound-"));
    }

    @Override
    protected void configureClientOutboundChannel(ChannelRegistration registration) {
        super.configureClientOutboundChannel(registration);
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
}
