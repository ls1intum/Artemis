package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATLAS_AGENT;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.atlas.config.AtlasAgentConfiguration;
import de.tum.cit.aet.artemis.atlas.config.AtlasAgentEnabled;

/**
 * Service for Atlas Agent functionality with Azure OpenAI integration.
 * Handles chat interactions and competency-related AI assistance.
 */
@Lazy
@Service
@AtlasAgentEnabled
@Profile(PROFILE_ATLAS_AGENT)
public class AtlasAgentService {

    private static final Logger log = LoggerFactory.getLogger(AtlasAgentService.class);

    private final RestTemplate restTemplate;

    private final AtlasAgentConfiguration configuration;

    public AtlasAgentService(@Qualifier("atlasAgentRestTemplate") RestTemplate restTemplate, AtlasAgentConfiguration configuration) {
        this.restTemplate = restTemplate;
        this.configuration = configuration;
    }

    /**
     * Process a chat message for the given course and return AI response.
     *
     * @param message  The user's message
     * @param courseId The course ID for context
     * @return AI response
     */
    public CompletableFuture<String> processChatMessage(String message, Long courseId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Processing chat message for course {}: {}", courseId, message.substring(0, Math.min(message.length(), 50)));

                // Prepare Azure OpenAI request
                String systemPrompt = """
                        You are an AI assistant that helps instructors work with Atlas competency management and Artemis course management.

                        Your role is to:
                        1. Help instructors discover and map competencies to course content using Atlas
                        2. Facilitate understanding of course competencies and their relationships
                        3. Provide clear, structured suggestions with explanations
                        4. Answer questions about competency frameworks and learning objectives

                        Guidelines:
                        - Always provide clear, concise responses
                        - Focus on educational competency management
                        - Be helpful and professional in all interactions
                        - If you cannot help with a specific request, explain why clearly

                        You are specifically designed to work with the Atlas competency management system within Artemis.
                        """;

                // Create request body for Azure OpenAI
                Map<String, Object> requestBody = Map.of("messages",
                        List.of(Map.of("role", "system", "content", systemPrompt), Map.of("role", "user", "content", String.format("Course ID: %d\n\n%s", courseId, message))),
                        "max_tokens", 800, "temperature", 0.7);

                // Set headers for Azure OpenAI
                HttpHeaders headers = new HttpHeaders();
                headers.set("Content-Type", "application/json");
                headers.set("api-key", configuration.getAzureApiKey());

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                // Make request to Azure OpenAI
                String url = configuration.getAzureEndpoint() + "?api-version=" + configuration.getAzureApiVersion();
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

                // Extract response text
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null && responseBody.containsKey("choices")) {
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                    if (!choices.isEmpty()) {
                        Map<String, Object> firstChoice = choices.get(0);
                        Map<String, Object> messageObj = (Map<String, Object>) firstChoice.get("message");
                        String content = (String) messageObj.get("content");

                        log.info("Successfully processed chat message for course {}", courseId);
                        return content != null ? content : "I apologize, but I couldn't generate a response.";
                    }
                }

                log.warn("No valid response from Azure OpenAI for course {}", courseId);
                return "I apologize, but I couldn't generate a response. Please try again.";

            }
            catch (Exception e) {
                log.error("Error processing chat message for course {}: {}", courseId, e.getMessage(), e);
                return "I apologize, but I'm having trouble processing your request right now. Please try again later.";
            }
        });
    }

    /**
     * Check if the Atlas Agent service is available and properly configured.
     *
     * @return true if the service is ready, false otherwise
     */
    public boolean isAvailable() {
        try {
            return restTemplate != null && configuration != null;
        }
        catch (Exception e) {
            log.warn("Atlas Agent service availability check failed: {}", e.getMessage());
            return false;
        }
    }
}
