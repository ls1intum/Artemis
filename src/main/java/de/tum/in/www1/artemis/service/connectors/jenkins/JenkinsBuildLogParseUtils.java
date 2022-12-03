package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import de.tum.in.www1.artemis.domain.BuildLogEntry;

public class JenkinsBuildLogParseUtils {

    // Pattern of the DateTime that is included in the logs received from Jenkins
    private static final DateTimeFormatter LOG_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");

    /**
     * Parses build logs from Jenkins into BuildLogEntry objects. The function reads the list
     * of log strings and tries to parse lines of the following format:
     *
     * [2021-05-10T15:19:49.741Z] [INFO] BUILD FAILURE
     *
     * and extract the timestamp and message.
     *
     * A small snippet of the log format is:
     * [Pipeline] {
     * [Pipeline] sh
     * [2021-05-10T15:19:37.112Z] + mvn clean test -B
     * [2021-05-10T15:19:49.741Z] [INFO] BUILD FAILURE
     * ...
     * [2021-05-10T15:19:49.741Z] [ERROR] BubbleSort.java:[15,9] not a statement
     * [2021-05-10T15:19:49.741Z] [ERROR] BubbleSort.java:[15,10] ';' expected
     * [Pipeline] }
     * @param logLines The lines of the Jenkins log
     * @return a list of BuildLogEntries
     */
    public static List<BuildLogEntry> parseBuildLogsFromJenkinsLogs(List<String> logLines) {
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

    /**
     * Parses build logs from the legacy version of Jenkins. An example
     * snippet of the file is located at: src/test-data/jenkins-response/legacy-failed-build-log.html
     *
     * @param logHtml Build logs in the HTML format
     * @return a list of BuildLogEntries
     */
    public static List<BuildLogEntry> parseLogsLegacy(Element logHtml) {
        final var buildLog = new LinkedList<BuildLogEntry>();
        final var iterator = logHtml.childNodes().iterator();
        while (iterator.hasNext()) {
            final var node = iterator.next();
            final String log;
            // For timestamps, parse the <b> tag containing the time as hh:mm:ss
            if (node.attributes().get("class").contains("timestamp")) {
                final var timeAsString = ((TextNode) node.childNode(0).childNode(0)).getWholeText();
                final var time = ZonedDateTime.parse(timeAsString, LOG_DATE_TIME_FORMATTER);
                log = reduceToText(iterator.next());
                buildLog.add(new BuildLogEntry(time, stripLogEndOfLine(log)));
            }
            else {
                // Log is from the same line as the last
                // Look for next text node in children
                log = reduceToText(node);
                final var lastLog = buildLog.getLast();
                lastLog.setLog(lastLog.getLog() + stripLogEndOfLine(log));
            }
        }
        return buildLog;
    }

    private static String stripLogEndOfLine(String log) {
        return log.replaceAll("[\\r\\n]", "");
    }

    private static String reduceToText(Node node) {
        if (node instanceof TextNode) {
            return ((TextNode) node).getWholeText();
        }

        return reduceToText(node.childNode(node.childNodeSize() - 1));
    }
}
