package de.tum.cit.aet.artemis.iris;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

class IrisAdminDashboardResourceIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irisadmindashboard";

    private static final String BASE_URL = "/api/iris/admin/dashboard/";

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getOverview_asAdmin_succeeds() throws Exception {
        request.get(BASE_URL + "overview?from=2026-05-26T00:00:00Z&to=2026-05-27T00:00:00Z", HttpStatus.OK, Object.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getOverview_asStudent_forbidden() throws Exception {
        request.get(BASE_URL + "overview?from=2026-05-26T00:00:00Z&to=2026-05-27T00:00:00Z", HttpStatus.FORBIDDEN, Object.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getOverview_invalidWindow_badRequest() throws Exception {
        request.get(BASE_URL + "overview?from=2026-05-27T00:00:00Z&to=2026-05-26T00:00:00Z", HttpStatus.BAD_REQUEST, Object.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getConfig_asAdmin_succeeds() throws Exception {
        request.get(BASE_URL + "config", HttpStatus.OK, Object.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getTimeSeries_asAdmin_succeeds() throws Exception {
        request.get(BASE_URL + "time-series?from=2026-05-26T00:00:00Z&to=2026-05-27T00:00:00Z&span=DAY&metric=SESSIONS", HttpStatus.OK, Object.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getBreakdown_asAdmin_succeeds() throws Exception {
        request.get(BASE_URL + "breakdown?from=2026-05-26T00:00:00Z&to=2026-05-27T00:00:00Z&dimension=MODEL", HttpStatus.OK, Object.class);
    }
}
