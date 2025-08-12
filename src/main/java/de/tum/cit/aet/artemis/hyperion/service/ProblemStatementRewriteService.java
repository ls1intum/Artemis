package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;

@Service
@Lazy
@Profile(PROFILE_HYPERION)
public class ProblemStatementRewriteService {

    private static final Logger log = LoggerFactory.getLogger(ProblemStatementRewriteService.class);

    private final ChatClient chatClient;

    private final PromptTemplateService templates;

    public ProblemStatementRewriteService(@Autowired(required = false) ChatClient chatClient, PromptTemplateService templates) {
        this.chatClient = chatClient;
        this.templates = templates;
    }

    public ProblemStatementRewriteResponseDTO rewriteProblemStatement(User user, Course course, String problemStatementText) throws NetworkingException {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        if (course == null) {
            throw new IllegalArgumentException("Course must not be null");
        }
        if (problemStatementText == null || problemStatementText.trim().isEmpty()) {
            throw new IllegalArgumentException("Problem statement text must not be null or empty");
        }

        log.info("Rewriting problem statement for course {} by user {}", course.getId(), user.getLogin());

        try {
            if (chatClient == null) {
                throw new NetworkingException("Spring AI ChatClient is not configured");
            }

            String rendered = templates.render("/prompts/hyperion/rewrite.st", Map.of("text", problemStatementText.trim()));
            String content = chatClient.prompt()
                    .system("You are an expert technical writing assistant for programming exercise problem statements. Return only the rewritten statement, no explanations.")
                    .user(rendered).call().content();

            if (content == null || content.trim().isEmpty()) {
                throw new NetworkingException("AI returned empty response");
            }

            String result = content.trim();
            boolean improved = !result.equals(problemStatementText.trim());
            return new ProblemStatementRewriteResponseDTO(result, improved);
        }
        catch (TransientAiException e) {
            log.warn("Transient AI error during problem statement rewrite: {}", e.getMessage());
            throw new NetworkingException("Temporary AI service issue. Please retry.", e);
        }
        catch (NonTransientAiException e) {
            log.error("Non-transient AI error during problem statement rewrite: {}", e.getMessage());
            throw new NetworkingException("AI request failed due to configuration or input. Check model and request.", e);
        }
        catch (Exception e) {
            throw new NetworkingException("An unexpected error occurred while rewriting problem statement", e);
        }
    }
}
