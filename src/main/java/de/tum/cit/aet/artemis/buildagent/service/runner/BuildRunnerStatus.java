package de.tum.cit.aet.artemis.buildagent.service.runner;

import org.jspecify.annotations.Nullable;

/**
 * Current reachability and version information for a LocalCI build runner.
 *
 * @param available whether new build jobs can be started
 * @param version   the execution system version, if it could be determined
 * @param message   a diagnostic message when the runner is unavailable
 */
public record BuildRunnerStatus(boolean available, @Nullable String version, @Nullable String message) {

    public static BuildRunnerStatus available(@Nullable String version) {
        return new BuildRunnerStatus(true, version, null);
    }

    public static BuildRunnerStatus unavailable(@Nullable String message) {
        return new BuildRunnerStatus(false, null, message);
    }
}
