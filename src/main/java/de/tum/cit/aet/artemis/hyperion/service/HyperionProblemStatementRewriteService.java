package de.tum.cit.aet.artemis.hyperion.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * Service for rewriting problem statements using Spring AI.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionProblemStatementRewriteService {

    private static final Logger log = LoggerFactory.getLogger(HyperionProblemStatementRewriteService.class);

    private final ChatClient chatClient;

    private final HyperionPromptTemplateService templateService;

    private final ObservationRegistry observationRegistry;

    /**
     * Creates a new ProblemStatementRewriteService.
     *
     * @param chatClient      the AI chat client (optional)
     * @param templateService prompt template service
     */
    public HyperionProblemStatementRewriteService(ChatClient chatClient, HyperionPromptTemplateService templateService, ObservationRegistry observationRegistry) {
        this.chatClient = chatClient;
        this.templateService = templateService;
        this.observationRegistry = observationRegistry;
    }

    /**
     * Rewrites the given problem statement text for the provided course and user.
     *
     * @param course               the course context
     * @param problemStatementText the original problem statement
     * @return the rewrite result including whether it was improved
     */
    public ProblemStatementRewriteResponseDTO rewriteProblemStatement(Course course, String problemStatementText) {
        log.debug("Rewriting problem statement for course {}", course.getId());

        String resourcePath = "/prompts/hyperion/rewrite_problem_statement.st";
        Map<String, String> input = Map.of("text", problemStatementText.trim());
        String renderedPrompt = templateService.render(resourcePath, input);
        // Create a root observation containing LLM calls' traces
        var parent = Observation.createNotStarted("hyperion.rewrite", observationRegistry).contextualName("problem statement rewrite for course id: " + course.getId())
                .lowCardinalityKeyValue(io.micrometer.common.KeyValue.of("ai.span", "true"))
                .highCardinalityKeyValue(io.micrometer.common.KeyValue.of("lf.trace.name", "problem statement rewrite for course id: " + course.getId())).start();
        try (Observation.Scope scope = parent.openScope()) {
            // @formatter:off
            String responseContent = chatClient
                    .prompt()
                    .system("You are an expert technical writing assistant for programming exercise problem statements. Return only the rewritten statement, no explanations.")
                    .user(renderedPrompt)
                    .call()
                    .content();
            // @formatter:on
            String result = responseContent.trim();
            boolean improved = !result.equals(problemStatementText.trim());
            return new ProblemStatementRewriteResponseDTO(result, improved);
        }
        catch (RuntimeException e) {
            log.warn("Failed to obtain or parse AI response for {} - returning original text", resourcePath, e);
            return new ProblemStatementRewriteResponseDTO(problemStatementText.trim(), false);
        }
        finally {
            parent.stop();
        }
    }
}
