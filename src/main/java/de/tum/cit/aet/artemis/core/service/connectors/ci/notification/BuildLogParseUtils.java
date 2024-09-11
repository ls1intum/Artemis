package de.tum.cit.aet.artemis.core.service.connectors.ci.notification;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import de.tum.cit.aet.artemis.programming.domain.BuildLogEntry;

public class BuildLogParseUtils {

    /**
     * Parses build logs from Jenkins or GitLab CI into BuildLogEntry objects. The function reads the list
     * of log strings and tries to parse lines of the following format:
     * <p>
     * [2021-05-10T15:19:49.741Z] [INFO] BUILD FAILURE
     * <p>
     * and extract the timestamp and message.
     * <p>
     * A small snippet of the log format is:
     * [Pipeline] {
     * [Pipeline] sh
     * [2021-05-10T15:19:37.112Z] + mvn clean test -B
     * [2021-05-10T15:19:49.741Z] [INFO] BUILD FAILURE
     * ...
     * [2021-05-10T15:19:49.741Z] [ERROR] BubbleSort.java:[15,9] not a statement
     * [2021-05-10T15:19:49.741Z] [ERROR] BubbleSort.java:[15,10] ';' expected
     * [Pipeline] }
     *
     * @param logLines The lines of the Jenkins log
     * @return a list of BuildLogEntries
     */
    public static List<BuildLogEntry> parseBuildLogsFromLogs(List<String> logLines) {
        final List<BuildLogEntry> buildLogs = new ArrayList<>();
        for (final var logLine : logLines) {
            // The build logs that we are interested in are the ones that start with a timestamp
            // of format [timestamp] ...
            final String possibleTimestamp = StringUtils.substringBetween(logLine, "[", "]");
            if (possibleTimestamp == null) {
                continue;
            }

            try {
                final ZonedDateTime timestamp = ZonedDateTime.parse(possibleTimestamp);
                // The 2 is used because the timestamp is surrounded with '[' ']'
                final String log = logLine.substring(possibleTimestamp.length() + 2);

                BuildLogEntry buildLogEntry = new BuildLogEntry(timestamp, stripLogEndOfLine(log).trim());
                buildLogs.add(buildLogEntry);
            }
            catch (DateTimeParseException e) {
                // The log line doesn't contain the timestamp so we ignore it
            }
        }
        return buildLogs;
    }

    private static String stripLogEndOfLine(String log) {
        return log.replaceAll("[\\r\\n]", "");
    }
}
