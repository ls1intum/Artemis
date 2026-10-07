package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;

/**
 * The failure dialog shows the generated post-mortem above the terminal detail, so the prompt must be told which of
 * the two cleanup outcomes actually happened. When the deletion of the half-finished clone fails, the pipeline keeps
 * the clone and asks the instructor to delete it manually — a summary claiming it was deleted contradicts the very
 * message next to it. These tests pin the two facts the prompt is grounded in.
 */
class ExerciseVariantFailureSummaryPromptTest {

    private static final String TEMPLATE = "prompts/hyperion/variants/failure_summary.st";

    private final HyperionPromptTemplateService templateService = new HyperionPromptTemplateService();

    @Test
    void shouldStateThatNothingNeedsCleanupWhenTheCloneWasDeleted() {
        String rendered = render(ExerciseVariantGenerationPipelineService.cleanupStatus(true, null));

        assertThat(rendered).contains("has been DELETED automatically; nothing needs manual cleanup");
        assertThat(rendered).doesNotContain("could NOT be deleted automatically");
    }

    @Test
    void shouldStateThatTheCloneSurvivedWhenItsDeletionFailed() {
        String rendered = render(ExerciseVariantGenerationPipelineService.cleanupStatus(false, 42L));

        assertThat(rendered).contains("could NOT be deleted automatically and still exists in the course").contains("(id 42)").contains("deleted manually");
        assertThat(rendered).doesNotContain("nothing needs manual cleanup");
    }

    private String render(String cleanupStatus) {
        Map<String, String> variables = new HashMap<>();
        variables.put("cleanupStatus", cleanupStatus);
        variables.put("exerciseType", "QUIZ");
        variables.put("sourceTitle", "Source quiz");
        variables.put("failedPhase", "TRANSFORMING");
        variables.put("failureDetail", "the model returned no usable output");
        variables.put("targetDifficulty", "unchanged");
        variables.put("domainText", "unchanged");
        variables.put("narrativeStyle", "consistent with the source");
        variables.put("additionalInstructions", "none");
        variables.put("changePlan", "No change plan was produced yet.");
        variables.put("stepOutputs", "PLANNING: ok");
        return templateService.render(TEMPLATE, variables);
    }
}
