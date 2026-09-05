package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.core.config.LoggingAspect;
import de.tum.cit.aet.artemis.videosource.repository.GocastApprovalAttemptRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastConnectionRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastCourseBindingRepository;
import de.tum.cit.aet.artemis.videosource.web.GocastApprovalCallbackResource;
import de.tum.cit.aet.artemis.videosource.web.GocastIntegrationResource;

class GocastLoggingSafetyTest {

    private static final String BASE_URL = "http://localhost:18081/api/v2";

    private static final String REQUEST_ID = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private static final String STATE = "KioqKioqKioqKioqKioqKioqKioqKioqKioqKioqKio";

    private static final String CODE = "Q0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0M";

    private static final String PASSWORD_SENTINEL = "password-value-that-must-not-be-logged";

    private static final String TOKEN_SENTINEL = "token-value-that-must-not-be-logged";

    private final List<LoggerState> loggerStates = new ArrayList<>();

    @BeforeEach
    void setUp() {
        loggerStates.clear();
    }

    @AfterEach
    void restoreLoggers() {
        loggerStates.forEach(LoggerState::restore);
    }

    @Test
    void actualAspectAndRestClientDebugLoggingDoNotExposeCredentialsWhileJsonStaysUnchanged() {
        ListAppender<ILoggingEvent> aspectAppender = capture(LoggingAspect.class.getName());
        ListAppender<ILoggingEvent> restClientAppender = capture("org.springframework.web.client.DefaultRestClient");

        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/integration/login")).andExpect(content().json("{\"email\":\"artemis@example.org\",\"password\":\"%s\"}".formatted(PASSWORD_SENTINEL)))
                .andRespond(withSuccess("{\"accessToken\":\"%s\",\"tokenType\":\"Bearer\",\"expiresIn\":100,\"userId\":17}".formatted(TOKEN_SENTINEL), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/integration/approval-requests")).andExpect(content()
                .json("{\"state\":\"%s\",\"courseLabel\":\"Course\",\"callbackUrl\":\"https://artemis.example/api/videosource/public/gocast/approval/callback\"}".formatted(STATE)))
                .andRespond(withSuccess("{\"requestId\":\"%s\",\"approvalUrl\":\"http://localhost:18081/integration/approve/%s\",\"expiresAt\":\"2026-09-05T03:15:00Z\"}"
                        .formatted(REQUEST_ID, REQUEST_ID), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/integration/approval-requests/" + REQUEST_ID + "/redeem"))
                .andExpect(content().json("{\"state\":\"%s\",\"code\":\"%s\"}".formatted(STATE, CODE)))
                .andRespond(withSuccess(
                        "{\"requestId\":\"%s\",\"state\":\"%s\",\"serviceUserId\":17,\"grantId\":23,\"courseId\":37,\"courseSlug\":\"course\",\"courseName\":\"Course\",\"courseVisibility\":\"public\"}"
                                .formatted(REQUEST_ID, STATE),
                        MediaType.APPLICATION_JSON));

        var environment = new MockEnvironment().withProperty("spring.profiles.active", "dev");
        GocastAuthenticationService authentication = proxied(
                new GocastAuthenticationService(builder.build(), "artemis@example.org", PASSWORD_SENTINEL, Clock.fixed(Instant.parse("2026-09-05T03:00:00Z"), ZoneOffset.UTC)),
                environment);
        GocastConnectorService connector = proxied(new GocastConnectorService(builder.build(), authentication, URI.create("http://localhost:18081"),
                Clock.fixed(Instant.parse("2026-09-05T03:00:00Z"), ZoneOffset.UTC)), environment);

        connector.createApproval(STATE, "Course", "https://artemis.example/api/videosource/public/gocast/approval/callback");
        connector.redeemApproval(REQUEST_ID, STATE, CODE);

        assertMessagesExclude(aspectAppender, PASSWORD_SENTINEL, TOKEN_SENTINEL, STATE, CODE, REQUEST_ID);
        assertMessagesExclude(restClientAppender, PASSWORD_SENTINEL, TOKEN_SENTINEL, STATE, CODE, REQUEST_ID);
        assertThat(restClientAppender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("Writing [LoginRequest[") && message.contains("password=[REDACTED]")))
                .as("RestClient DEBUG captures the redacted login request").isTrue();
        assertThat(restClientAppender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("Writing [CreateApprovalRequest[") && message.contains("state=[REDACTED]")))
                .as("RestClient DEBUG captures the redacted approval request").isTrue();
        assertThat(restClientAppender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("Writing [RedeemRequest[") && message.contains("code=[REDACTED]")))
                .as("RestClient DEBUG captures the redacted redeem request").isTrue();
        server.verify();
    }

    @Test
    void allGocastCredentialBoundaryMethodsAreExcludedFromArgumentAndResultLogging() throws NoSuchMethodException {
        String expression = LoggingAspect.class.getMethod("logAround", ProceedingJoinPoint.class).getAnnotation(Around.class).value();
        var pointcut = new AspectJExpressionPointcut();
        pointcut.setPointcutDeclarationScope(LoggingAspect.class);
        pointcut.setExpression(expression);

        List<Class<?>> credentialBoundaryTypes = List.of(GocastAuthenticationService.class, GocastConnectorService.class, GocastBindingService.class,
                GocastConnectionRepository.class, GocastApprovalAttemptRepository.class, GocastCourseBindingRepository.class, GocastApprovalCallbackResource.class,
                GocastIntegrationResource.class);
        for (Class<?> type : credentialBoundaryTypes) {
            assertThat(List.of(type.getDeclaredMethods()).stream().filter(method -> Modifier.isPublic(method.getModifiers())).noneMatch(method -> pointcut.matches(method, type)))
                    .as("all public methods on %s are outside credential logging", type.getSimpleName()).isTrue();
        }
    }

    private ListAppender<ILoggingEvent> capture(String loggerName) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        loggerStates.add(new LoggerState(logger, logger.getLevel(), logger.isAdditive(), appender));
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        logger.addAppender(appender);
        return appender;
    }

    private static void assertMessagesExclude(ListAppender<ILoggingEvent> appender, String... forbiddenValues) {
        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        for (String forbiddenValue : forbiddenValues) {
            assertThat(messages.stream().noneMatch(message -> message.contains(forbiddenValue))).as("captured logs exclude a credential sentinel").isTrue();
        }
    }

    private static <T> T proxied(T target, MockEnvironment environment) {
        var factory = new AspectJProxyFactory(target);
        factory.addAspect(new LoggingAspect(environment));
        @SuppressWarnings("unchecked")
        T proxy = (T) factory.getProxy();
        return proxy;
    }

    private record LoggerState(Logger logger, Level level, boolean additive, ListAppender<ILoggingEvent> appender) {

        private void restore() {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(level);
            logger.setAdditive(additive);
        }
    }
}
