package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.dto.ClientErrorReportDTO;
import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;

/**
 * Writes the errors that clients report to the log.
 * <p>
 * The entries are the input of the error analysis, and they come from clients that need not be authenticated. Every value is
 * therefore shortened, cleaned of control characters so that a client cannot forge a log line, and cleaned of personal data
 * before it is logged, whatever the client claims to have done already. Nothing is stored apart from the log.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ClientErrorReportService {

    private static final Logger log = LoggerFactory.getLogger(ClientErrorReportService.class);

    private final RateLimitService rateLimitService;

    private static final String ENTRY_FORMAT = "Client error report [version={}, url={}, authenticated={}, userAgent={}, tags={}, extra={}]: {}";

    private static final String REMOVED = "[removed]";

    private static final int MAX_USER_AGENT_LENGTH = 200;

    /** Characters that end a line, which a client could use to start a forged log entry. */
    private static final Pattern LINE_BREAKS_AND_CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}\\u0085\\u2028\\u2029]+");

    /** The same without the line feed, which separates the lines of a stack trace. */
    private static final Pattern CONTROL_CHARACTERS_EXCEPT_LINE_FEED = Pattern.compile("[[\\p{Cntrl}\\u0085\\u2028\\u2029]&&[^\\n]]+");

    /** Personal data in a text: {@code user=barney_young}, {@code User{...}} and email addresses. */
    private static final List<Pattern> PERSONAL_DATA_PATTERNS = List.of(Pattern.compile("user=\\S+"), Pattern.compile("User\\{[^}]*}"),
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"));

    /** A repository in a url, named {@code <project key>-<suffix>.git}. */
    private static final Pattern REPOSITORY_IN_URL = Pattern.compile("/git/([A-Z0-9]+)/([^/]+)\\.git");

    /** The repositories of an exercise that belong to nobody, as in {@code abc123-solution}. */
    private static final List<String> EXERCISE_REPOSITORY_SUFFIXES = List.of("exercise", "solution", "tests");

    public ClientErrorReportService(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    /**
     * Accepts an error that a client reported and writes it to the log.
     * <p>
     * A client that is not authenticated is bounded per address by {@link RateLimitType#CLIENT_ERROR_REPORT}, since every report
     * writes a log entry and the endpoint is open to everybody. A signed-in client is not limited here: the client drops
     * duplicate reports and caps the reports of a page, so a signed-in client is not expected to flood the log.
     *
     * @param report    the error that the client reported
     * @param userAgent the {@code User-Agent} header of the request, may be {@code null}
     * @throws RateLimitExceededException if the client is not authenticated and has used up its limit
     */
    public void report(ClientErrorReportDTO report, String userAgent) {
        boolean authenticated = SecurityUtils.isAuthenticated();
        if (!authenticated) {
            rateLimitService.enforcePerMinute(rateLimitService.resolveClientId(), RateLimitType.CLIENT_ERROR_REPORT);
        }
        logReport(report, userAgent, authenticated);
    }

    /**
     * Writes a reported error to the log, with the log level of its severity.
     *
     * @param report        the error that the client reported
     * @param userAgent     the {@code User-Agent} header of the request, may be {@code null}
     * @param authenticated whether the client that reported the error is signed in
     */
    public void logReport(ClientErrorReportDTO report, String userAgent, boolean authenticated) {
        Object[] entry = entryArguments(report, userAgent, authenticated);
        switch (report.severity()) {
            case INFO -> log.info(ENTRY_FORMAT, entry);
            case WARN -> log.warn(ENTRY_FORMAT, entry);
            case ERROR -> log.error(ENTRY_FORMAT, entry);
        }
    }

    /**
     * Formats the log entry of a report, which is the part of {@link #logReport} that cleans the values of the client.
     *
     * @param report        the error that the client reported
     * @param userAgent     the {@code User-Agent} header of the request, may be {@code null}
     * @param authenticated whether the client that reported the error is signed in
     * @return the text of the log entry
     */
    String formatEntry(ClientErrorReportDTO report, String userAgent, boolean authenticated) {
        return MessageFormatter.arrayFormat(ENTRY_FORMAT, entryArguments(report, userAgent, authenticated)).getMessage();
    }

    private static Object[] entryArguments(ClientErrorReportDTO report, String userAgent, boolean authenticated) {
        return new Object[] { singleLine(report.clientVersion(), ClientErrorReportDTO.MAX_VERSION_LENGTH),
                singleLine(withoutQueryAndFragment(report.url()), ClientErrorReportDTO.MAX_URL_LENGTH), authenticated, singleLine(userAgent, MAX_USER_AGENT_LENGTH),
                render(report.tags()), render(report.extra()), messageWithStack(report) };
    }

    private static String messageWithStack(ClientErrorReportDTO report) {
        String message = singleLine(report.message(), ClientErrorReportDTO.MAX_MESSAGE_LENGTH);
        if (report.stack() == null || report.stack().isBlank()) {
            return message;
        }
        // Every line of the stack starts with a tab, so that none of them can look like the start of a log entry
        String stack = removePersonalData(CONTROL_CHARACTERS_EXCEPT_LINE_FEED.matcher(shorten(report.stack(), ClientErrorReportDTO.MAX_STACK_LENGTH)).replaceAll(" "));
        return message + stack.lines().map(line -> "\n\t" + line).collect(Collectors.joining());
    }

    private static String render(Map<String, String> entries) {
        if (entries == null || entries.isEmpty()) {
            return "{}";
        }
        return new TreeMap<>(entries).entrySet().stream().limit(ClientErrorReportDTO.MAX_ENTRIES)
                .map(entry -> singleLine(entry.getKey(), ClientErrorReportDTO.MAX_KEY_LENGTH) + "=" + singleLine(entry.getValue(), ClientErrorReportDTO.MAX_VALUE_LENGTH))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private static String withoutQueryAndFragment(String url) {
        if (url == null) {
            return null;
        }
        int end = url.length();
        for (char separator : new char[] { '?', '#' }) {
            int index = url.indexOf(separator);
            if (index >= 0) {
                end = Math.min(end, index);
            }
        }
        return url.substring(0, end);
    }

    /**
     * Shortens a value and turns it into one line without personal data.
     *
     * @param value     the value that a client sent, may be {@code null}
     * @param maxLength the most characters to keep
     * @return the cleaned value, an empty string for {@code null}
     */
    private static String singleLine(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return removePersonalData(LINE_BREAKS_AND_CONTROL_CHARACTERS.matcher(shorten(value, maxLength)).replaceAll(" ")).strip();
    }

    private static String shorten(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String removePersonalData(String text) {
        String cleaned = text;
        for (Pattern pattern : PERSONAL_DATA_PATTERNS) {
            cleaned = pattern.matcher(cleaned).replaceAll(REMOVED);
        }
        return REPOSITORY_IN_URL.matcher(cleaned).replaceAll(ClientErrorReportService::withoutParticipant);
    }

    /**
     * Removes the participant from a repository in a url. The project key has no hyphen, so everything behind the first hyphen
     * identifies the participant, even if the login or the team name contains hyphens. The repositories of the exercise itself
     * (template, solution and tests) identify nobody and stay as they are.
     */
    private static String withoutParticipant(MatchResult repository) {
        String projectKey = repository.group(1);
        String name = repository.group(2);
        int separator = name.indexOf('-');
        if (separator == -1 || EXERCISE_REPOSITORY_SUFFIXES.contains(name.substring(separator + 1))) {
            return Matcher.quoteReplacement(repository.group());
        }
        return Matcher.quoteReplacement("/git/" + projectKey + "/" + name.substring(0, separator) + ".git");
    }
}
