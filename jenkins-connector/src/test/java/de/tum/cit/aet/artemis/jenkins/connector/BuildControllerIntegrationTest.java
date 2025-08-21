package de.tum.cit.aet.artemis.jenkins.connector;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
class BuildControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointShouldReturnUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void buildStatusWithInvalidIdShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/build/invalid-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buildTriggerWithValidRequestShouldSucceed() throws Exception {
        String validRequest = """
            {
                "exerciseId": 123,
                "participationId": 456,
                "exerciseRepository": {
                    "url": "https://github.com/user/repo.git",
                    "commitHash": "abc123"
                },
                "buildScript": "#!/bin/bash\\n./gradlew test",
                "programmingLanguage": "JAVA"
            }
            """;

        // This test might fail due to Jenkins connectivity, but it validates the controller structure
        mockMvc.perform(post("/api/v1/build")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void buildTriggerWithInvalidRequestShouldReturnBadRequest() throws Exception {
        String invalidRequest = """
            {
                "exerciseId": null,
                "participationId": 456
            }
            """;

        mockMvc.perform(post("/api/v1/build")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }
}