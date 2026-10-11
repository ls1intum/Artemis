package de.tum.cit.aet.artemis.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.notification.config.MailDeliveryProperties.Retry;

/**
 * A retry setting that is not positive would not fail loudly but break the delivery: a queue of size 0 drops every mail, a batch of
 * size 0 never works the queue off, and a delay of 0 makes a failed mail due again at once. The binding is validated, so such a
 * value stops the startup.
 */
class MailDeliveryPropertiesTest {

    private static ValidatorFactory validatorFactory;

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    private static Set<ConstraintViolation<MailDeliveryProperties>> validate(MailDeliveryProperties properties) {
        return validator.validate(properties);
    }

    @Test
    void shouldAcceptTheDefaults() {
        assertThat(validate(new MailDeliveryProperties())).isEmpty();
    }

    private static Stream<Arguments> invalidSettings() {
        BiConsumer<Retry, Integer> maxQueueSize = Retry::setMaxQueueSize;
        BiConsumer<Retry, Integer> drainBatchSize = Retry::setDrainBatchSize;
        BiConsumer<Retry, Duration> initialDelay = Retry::setInitialDelay;
        BiConsumer<Retry, Duration> maxDelay = Retry::setMaxDelay;
        BiConsumer<Retry, Duration> quotaPause = Retry::setQuotaPause;
        BiConsumer<Retry, Duration> unavailablePause = Retry::setUnavailablePause;
        BiConsumer<Retry, Duration> transactionalMaxAge = Retry::setTransactionalMaxAge;
        BiConsumer<Retry, Duration> bulkMaxAge = Retry::setBulkMaxAge;
        BiConsumer<Retry, Duration> drainInterval = Retry::setDrainInterval;
        return Stream.of(Arguments.of("maxQueueSize", maxQueueSize, 0), Arguments.of("maxQueueSize", maxQueueSize, -5), Arguments.of("drainBatchSize", drainBatchSize, 0),
                Arguments.of("drainBatchSize", drainBatchSize, -1), Arguments.of("initialDelay", initialDelay, Duration.ZERO),
                Arguments.of("initialDelay", initialDelay, Duration.ofSeconds(-1)), Arguments.of("maxDelay", maxDelay, Duration.ZERO),
                Arguments.of("quotaPause", quotaPause, Duration.ZERO), Arguments.of("unavailablePause", unavailablePause, Duration.ofMinutes(-2)),
                Arguments.of("transactionalMaxAge", transactionalMaxAge, Duration.ZERO), Arguments.of("bulkMaxAge", bulkMaxAge, Duration.ofHours(-1)),
                Arguments.of("drainInterval", drainInterval, Duration.ZERO));
    }

    @ParameterizedTest(name = "{0} = {2}")
    @MethodSource("invalidSettings")
    <T> void shouldRejectASettingThatIsNotPositive(String property, BiConsumer<Retry, T> setter, T value) {
        var properties = new MailDeliveryProperties();
        setter.accept(properties.getRetry(), value);

        var violations = validate(properties);

        // The nested retry settings are validated through the cascade from the properties.
        assertThat(violations).singleElement().satisfies(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("retry." + property));
    }

    @Test
    void shouldAcceptTheSmallestPositiveValues() {
        var properties = new MailDeliveryProperties();
        properties.getRetry().setMaxQueueSize(1);
        properties.getRetry().setDrainBatchSize(1);
        properties.getRetry().setInitialDelay(Duration.ofNanos(1));
        properties.getRetry().setDrainInterval(Duration.ofMillis(1));

        assertThat(validate(properties)).isEmpty();
    }
}
