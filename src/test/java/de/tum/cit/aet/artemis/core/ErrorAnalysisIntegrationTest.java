package de.tum.cit.aet.artemis.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.cit.aet.artemis.core.domain.ErrorLocation;
import de.tum.cit.aet.artemis.core.domain.ErrorSeverity;
import de.tum.cit.aet.artemis.core.dto.ErrorGroupDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ErrorAnalysisIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "erroranalysis";

    private static final String REPORT_ENDPOINT = "/api/core/public/client-errors";

    private static final String ANALYSIS_ENDPOINT = "/api/core/admin/error-analysis";

    /** The analysis is shared by every test of the JVM, so each test marks its messages to find them again. Letters only, digits are replaced by placeholders. */
    private String marker;

    @BeforeEach
    void setUp() {
        marker = "analysistest" + ThreadLocalRandom.current().ints(16, 'a', 'z' + 1).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append);
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        // the admin endpoints resolve the authenticated login against the database
        userUtilService.addAdmin(TEST_PREFIX);
    }

    private void report(String severity, String message) throws Exception {
        request.postWithoutResponseBody(REPORT_ENDPOINT, Map.of("severity", severity, "message", marker + " " + message), HttpStatus.OK);
    }

    private List<ErrorGroupDTO> analysis(MultiValueMap<String, String> params) throws Exception {
        return request.getList(ANALYSIS_ENDPOINT, HttpStatus.OK, ErrorGroupDTO.class, params).stream().filter(group -> group.message().startsWith(marker)).toList();
    }

    private static MultiValueMap<String, String> params(String... keyValues) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.add(keyValues[i], keyValues[i + 1]);
        }
        return params;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void groupsTheReportsOfClientsAndAnswersWithTheirCountAndTimes() throws Exception {
        report("ERROR", "Unknown question type 3");
        report("ERROR", "Unknown question type 7");
        report("CRITICAL", "Cannot start");

        List<ErrorGroupDTO> groups = analysis(params());

        assertThat(groups).hasSize(2);
        ErrorGroupDTO unknownType = groups.stream().filter(group -> group.message().contains("Unknown question type")).findFirst().orElseThrow();
        assertThat(unknownType.location()).isEqualTo(ErrorLocation.CLIENT);
        assertThat(unknownType.severity()).isEqualTo(ErrorSeverity.ERROR);
        assertThat(unknownType.message()).endsWith("Unknown question type <n>");
        assertThat(unknownType.count()).isEqualTo(2);
        assertThat(unknownType.firstSeen()).isNotNull().isBeforeOrEqualTo(unknownType.lastSeen());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void showsOnlyErrorsAndCriticalErrorsByDefaultAndAllLevelsOnRequest() throws Exception {
        report("WARN", "a warning");
        report("INFO", "a hint");
        report("ERROR", "an error");
        report("CRITICAL", "a critical error");

        assertThat(analysis(params())).extracting(ErrorGroupDTO::severity).containsExactlyInAnyOrder(ErrorSeverity.ERROR, ErrorSeverity.CRITICAL);
        assertThat(analysis(params("minSeverity", "WARN"))).extracting(ErrorGroupDTO::severity).containsExactlyInAnyOrder(ErrorSeverity.WARN, ErrorSeverity.ERROR,
                ErrorSeverity.CRITICAL);
        assertThat(analysis(params("minSeverity", "CRITICAL"))).extracting(ErrorGroupDTO::severity).containsExactly(ErrorSeverity.CRITICAL);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void filtersByLocation() throws Exception {
        report("ERROR", "reported by a client");

        assertThat(analysis(params("location", "CLIENT"))).hasSize(1);
        assertThat(analysis(params("location", "SERVER"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "DAY", "WEEK", "MONTH" })
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void acceptsEveryTimeSpan(String span) throws Exception {
        report("ERROR", "within every time span");

        assertThat(analysis(params("span", span))).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "span=YEAR", "location=EVERYWHERE", "minSeverity=FATAL" })
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void rejectsAnUnknownFilter(String parameter) throws Exception {
        request.getList(ANALYSIS_ENDPOINT + "?" + parameter, HttpStatus.BAD_REQUEST, ErrorGroupDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void forbidsEverybodyButAdministrators() throws Exception {
        request.getList(ANALYSIS_ENDPOINT, HttpStatus.FORBIDDEN, ErrorGroupDTO.class);
    }

    @Test
    void requiresAuthentication() throws Exception {
        request.getList(ANALYSIS_ENDPOINT, HttpStatus.UNAUTHORIZED, ErrorGroupDTO.class);
    }
}
