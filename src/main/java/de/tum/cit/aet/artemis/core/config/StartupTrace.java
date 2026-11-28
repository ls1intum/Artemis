package de.tum.cit.aet.artemis.core.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.boot.context.metrics.buffering.StartupTimeline;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.core.metrics.StartupStep;

import de.tum.cit.aet.artemis.core.DeferredEagerBeanInitializationCompletedEvent;

/**
 * Opt-in trace that names the slowest steps of the server startup.
 * <p>
 * The log of a normal startup only shows a few milestones, so a gap of several seconds between two lines cannot be
 * attributed to a bean. With {@code artemis.startup-trace.enabled=true} (environment variable
 * {@code ARTEMIS_STARTUP_TRACE_ENABLED}) Spring Boot records every startup step in a {@link BufferingApplicationStartup}
 * and this class logs two reports, every line prefixed with {@code Startup trace} so that they can be grepped:
 * <ul>
 * <li><b>until ready</b>: everything up to {@code ApplicationReadyEvent}, which is the time to "Started ArtemisApp".</li>
 * <li><b>deferred warm-up</b>: the beans that {@link DeferredEagerBeanInitializer} creates after that.</li>
 * </ul>
 * Each report lists the steps with the largest <i>own</i> time, which is the duration of a step without the steps nested in
 * it, and the own time summed per kind of step. The default is off because recording every step costs memory and a
 * little time.
 * <p>
 * Steps are nested by the thread-agnostic "latest active step", so the own time of steps that overlap on several threads is
 * approximate. It is clamped at zero and never negative.
 */
public final class StartupTrace {

    /**
     * Switches the trace on.
     */
    public static final String ENABLED_PROPERTY = "artemis.startup-trace.enabled";

    /**
     * Number of steps listed per report.
     */
    public static final String TOP_STEPS_PROPERTY = "artemis.startup-trace.top-steps";

    /**
     * Capacity of the step buffer. Steps beyond it are dropped, which the report points out.
     */
    public static final String BUFFER_SIZE_PROPERTY = "artemis.startup-trace.buffer-size";

    private static final int DEFAULT_TOP_STEPS = 30;

    private static final int DEFAULT_BUFFER_SIZE = 100_000;

    private static final int MAX_LABEL_LENGTH = 110;

    private static final Logger log = LoggerFactory.getLogger(StartupTrace.class);

    private StartupTrace() {
    }

    /**
     * One recorded startup step, reduced to what the report needs.
     *
     * @param id       the id of the step, unique within a recording
     * @param parentId the id of the enclosing step, or a negative value for a top-level step
     * @param name     the kind of step, for example {@code spring.beans.instantiate}
     * @param label    what the step worked on, usually the bean name, or an empty string
     * @param duration the total duration of the step, including nested steps
     */
    record Step(long id, long parentId, String name, String label, Duration duration) {
    }

    /**
     * A step together with its own time.
     *
     * @param step    the step
     * @param ownTime the duration of the step without the durations of its direct children
     */
    record StepTiming(Step step, Duration ownTime) {
    }

    /**
     * The own time of all steps of one kind.
     *
     * @param name    the kind of step
     * @param count   the number of steps of this kind
     * @param ownTime the sum of the own times
     */
    record KindTotal(String name, int count, Duration ownTime) {
    }

    /**
     * Registers the trace on the application. It does nothing at runtime unless the property switches it on.
     *
     * @param application the application that is about to run
     */
    public static void register(SpringApplication application) {
        application.addListeners(new StartupTraceListener());
    }

    /**
     * Computes the own time of every step, largest first.
     *
     * @param steps the recorded steps
     * @return the steps with their own times, ordered by own time descending
     */
    static List<StepTiming> ownTimes(List<Step> steps) {
        Map<Long, Duration> childTimeByParent = new HashMap<>();
        for (Step step : steps) {
            if (step.parentId() >= 0) {
                childTimeByParent.merge(step.parentId(), step.duration(), Duration::plus);
            }
        }
        List<StepTiming> timings = new ArrayList<>(steps.size());
        for (Step step : steps) {
            Duration own = step.duration().minus(childTimeByParent.getOrDefault(step.id(), Duration.ZERO));
            timings.add(new StepTiming(step, own.isNegative() ? Duration.ZERO : own));
        }
        timings.sort(Comparator.comparing(StepTiming::ownTime).reversed().thenComparingLong(timing -> timing.step().id()));
        return timings;
    }

    /**
     * Sums the own times per kind of step, largest first.
     *
     * @param timings the steps with their own times
     * @return one total per kind of step, ordered by own time descending
     */
    static List<KindTotal> totalsPerKind(List<StepTiming> timings) {
        Map<String, Duration> ownTimeByName = new HashMap<>();
        Map<String, Integer> countByName = new HashMap<>();
        for (StepTiming timing : timings) {
            ownTimeByName.merge(timing.step().name(), timing.ownTime(), Duration::plus);
            countByName.merge(timing.step().name(), 1, Integer::sum);
        }
        return ownTimeByName.entrySet().stream().map(entry -> new KindTotal(entry.getKey(), countByName.get(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparing(KindTotal::ownTime).reversed().thenComparing(KindTotal::name)).toList();
    }

    /**
     * Formats one report.
     *
     * @param phase      the name of the phase the steps belong to
     * @param steps      the recorded steps of the phase
     * @param topSteps   how many steps to list
     * @param bufferSize the capacity of the step buffer, to point out when it may have overflowed
     * @return the report as multi-line text, whose first line starts with {@code Startup trace}
     */
    static String formatReport(String phase, List<Step> steps, int topSteps, int bufferSize) {
        List<StepTiming> timings = ownTimes(steps);
        StringBuilder report = new StringBuilder();
        report.append("Startup trace, ").append(phase).append(": ").append(steps.size()).append(" steps recorded");
        if (steps.size() >= bufferSize) {
            report.append(" (the buffer is full, steps were dropped; raise ").append(BUFFER_SIZE_PROPERTY).append(')');
        }
        report.append("\nOwn time per kind of step:");
        for (KindTotal total : totalsPerKind(timings)) {
            report.append(String.format(Locale.ROOT, "%n  %7d ms  %6d x  %s", total.ownTime().toMillis(), total.count(), total.name()));
        }
        report.append("\nSlowest steps by own time (own / total):");
        timings.stream().limit(topSteps).forEach(timing -> {
            Step step = timing.step();
            report.append(String.format(Locale.ROOT, "%n  %7d ms / %7d ms  %s %s", timing.ownTime().toMillis(), step.duration().toMillis(), step.name(), step.label()));
        });
        return report.toString();
    }

    static List<Step> toSteps(StartupTimeline timeline) {
        return timeline.getEvents().stream().map(event -> {
            StartupStep step = event.getStartupStep();
            return new Step(step.getId(), step.getParentId() == null ? -1 : step.getParentId(), step.getName(), labelOf(step), event.getDuration());
        }).toList();
    }

    private static String labelOf(StartupStep step) {
        String label = tagValue(step, "beanName");
        if (label == null) {
            label = tagValue(step, "postProcessor");
        }
        if (label == null) {
            label = tagValue(step, "className");
        }
        if (label == null) {
            label = step.getTags().iterator().hasNext() ? step.getTags().iterator().next().getValue() : "";
        }
        return label.length() > MAX_LABEL_LENGTH ? label.substring(0, MAX_LABEL_LENGTH) + "..." : label;
    }

    private static String tagValue(StartupStep step, String key) {
        for (StartupStep.Tag tag : step.getTags()) {
            if (key.equals(tag.getKey())) {
                return tag.getValue();
            }
        }
        return null;
    }

    /**
     * Switches the recording on while the environment is prepared and logs the reports once the phases are over.
     * <p>
     * The application startup can still be replaced when the environment is prepared, because the context is created
     * after that. Only the few steps before it are missing from the recording.
     */
    private static final class StartupTraceListener implements ApplicationListener<ApplicationEvent> {

        private BufferingApplicationStartup startup;

        private int topSteps = DEFAULT_TOP_STEPS;

        private int bufferSize = DEFAULT_BUFFER_SIZE;

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            if (event instanceof ApplicationEnvironmentPreparedEvent prepared) {
                enableIfRequested(prepared);
            }
            else if (startup != null && event instanceof ApplicationReadyEvent) {
                report("until ready (time to the startup banner)");
            }
            else if (startup != null && event instanceof PayloadApplicationEvent<?> payloadEvent
                    && payloadEvent.getPayload() instanceof DeferredEagerBeanInitializationCompletedEvent) {
                report("deferred warm-up (after the startup banner)");
            }
        }

        private void enableIfRequested(ApplicationEnvironmentPreparedEvent event) {
            if (!event.getEnvironment().getProperty(ENABLED_PROPERTY, Boolean.class, false)) {
                return;
            }
            topSteps = event.getEnvironment().getProperty(TOP_STEPS_PROPERTY, Integer.class, DEFAULT_TOP_STEPS);
            bufferSize = event.getEnvironment().getProperty(BUFFER_SIZE_PROPERTY, Integer.class, DEFAULT_BUFFER_SIZE);
            startup = new BufferingApplicationStartup(bufferSize);
            event.getSpringApplication().setApplicationStartup(startup);
        }

        private void report(String phase) {
            // Draining empties the buffer, so the next report only covers the steps after this one.
            String report = formatReport(phase, toSteps(startup.drainBufferedTimeline()), topSteps, bufferSize);
            // One log entry per line, because the log converter escapes line breaks and a multi-line entry would be unreadable.
            report.lines().forEach(line -> log.info("{}", line.startsWith("Startup trace") ? line : "Startup trace |" + line));
        }
    }
}
