package de.tum.cit.aet.artemis.hyperion.web.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class AdminHyperionWorkerResourceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void disabledGenerationDoesNotPretendToHaveAnEmptyRegistry() throws Exception {
        userUtilService.addAdmin("");
        request.performMvcRequest(get("/api/hyperion/admin/workers")).andExpect(status().isNotFound());
    }
}
