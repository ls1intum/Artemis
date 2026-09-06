package de.tum.cit.aet.artemis.videosource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.lang.reflect.Modifier;
import java.net.URI;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.core.config.LoggingAspect;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.videosource.repository.GocastApprovalAttemptRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastConnectionRepository;
import de.tum.cit.aet.artemis.videosource.repository.GocastCourseBindingRepository;

class GocastLoggingSafetyTest extends AbstractSpringIntegrationIndependentTest {

    private static final String BASE_URL = "http://localhost:18081/api/v2";

    private static final String STATE = "KioqKioqKioqKioqKioqKioqKioqKioqKioqKioqKio";

    private static final String CODE = "Q0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0NDQ0M";

    private static final String API_KEY_SENTINEL = "api-key-value-that-must-not-be-logged";

    private final List<LoggerState> loggerStates = new ArrayList<>();

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

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

        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL).defaultHeader("Authorization", "Bearer " + API_KEY_SENTINEL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/integration")).andRespond(withSuccess(
                "{\"id\":17,\"name\":\"Artemis\",\"returnUrl\":\"https://artemis.example/api/videosource/public/gocast/approval/callback\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/integration/authorizations/redeem"))
                .andExpect(content().json("{\"code\":\"%s\",\"state\":\"%s\"}".formatted(CODE, STATE), JsonCompareMode.STRICT))
                .andRespond(withSuccess("{\"grantId\":23,\"courseId\":37}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/integration/grants/23"))
                .andRespond(withSuccess("{\"courseId\":37,\"name\":\"Course\",\"slug\":\"course\",\"visibility\":\"public\"}", MediaType.APPLICATION_JSON));

        var environment = new MockEnvironment().withProperty("spring.profiles.active", "dev");
        GocastConnectorService connector = proxied(new GocastConnectorService(builder.build(), URI.create("http://localhost:18081")), environment);

        connector.getIntegration();
        connector.authorizationUrl(17, STATE);
        connector.redeemApproval(17, STATE, CODE);

        assertMessagesExclude(aspectAppender, API_KEY_SENTINEL, STATE, CODE);
        assertMessagesExclude(restClientAppender, API_KEY_SENTINEL, STATE, CODE);
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

        List<Class<?>> credentialBoundaryTypes = List.of(GocastConnectorService.class, GocastBindingService.class, GocastConnectionRepository.class,
                GocastApprovalAttemptRepository.class, GocastCourseBindingRepository.class, handlerType("/api/videosource/public/gocast/approval/callback"),
                handlerType("/api/videosource/courses/{courseId}/binding"));
        for (Class<?> type : credentialBoundaryTypes) {
            assertThat(List.of(type.getDeclaredMethods()).stream().filter(method -> Modifier.isPublic(method.getModifiers())).noneMatch(method -> pointcut.matches(method, type)))
                    .as("all public methods on %s are outside credential logging", type.getSimpleName()).isTrue();
        }
    }

    private Class<?> handlerType(String path) {
        return requestMappingHandlerMapping.getHandlerMethods().entrySet().stream().filter(entry -> entry.getKey().getPatternValues().contains(path))
                .map(entry -> entry.getValue().getBeanType()).findFirst().orElseThrow(() -> new AssertionError("No MVC handler registered for " + path));
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
