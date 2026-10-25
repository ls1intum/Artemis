package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import io.sentry.Breadcrumb;
import io.sentry.CustomSamplingContext;
import io.sentry.Hint;
import io.sentry.SamplingContext;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.TransactionContext;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.User;

class SentryConfigurationCallbacksTest {

    @Test
    void absentAndEmptyDsnNeverInitializeSentry() {
        try (var sentry = mockStatic(Sentry.class)) {
            configuration(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()).init();
            configuration(Optional.of(""), Optional.empty(), Optional.empty(), Optional.empty()).init();
            sentry.verifyNoInteractions();
        }
    }

    @Test
    void initializationFailureDoesNotPreventApplicationStartup() {
        try (var sentry = mockStatic(Sentry.class)) {
            sentry.when(() -> Sentry.init(any(Sentry.OptionsConfiguration.class))).thenThrow(new IllegalArgumentException("invalid DSN"));
            assertThatCode(() -> configuration(Optional.of("invalid"), Optional.empty(), Optional.empty(), Optional.empty()).init()).doesNotThrowAnyException();
        }
    }

    @ParameterizedTest
    @CsvSource({ "' PrOD ',,prod,0.05", "staging,,staging,1.0", "test-node,,test-node,1.0", "custom,,custom,0.0", "'',true,test,1.0", "'',false,prod,0.05", "'',,local,0.0" })
    void initializesEnvironmentReleaseAndDefaultSampling(String environment, Boolean testServer, String expectedEnvironment, double expectedRate) {
        var options = options(configuration(Optional.of("https://key@example.invalid/1"), Optional.ofNullable(environment), Optional.ofNullable(testServer), Optional.empty()));
        assertThat(options.getEnvironment()).isEqualTo(expectedEnvironment);
        assertThat(options.getRelease()).isEqualTo("test-release");
        assertThat(options.getTracesSampleRate()).isEqualTo(expectedRate);
        assertThat(options.getDsn()).endsWith("?stacktrace.app.packages=de.tum.cit.aet.artemis");
    }

    @Test
    void samplerFiltersNoisyEndpointsAndHonoursParentDecisions() {
        var options = options(configuration(Optional.of("https://key@example.invalid/1"), Optional.of("prod"), Optional.empty(), Optional.of(0.2)));
        assertThat(options.getTracesSampleRate()).isEqualTo(0.2);
        assertThat(sample(options, null, null, null, null)).isEqualTo(0.2);
        assertThat(sample(options, "unrelated", null, null, null)).isEqualTo(0.2);
        assertThat(sample(options, "unrelated", null, null, true)).isEqualTo(1.0);
        assertThat(sample(options, "unrelated", null, null, false)).isZero();
        assertThat(sample(options, "request", "HEAD", "/api/courses", true)).isZero();
        assertThat(sample(options, "request", "GET", "/api/public/time", true)).isZero();
        assertThat(sample(options, "request", "GET", "/api/iris/status", true)).isEqualTo(0.001);
        assertThat(sample(options, "request", "GET", "/management/prometheus", true)).isEqualTo(0.001);
        assertThat(sample(options, "request", "GET", "/management/info", true)).isEqualTo(0.001);
        assertThat(sample(options, "request", "GET", "/api/courses", true)).isEqualTo(1.0);
        assertThat(sample(options, "request", "GET", "/api/courses", false)).isZero();
        assertThat(sample(options, "request", "GET", "/api/courses", null)).isEqualTo(0.2);
    }

    @Test
    void errorCallbackScrubsEveryPersonalDataFieldAndKeepsUsefulHeaders() {
        var options = options(configuration(Optional.of("https://key@example.invalid/1"), Optional.empty(), Optional.empty(), Optional.empty()));
        SentryEvent event = new SentryEvent();
        Message message = new Message();
        message.setMessage("failure user=student mail@example.org User{login=student}");
        message.setFormatted("mail@example.org failed");
        message.setParams(List.of("user=student", "mail@example.org", "useful"));
        event.setMessage(message);
        event.setTransaction("/git/COURSE/exercise-student.git");
        SentryException exception = new SentryException();
        exception.setValue("failure user=student");
        SentryException noValue = new SentryException();
        event.setExceptions(List.of(exception, noValue));
        Request request = new Request();
        request.setUrl("https://host/git/COURSE/exercise-student.git");
        request.setCookies("private cookie");
        request.setHeaders(Map.of("X-Artemis-Client-Id", "student", "x-artemis-client-version", "secret", "Accept", "application/json"));
        event.setRequest(request);
        event.setUser(new User());

        assertThat(options.getBeforeSend().execute(event, new Hint())).isSameAs(event);
        assertThat(message.getMessage()).isEqualTo("failure   ");
        assertThat(message.getFormatted()).isEqualTo(" failed");
        assertThat(message.getParams()).containsExactly("", "", "useful");
        assertThat(event.getTransaction()).isEqualTo("/git/COURSE/exercise.git");
        assertThat(exception.getValue()).isEqualTo("failure ");
        assertThat(noValue.getValue()).isNull();
        assertThat(request.getCookies()).isEmpty();
        assertThat(request.getUrl()).isEqualTo("https://host/git/COURSE/exercise.git");
        assertThat(request.getHeaders()).containsExactlyEntriesOf(Map.of("Accept", "application/json"));
        assertThat(event.getUser()).isNull();
    }

    @Test
    void callbacksAcceptAbsentOptionalDataAndPreserveExerciseRepositories() {
        var options = options(configuration(Optional.of("https://key@example.invalid/1"), Optional.empty(), Optional.empty(), Optional.empty()));
        SentryEvent empty = new SentryEvent();
        assertThat(options.getBeforeSend().execute(empty, new Hint())).isSameAs(empty);
        empty.setMessage(new Message());
        Request request = new Request();
        empty.setRequest(request);
        options.getBeforeSend().execute(empty, new Hint());
        assertThat(request.getCookies()).isEmpty();
        for (String suffix : List.of("tests", "exercise", "solution")) {
            String url = "https://host/git/COURSE/exercise-" + suffix + ".git";
            request.setUrl(url);
            options.getBeforeSend().execute(empty, new Hint());
            assertThat(request.getUrl()).isEqualTo(url);
        }
        SentryTransaction transaction = new SentryTransaction("request", 0.0, 1.0, List.of(), Map.of(), new io.sentry.protocol.TransactionInfo("url"));
        transaction.setRequest(request);
        transaction.setUser(new User());
        assertThat(options.getBeforeSendTransaction().execute(transaction, new Hint())).isSameAs(transaction);
        assertThat(transaction.getUser()).isNull();
        Breadcrumb breadcrumb = new Breadcrumb();
        assertThat(options.getBeforeBreadcrumb().execute(breadcrumb, new Hint())).isSameAs(breadcrumb);
        breadcrumb.setMessage("failure user=student");
        options.getBeforeBreadcrumb().execute(breadcrumb, new Hint());
        assertThat(breadcrumb.getMessage()).isEqualTo("failure ");
    }

    private double sample(SentryOptions options, String requestType, String method, String uri, Boolean parentSampled) {
        TransactionContext transaction = mock(TransactionContext.class);
        when(transaction.getParentSampled()).thenReturn(parentSampled);
        CustomSamplingContext context = null;
        if (requestType != null) {
            context = new CustomSamplingContext();
            Object request = new Object();
            if (requestType.equals("request")) {
                HttpServletRequest servletRequest = mock(HttpServletRequest.class);
                when(servletRequest.getMethod()).thenReturn(method);
                when(servletRequest.getRequestURI()).thenReturn(uri);
                request = servletRequest;
            }
            context.set("request", request);
        }
        SamplingContext samplingContext = mock(SamplingContext.class);
        when(samplingContext.getTransactionContext()).thenReturn(transaction);
        when(samplingContext.getCustomSamplingContext()).thenReturn(context);
        return options.getTracesSampler().sample(samplingContext);
    }

    @SuppressWarnings("unchecked")
    private SentryOptions options(SentryConfiguration configuration) {
        SentryOptions options = new SentryOptions();
        try (var sentry = mockStatic(Sentry.class)) {
            sentry.when(() -> Sentry.init(any(Sentry.OptionsConfiguration.class))).thenAnswer(call -> {
                ((Sentry.OptionsConfiguration<SentryOptions>) call.getArgument(0)).configure(options);
                return null;
            });
            configuration.init();
            sentry.verify(() -> Sentry.init(any(Sentry.OptionsConfiguration.class)));
        }
        return options;
    }

    private SentryConfiguration configuration(Optional<String> dsn, Optional<String> environment, Optional<Boolean> testServer, Optional<Double> rate) {
        SentryConfiguration configuration = new SentryConfiguration();
        ReflectionTestUtils.setField(configuration, "sentryDsn", dsn);
        ReflectionTestUtils.setField(configuration, "environment", environment);
        ReflectionTestUtils.setField(configuration, "isTestServer", testServer);
        ReflectionTestUtils.setField(configuration, "configuredTracesSampleRate", rate);
        ReflectionTestUtils.setField(configuration, "artemisVersion", "test-release");
        return configuration;
    }
}
