package de.tum.cit.aet.artemis.aiworker.web.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/** AI Worker coordination is disabled in the independent context. */
class AdminAiWorkerResourceTest extends AbstractSpringIntegrationIndependentTest {

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void disabledCoordinationDoesNotPretendToHaveAnEmptyRegistry() throws Exception {
        userUtilService.addAdmin("");
        request.performMvcRequest(get("/api/aiworker/admin/workers")).andExpect(status().isNotFound());
    }
}
