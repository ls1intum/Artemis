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
     */
    public ProblemStatementRewriteResponseDTO rewriteProblemStatement(User user, Course course, String problemStatementText) {
        log.info("Rewriting problem statement for course {} by user {}", course.getId(), user.getLogin());

        String resourcePath = "/prompts/hyperion/rewrite_problem_statement.st";
        Map<String, String> input = Map.of("text", problemStatementText.trim());
        String renderedPrompt = templates.render(resourcePath, input);
        try {
            String responseContent = chatClient.prompt()
                    .system("You are an expert technical writing assistant for programming exercise problem statements. Return only the rewritten statement, no explanations.")
                    .user(renderedPrompt).call().content();
            String result = responseContent.trim();
            boolean improved = !result.equals(problemStatementText.trim());
            return new ProblemStatementRewriteResponseDTO(result, improved);
        }
        catch (RuntimeException e) {
            log.warn("Failed to obtain or parse AI response for {} - returning original text", resourcePath, e);
            return new ProblemStatementRewriteResponseDTO(problemStatementText.trim(), false);
        }
    }
}
