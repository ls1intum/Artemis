package de.tum.cit.aet.artemis.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.cit.aet.artemis.core.domain.ErrorLocation;
import de.tum.cit.aet.artemis.core.domain.ErrorSeverity;
import de.tum.cit.aet.artemis.core.dto.ErrorAnalysisPageDTO;
import de.tum.cit.aet.artemis.core.dto.ErrorGroupDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ErrorAnalysisIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "erroranalysis";

    private static final String REPORT_ENDPOINT = "/api/core/public/client-errors";

    private static final String ANALYSIS_ENDPOINT = "/api/core/admin/error-analysis";

    /** The analysis is shared by every test of the JVM, so each test marks its messages to find them again. Letters only, digits are replaced by placeholders. */
    private String marker;

    /** The release that the server runs, which the clients of this test report. */
    @Value("${artemis.version}")
    private String artemisVersion;

    @BeforeEach
    void setUp() {
        marker = "analysistest" + ThreadLocalRandom.current().ints(16, 'a', 'z' + 1).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append);
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        // the admin endpoints resolve the authenticated login against the database
        userUtilService.addAdmin(TEST_PREFIX);
    }

    private void report(String severity, String message) throws Exception {
        report(severity, message, artemisVersion);
    }

    private void report(String severity, String message, String clientVersion) throws Exception {
        request.postWithoutResponseBody(REPORT_ENDPOINT, Map.of("severity", severity, "message", marker + " " + message, "clientVersion", clientVersion), HttpStatus.OK);
    }

    /**
     * The groups of this test. The analysis is shared with every other test, so the most recent groups come first, and there is room for
     * all of them on the first page.
     */
    private List<ErrorGroupDTO> analysis(MultiValueMap<String, String> params) throws Exception {
        params.putIfAbsent("sortBy", List.of("LAST_SEEN"));
        params.putIfAbsent("size", List.of("100"));
        return request.get(ANALYSIS_ENDPOINT, HttpStatus.OK, ErrorAnalysisPageDTO.class, params).groups().stream().filter(group -> group.message().startsWith(marker)).toList();
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
    @ValueSource(strings = { "RELEASE", "DAY", "WEEK", "MONTH" })
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void acceptsEveryTimeSpan(String span) throws Exception {
        report("ERROR", "within every time span");

        assertThat(analysis(params("span", span))).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "span=YEAR", "location=EVERYWHERE", "minSeverity=FATAL", "sortBy=COLOR", "direction=SIDEWAYS", "page=-1", "size=0", "size=101", "page=x" })
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void rejectsAnUnknownFilter(String parameter) throws Exception {
        request.get(ANALYSIS_ENDPOINT + "?" + parameter, HttpStatus.BAD_REQUEST, ErrorAnalysisPageDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void showsTheReleaseThatIsRunningByDefaultAndLeavesOutClientsOfAnyOtherRelease() throws Exception {
        report("ERROR", "client of this release");
        report("ERROR", "client of an earlier release", "0.0.1-earlier");

        assertThat(analysis(params())).extracting(group -> group.message().substring(marker.length() + 1)).containsExactly("client of this release");
        assertThat(analysis(params("span", "DAY"))).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void returnsOnePageWithTheTotalsOfAllPages() throws Exception {
        report("ERROR", "first of three");
        report("ERROR", "second of three");
        report("ERROR", "third of three");

        ErrorAnalysisPageDTO page = request.get(ANALYSIS_ENDPOINT, HttpStatus.OK, ErrorAnalysisPageDTO.class, params("size", "1", "page", "0", "sortBy", "LAST_SEEN"));

        assertThat(page.groups()).hasSize(1);
        assertThat(page.totalGroups()).isGreaterThanOrEqualTo(3);
        assertThat(page.totalErrors()).isGreaterThanOrEqualTo(3);
        // the most recent group comes first when sorted by the last time, which is the third report
        assertThat(page.groups().getFirst().message()).endsWith("third of three");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void sortsBeforeItCutsPagesAndAcceptsEverySortFieldInBothDirections() throws Exception {
        report("ERROR", "alpha");
        report("ERROR", "omega");

        List<String> ascending = request.get(ANALYSIS_ENDPOINT, HttpStatus.OK, ErrorAnalysisPageDTO.class, params("sortBy", "MESSAGE", "direction", "ASC", "size", "100")).groups()
                .stream().map(ErrorGroupDTO::message).toList();

        assertThat(ascending).isSortedAccordingTo(String::compareTo);
        for (String field : List.of("COUNT", "SEVERITY", "LOCATION", "MESSAGE", "FIRST_SEEN", "LAST_SEEN")) {
            for (String direction : List.of("ASC", "DESC")) {
                request.get(ANALYSIS_ENDPOINT, HttpStatus.OK, ErrorAnalysisPageDTO.class, params("sortBy", field, "direction", direction));
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void forbidsCachingTheAnalysisSoThatEveryVisitComputesItAgain() throws Exception {
        request.performMvcRequest(get(ANALYSIS_ENDPOINT)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void showsAnErrorThatWasReportedMomentsAgoWithoutWaitingForAnyCache() throws Exception {
        assertThat(analysis(params())).isEmpty();

        report("ERROR", "reported right now");

        assertThat(analysis(params())).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void forbidsEverybodyButAdministrators() throws Exception {
        request.get(ANALYSIS_ENDPOINT, HttpStatus.FORBIDDEN, ErrorAnalysisPageDTO.class);
    }

    @Test
    void requiresAuthentication() throws Exception {
        request.get(ANALYSIS_ENDPOINT, HttpStatus.UNAUTHORIZED, ErrorAnalysisPageDTO.class);
    }
}
