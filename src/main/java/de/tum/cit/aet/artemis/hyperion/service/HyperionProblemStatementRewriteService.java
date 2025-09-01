package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;

/**
 * Service for rewriting problem statements using Spring AI.
 */
@Service
@Lazy
@Profile(PROFILE_HYPERION)
public class HyperionProblemStatementRewriteService {

    private static final Logger log = LoggerFactory.getLogger(HyperionProblemStatementRewriteService.class);

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templates;

    /**
     * Creates a new ProblemStatementRewriteService.
     *
     * @param chatClient the AI chat client (optional)
     * @param templates  prompt template service
     */
    public HyperionProblemStatementRewriteService(@Autowired(required = false) ChatClient chatClient, HyperionPromptTemplateService templates) {
        this.chatClient = chatClient;
        this.templates = templates;
    }

    /**
     * Rewrites the given problem statement text for the provided course and user.
     *
     * @param user                 the requesting user
     * @param course               the course context
     * @param problemStatementText the original problem statement
     * @return the rewrite result including whether it was improved
     * @throws NetworkingException if the AI call fails or returns empty content
     */
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

            String rendered = templates.render("/prompts/hyperion/rewrite_problem_statement.st", Map.of("text", problemStatementText.trim()));
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

        catch (Exception e) {
            throw new NetworkingException("An unexpected error occurred while rewriting problem statement", e);
        }
    }
}
