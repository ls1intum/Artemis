package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.context.ApplicationListener;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.mock.env.MockEnvironment;

import de.tum.cit.aet.artemis.core.config.StartupTrace.KindTotal;
import de.tum.cit.aet.artemis.core.config.StartupTrace.Step;
import de.tum.cit.aet.artemis.core.config.StartupTrace.StepTiming;

/**
 * Covers the own-time arithmetic of the opt-in startup trace, since a wrong subtraction would point at the wrong bean, and the
 * switch that keeps the recording off by default.
 */
class StartupTraceTest {

    private static Step step(long id, long parentId, String name, String label, long millis) {
        return new Step(id, parentId, name, label, Duration.ofMillis(millis));
    }

    @Test
    void testOwnTimeIsTheDurationWithoutTheDirectChildren() {
        List<StepTiming> timings = StartupTrace.ownTimes(List.of(step(1, -1, "outer", "a", 100), step(2, 1, "inner", "b", 30), step(3, 1, "inner", "c", 20)));

        assertThat(timings).extracting(timing -> timing.step().label()).containsExactly("a", "b", "c");
        assertThat(timings).extracting(StepTiming::ownTime).containsExactly(Duration.ofMillis(50), Duration.ofMillis(30), Duration.ofMillis(20));
    }

    /**
     * A step that runs on another thread can outlast the step it is nested in, which would make the own time of the
     * parent negative.
     */
    @Test
    void testOwnTimeIsNeverNegative() {
        List<StepTiming> timings = StartupTrace.ownTimes(List.of(step(1, -1, "outer", "a", 10), step(2, 1, "inner", "b", 30)));

        assertThat(timings).filteredOn(timing -> timing.step().id() == 1).singleElement().extracting(StepTiming::ownTime).isEqualTo(Duration.ZERO);
    }

    @Test
    void testOnlyDirectChildrenAreSubtracted() {
        // The grandchild is already part of the child's duration, subtracting it from the grandparent as well would count it twice.
        List<StepTiming> timings = StartupTrace.ownTimes(List.of(step(1, -1, "outer", "a", 100), step(2, 1, "inner", "b", 60), step(3, 2, "innermost", "c", 40)));

        assertThat(timings).extracting(timing -> timing.step().label() + "=" + timing.ownTime().toMillis()).containsExactlyInAnyOrder("a=40", "b=20", "c=40");
    }

    @Test
    void testTotalsPerKindSumOwnTimes() {
        List<StepTiming> timings = StartupTrace.ownTimes(
                List.of(step(1, -1, "spring.beans.instantiate", "a", 40), step(2, -1, "spring.beans.instantiate", "b", 25), step(3, -1, "spring.context.refresh", "c", 100)));

        List<KindTotal> totals = StartupTrace.totalsPerKind(timings);

        assertThat(totals).extracting(KindTotal::name).containsExactly("spring.context.refresh", "spring.beans.instantiate");
        assertThat(totals.get(1).count()).isEqualTo(2);
        assertThat(totals.get(1).ownTime()).isEqualTo(Duration.ofMillis(65));
    }

    @Test
    void testReportListsOnlyTheRequestedNumberOfSteps() {
        List<Step> steps = List.of(step(1, -1, "kind", "slowest", 300), step(2, -1, "kind", "middle", 200), step(3, -1, "kind", "fastest", 100));

        String report = StartupTrace.formatReport("until ready", steps, 2, 1000);

        assertThat(report).startsWith("Startup trace, until ready: 3 steps recorded").contains("slowest").contains("middle").doesNotContain("fastest")
                .doesNotContain("buffer is full");
    }

    @Test
    void testReportWarnsWhenTheBufferMayHaveOverflowed() {
        String report = StartupTrace.formatReport("until ready", List.of(step(1, -1, "kind", "a", 1), step(2, -1, "kind", "b", 1)), 5, 2);

        assertThat(report).contains("the buffer is full").contains(StartupTrace.BUFFER_SIZE_PROPERTY);
    }

    /**
     * The recording of Spring Boot is the real input of the trace, so this checks that the bean name becomes the label and
     * that nesting survives the conversion, without relying on any timing.
     */
    @Test
    void testRealTimelineKeepsBeanNamesAndNesting() {
        BufferingApplicationStartup startup = new BufferingApplicationStartup(10);
        StartupStep outer = startup.start("spring.beans.instantiate").tag("beanName", "outerBean");
        StartupStep inner = startup.start("spring.beans.instantiate").tag("beanName", "innerBean");
        inner.end();
        outer.end();

        List<Step> steps = StartupTrace.toSteps(startup.drainBufferedTimeline());

        assertThat(steps).extracting(Step::label).containsExactlyInAnyOrder("outerBean", "innerBean");
        Step recordedOuter = steps.stream().filter(recorded -> recorded.label().equals("outerBean")).findFirst().orElseThrow();
        Step recordedInner = steps.stream().filter(recorded -> recorded.label().equals("innerBean")).findFirst().orElseThrow();
        assertThat(recordedInner.parentId()).isEqualTo(recordedOuter.id());
        assertThat(recordedOuter.parentId()).isNegative();
    }

    @Test
    void testRecordingStaysOffUnlessTheEnvironmentSwitchesItOn() {
        SpringApplication application = new SpringApplication();
        StartupTrace.register(application);

        fireEnvironmentPrepared(application, new MockEnvironment());

        assertThat(application.getApplicationStartup()).isSameAs(ApplicationStartup.DEFAULT);
    }

    @Test
    void testRecordingStartsWhenTheEnvironmentSwitchesItOn() {
        SpringApplication application = new SpringApplication();
        StartupTrace.register(application);

        fireEnvironmentPrepared(application, new MockEnvironment().withProperty(StartupTrace.ENABLED_PROPERTY, "true"));

        assertThat(application.getApplicationStartup()).isInstanceOf(BufferingApplicationStartup.class);
    }

    @SuppressWarnings("unchecked")
    private static void fireEnvironmentPrepared(SpringApplication application, MockEnvironment environment) {
        ApplicationEnvironmentPreparedEvent event = new ApplicationEnvironmentPreparedEvent(new DefaultBootstrapContext(), application, new String[0], environment);
        // The application also holds the default listeners of Spring Boot, which expect other events.
        application.getListeners().stream().filter(listener -> listener.getClass().getEnclosingClass() == StartupTrace.class)
                .forEach(listener -> ((ApplicationListener<ApplicationEnvironmentPreparedEvent>) listener).onApplicationEvent(event));
    }
}
