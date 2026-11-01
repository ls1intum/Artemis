package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.admin.service.RateLimitService;
import de.tum.cit.aet.artemis.core.domain.ClientErrorSeverity;
import de.tum.cit.aet.artemis.core.dto.ClientErrorReportDTO;

/**
 * Tests how the values of a client are cleaned before they are logged. That the entry reaches the log with the level of its
 * severity is covered by {@code ClientErrorReportIntegrationTest}, which sees the real log.
 */
class ClientErrorReportServiceTest {

    private final ClientErrorReportService service = new ClientErrorReportService(mock(RateLimitService.class));

    private static ClientErrorReportDTO report(String message) {
        return new ClientErrorReportDTO(ClientErrorSeverity.ERROR, message, null, null, null, null, null);
    }

    private String format(ClientErrorReportDTO report) {
        return service.formatEntry(report, null, false);
    }

    @Test
    void formatsTheContextOfTheReport() {
        var report = new ClientErrorReportDTO(ClientErrorSeverity.ERROR, "Unknown question type", null, "/courses/1/exams/2?token=secret#top", "9.8.0",
                Map.of("exerciseId", "42", "step", "3"), Map.of("feature", "quiz"));

        String entry = service.formatEntry(report, "Mozilla/5.0 (X11; Linux x86_64)", true);

        assertThat(entry).isEqualTo("Client error report [version=9.8.0, url=/courses/1/exams/2, authenticated=true, userAgent=Mozilla/5.0 (X11; Linux x86_64), "
                + "tags={feature=quiz}, extra={exerciseId=42, step=3}]: Unknown question type");
    }

    @Test
    void formatsAReportWithoutContext() {
        assertThat(format(report("No context"))).isEqualTo("Client error report [version=, url=, authenticated=false, userAgent=, tags={}, extra={}]: No context");
    }

    @Test
    void keepsAForgedLogEntryOnTheLineOfTheReport() {
        char lineSeparator = (char) 0x2028;
        char nextLine = (char) 0x0085;
        char bell = (char) 0x0007;
        char nul = (char) 0x0000;
        String forged = "boom\r\n2026-11-01 12:00:00 ERROR [main] Forged : admin logged in" + lineSeparator + "and" + nextLine + "more" + bell;
        var report = new ClientErrorReportDTO(ClientErrorSeverity.ERROR, forged, null, "/a\nb", "1\n2", Map.of("k\ney", "v\r\nalue"), Map.of("t" + nul + "ag", "x\ny"));

        String entry = service.formatEntry(report, "agent\r\nX-Forged: yes", false);

        assertThat(entry).doesNotContain("\n", "\r", String.valueOf(lineSeparator), String.valueOf(nextLine), String.valueOf(bell), String.valueOf(nul));
    }

    @Test
    void startsEveryLineOfTheStackWithATabSoThatNoLineLooksLikeALogEntry() {
        String stack = "Error: boom\n    at a (a.js:1:1)\r\n2026-11-01 12:00:00 ERROR [main] Forged : admin logged in\n    at b (b.js:2:2)";
        var report = new ClientErrorReportDTO(ClientErrorSeverity.ERROR, "boom", stack, null, null, null, null);

        String entry = format(report);

        String[] lines = entry.split("\n", -1);
        assertThat(lines).hasSize(5);
        assertThat(lines[0]).startsWith("Client error report [");
        assertThat(Arrays.stream(lines).skip(1)).allSatisfy(line -> assertThat(line).startsWith("\t"));
        assertThat(lines[3]).isEqualTo("\t2026-11-01 12:00:00 ERROR [main] Forged : admin logged in");
        assertThat(entry).doesNotContain("\r");
    }

    @ParameterizedTest
    @MethodSource("personalData")
    void removesPersonalDataFromTheMessage(String message, String expected) {
        assertThat(format(report(message))).endsWith("]: " + expected);
    }

    private static Stream<Arguments> personalData() {
        return Stream.of(Arguments.of("Could not load user=barney_young now", "Could not load [removed] now"),
                Arguments.of("Failed for User{id=1, login='barney'} here", "Failed for [removed] here"),
                Arguments.of("Mail to barney.young@example.org failed", "Mail to [removed] failed"));
    }

    @Test
    void removesPersonalDataFromTheStackAndTheContext() {
        var report = new ClientErrorReportDTO(ClientErrorSeverity.ERROR, "boom", "Error: user=barney\n    at a (barney@example.org)", null, null,
                Map.of("owner", "barney@example.org"), null);

        assertThat(format(report)).doesNotContain("barney");
    }

    @Test
    void removesTheParticipantFromRepositoriesButKeepsTheRepositoriesOfTheExercise() {
        String message = "Clone failed: https://artemis.example.org/git/ABC123/abc123-barney-young.git and /git/ABC123/abc123-solution.git and /git/ABC123/abc123-tests.git "
                + "and /git/ABC123/abc123-exercise.git and /git/ABC123/abc123.git";

        assertThat(format(report(message))).endsWith("]: Clone failed: https://artemis.example.org/git/ABC123/abc123.git and /git/ABC123/abc123-solution.git and "
                + "/git/ABC123/abc123-tests.git and /git/ABC123/abc123-exercise.git and /git/ABC123/abc123.git");
    }

    @Test
    void shortensEveryValueToItsLimit() {
        String tooLong = StringUtils.repeat('Q', 10_000);
        var report = new ClientErrorReportDTO(ClientErrorSeverity.ERROR, tooLong, tooLong, tooLong, tooLong, Map.of("k", tooLong), Map.of(tooLong, "v"));

        String entry = service.formatEntry(report, tooLong, false);

        // the message, the stack, the url, the version, the value of an extra entry, the key of a tag and the user agent
        int expected = ClientErrorReportDTO.MAX_MESSAGE_LENGTH + ClientErrorReportDTO.MAX_STACK_LENGTH + ClientErrorReportDTO.MAX_URL_LENGTH
                + ClientErrorReportDTO.MAX_VERSION_LENGTH + ClientErrorReportDTO.MAX_VALUE_LENGTH + ClientErrorReportDTO.MAX_KEY_LENGTH + 200;
        assertThat(StringUtils.countMatches(entry, 'Q')).isEqualTo(expected);
    }

    @Test
    void formatsAtMostTheAllowedNumberOfEntries() {
        Map<String, String> tooMany = new HashMap<>();
        for (int i = 0; i < 25; i++) {
            tooMany.put("key" + i, "v");
        }

        String entry = format(new ClientErrorReportDTO(ClientErrorSeverity.ERROR, "boom", null, null, null, tooMany, null));

        assertThat(StringUtils.countMatches(entry, "=v")).isEqualTo(ClientErrorReportDTO.MAX_ENTRIES);
    }
}
