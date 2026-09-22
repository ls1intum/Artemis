package de.tum.cit.aet.artemis.core.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

/**
 * Keeps the most recent log events in memory so an administrator can read them in the admin UI.
 * <p>
 * This exists because Artemis logs only to the console: the FILE appender in {@code logback-spring.xml} is
 * commented out, so there is no log file for Actuator's {@code logfile} endpoint to serve, and the logs are
 * only readable wherever the container's stdout is collected. When that collector is unavailable there is no
 * way to see a stack trace at all without shell access to the server, which is the gap this closes.
 * <p>
 * Deliberately bounded and in-memory. It is a debugging aid, not a log store: it holds the last
 * {@link #capacity} events, it is lost on restart, and it never touches disk. Anything that must survive
 * either of those belongs in a real log pipeline.
 * <p>
 * The buffer is static because Logback instantiates appenders itself rather than through Spring, so the
 * reading side cannot be injected with this instance and reads the shared buffer instead.
 */
public class RecentLogsAppender extends AppenderBase<ILoggingEvent> {

    /**
     * How many events are kept when the configuration names no capacity. Roughly a megabyte of typical
     * events — enough to cover the window around a failure without being worth tuning.
     */
    private static final int DEFAULT_CAPACITY = 2000;

    /**
     * Longest stack trace kept per event. A truncated trace still names the failure and its first frames,
     * which is what a reader needs; keeping entire traces lets a single crash loop evict the whole buffer.
     */
    private static final int MAX_STACK_TRACE_LENGTH = 8000;

    /**
     * Guards {@link #buffer}. Appending happens on every logging thread, reading on request threads.
     */
    private static final Object LOCK = new Object();

    private static final Deque<RecentLogEvent> buffer = new ArrayDeque<>();

    private static volatile int capacity = DEFAULT_CAPACITY;

    /**
     * One captured event, already rendered to strings so nothing holds a reference to the logging context.
     *
     * @param timestamp  when it was logged, in epoch milliseconds
     * @param level      the level name, e.g. {@code ERROR}
     * @param loggerName the logger that emitted it
     * @param threadName the thread it was emitted on
     * @param message    the formatted message
     * @param stackTrace the rendered throwable, or null when the event carried none
     */
    public record RecentLogEvent(long timestamp, String level, String loggerName, String threadName, String message, String stackTrace) {
    }

    /**
     * Sets how many events are retained. Called by Logback from the {@code <capacity>} element.
     *
     * @param newCapacity the number of events to retain; values below one are ignored
     */
    public void setCapacity(int newCapacity) {
        if (newCapacity > 0) {
            capacity = newCapacity;
            synchronized (LOCK) {
                trimToCapacity();
            }
        }
    }

    public int getCapacity() {
        return capacity;
    }

    @Override
    protected void append(ILoggingEvent event) {
        // Render eagerly: ILoggingEvent can defer formatting, and the event must not outlive its context.
        var captured = new RecentLogEvent(event.getTimeStamp(), event.getLevel().toString(), event.getLoggerName(), event.getThreadName(), event.getFormattedMessage(),
                renderThrowable(event.getThrowableProxy()));
        synchronized (LOCK) {
            buffer.addLast(captured);
            trimToCapacity();
        }
    }

    /**
     * Returns the retained events, newest first.
     *
     * @return a snapshot of the buffer; never the live deque, so a caller can iterate it safely
     */
    public static List<RecentLogEvent> snapshot() {
        synchronized (LOCK) {
            var snapshot = new ArrayList<>(buffer);
            snapshot.sort((first, second) -> Long.compare(second.timestamp(), first.timestamp()));
            return snapshot;
        }
    }

    /**
     * Drops every retained event. Useful before reproducing a problem, so the buffer holds only that attempt.
     */
    public static void clear() {
        synchronized (LOCK) {
            buffer.clear();
        }
    }

    private static void trimToCapacity() {
        while (buffer.size() > capacity) {
            buffer.removeFirst();
        }
    }

    private static String renderThrowable(IThrowableProxy throwableProxy) {
        if (throwableProxy == null) {
            return null;
        }
        String rendered = ThrowableProxyUtil.asString(throwableProxy);
        return rendered.length() <= MAX_STACK_TRACE_LENGTH ? rendered : rendered.substring(0, MAX_STACK_TRACE_LENGTH) + "\n... (truncated)";
    }
}
