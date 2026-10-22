package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.FilterReply;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;

@ResourceLock("LoggingAspectLogger")
class LoggingAspectTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingAspect.class);

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private Level originalLevel;

    @BeforeEach
    void setUp() {
        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        String testThread = Thread.currentThread().getName();
        appender.addFilter(new Filter<>() {

            @Override
            public FilterReply decide(ILoggingEvent event) {
                return event.getThreadName().equals(testThread) ? FilterReply.ACCEPT : FilterReply.DENY;
            }
        });
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(originalLevel);
    }

    @ParameterizedTest
    @ValueSource(strings = { "No authorization header provided", "The username has to be provided" })
    void routineAuthenticationFailuresAreNotLogged(String message) {
        var joinPoint = mock(ProceedingJoinPoint.class);
        new LoggingAspect(new MockEnvironment()).logAfterThrowing(joinPoint, new LocalVCAuthException(message));
        assertThat(appender.list).isEmpty();
        verifyNoInteractions(joinPoint);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "credentials rejected" })
    void otherAuthenticationFailuresRemainVisible(String message) {
        new LoggingAspect(new MockEnvironment()).logAfterThrowing(joinPoint(), new LocalVCAuthException(message));
        assertThat(appender.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage()).contains("Exception in TestService.perform()"));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void developmentErrorsLogOuterExceptionWhileProductionLogsItsCause(boolean development) {
        MockEnvironment environment = new MockEnvironment();
        if (development) {
            environment.setActiveProfiles("dev");
        }
        var aspect = new LoggingAspect(environment);
        Throwable cause = new IllegalStateException("cause");
        aspect.logAfterThrowing(joinPoint(), new RuntimeException("failure", cause));
        aspect.logAfterThrowing(joinPoint(), new RuntimeException("without cause"));
        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.getFirst().getFormattedMessage()).contains("cause");
        assertThat(appender.list.getLast().getFormattedMessage()).contains("NULL");
        if (development) {
            assertThat(appender.list.getFirst().getThrowableProxy().getMessage()).isEqualTo("failure");
        }
        else {
            assertThat(appender.list.getFirst().getThrowableProxy().getMessage()).isEqualTo("cause");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void aroundAdvicePreservesReturnValueAndOnlyLogsArgumentsWhenDebugEnabled(boolean debug) throws Throwable {
        logger.setLevel(debug ? Level.DEBUG : Level.INFO);
        var joinPoint = joinPoint();
        Object result = new Object();
        when(joinPoint.proceed()).thenReturn(result);
        assertThat(new LoggingAspect(new MockEnvironment()).logAround(joinPoint)).isSameAs(result);
        assertThat(appender.list).hasSize(debug ? 2 : 0);
        if (debug) {
            assertThat(appender.list.getFirst().getFormattedMessage()).contains("Enter: TestService.perform()", "argument");
            assertThat(appender.list.getLast().getFormattedMessage()).contains("Exit: TestService.perform()");
        }
    }

    @Test
    void invalidArgumentIsLoggedAndPropagatedUnchanged() throws Throwable {
        var joinPoint = joinPoint();
        var failure = new IllegalArgumentException("bad request");
        when(joinPoint.proceed()).thenThrow(failure);
        assertThatThrownBy(() -> new LoggingAspect(new MockEnvironment()).logAround(joinPoint)).isSameAs(failure);
        assertThat(appender.list).anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("Illegal argument: [argument] in TestService.perform()"));
    }

    private ProceedingJoinPoint joinPoint() {
        var joinPoint = mock(ProceedingJoinPoint.class);
        var signature = mock(Signature.class);
        when(signature.getDeclaringTypeName()).thenReturn("TestService");
        when(signature.getName()).thenReturn("perform");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[] { "argument" });
        return joinPoint;
    }
}
