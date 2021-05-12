package de.tum.in.www1.artemis.service.connectors.jenkins;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import de.tum.in.www1.artemis.domain.BuildLogEntry;

public class JenkinsBuildLogUtils {

    // Pattern of the DateTime that is included in the logs received from Jenkins
    private static final DateTimeFormatter LOG_DATA_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");

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

    public static List<BuildLogEntry> parsePipelineLogs(Element logHtml) throws IllegalArgumentException {
        final var buildLog = new LinkedList<BuildLogEntry>();
        if (logHtml.childNodes().stream().noneMatch(child -> child.attr("class").contains("pipeline"))) {
            throw new IllegalArgumentException("Log is not pipeline log");
        }
        for (Element elem : logHtml.children()) {
            // Only pipeline-node-ID elements contain actual log entries
            if (elem.attributes().get("class").contains("pipeline-node")) {
                // At least one child must have a timestamp class
                if (elem.childNodes().stream().anyMatch(child -> child.attr("class").contains("timestamp"))) {
                    Iterator<Node> nodeIterator = elem.childNodes().iterator();

                    while (nodeIterator.hasNext()) {
                        Node node = nodeIterator.next();
                        String log;
                        if (node.attributes().get("class").contains("timestamp")) {
                            final var timeAsString = ((TextNode) node.childNode(0).childNode(0)).getWholeText();
                            final var time = ZonedDateTime.parse(timeAsString, LOG_DATA_TIME_FORMATTER);
                            var contentCandidate = nodeIterator.next();

                            // Skip invisible entries (they contain only the timestamp, but we already got that above)
                            if (contentCandidate.attr("style").contains("display: none")) {
                                contentCandidate = nodeIterator.next();
                            }
                            log = reduceToText(contentCandidate);

                            // There are color codes in the logs that need to be filtered out.
                            // This is needed for old programming exercises
                            // For example:[[1;34mINFO[m] is changed to [INFO]
                            log = log.replace("\u001B[1;34m", "");
                            log = log.replace("\u001B[m", "");
                            log = log.replace("\u001B[1;31m", "");
                            buildLog.add(new BuildLogEntry(time, stripLogEndOfLine(log).trim()));
                        }
                        else {
                            // Log is from the same line as the last
                            // Look for next text node in children
                            log = reduceToText(node);
                            final var lastLog = buildLog.getLast();
                            lastLog.setLog(lastLog.getLog() + stripLogEndOfLine(log).trim());
                        }
                    }
                }
            }
        }
        return buildLog;
    }

    public static List<BuildLogEntry> parseLogsLegacy(Element logHtml) {
        final var buildLog = new LinkedList<BuildLogEntry>();
        final var iterator = logHtml.childNodes().iterator();
        while (iterator.hasNext()) {
            final var node = iterator.next();
            final String log;
            // For timestamps, parse the <b> tag containing the time as hh:mm:ss
            if (node.attributes().get("class").contains("timestamp")) {
                final var timeAsString = ((TextNode) node.childNode(0).childNode(0)).getWholeText();
                final var time = ZonedDateTime.parse(timeAsString, LOG_DATA_TIME_FORMATTER);
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
