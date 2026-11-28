package de.tum.cit.aet.artemis.hyperion.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

import de.tum.cit.aet.artemis.core.config.llm.LlmPresetAssignment;
import de.tum.cit.aet.artemis.core.config.llm.LlmPresetAssignments;

/**
 * The LLM presets of Hyperion's authoring features, under {@code artemis.hyperion.llm-presets}. A task without its own key uses {@code default}.
 * Exercise generation, the quiz question pool and its effort profiles assign their presets in their own blocks.
 *
 * @param defaultPreset           the preset of every task without its own key
 * @param problemStatement        generating and refining problem statements
 * @param problemStatementRewrite rewriting problem statements
 * @param consistencyCheck        the consistency check of programming exercises
 * @param checklist               the quality checklist
 * @param assessmentCriteria      generating assessment criteria
 * @param quizGeneration          generating quiz questions
 * @param codeGeneration          generating solution, template and test repositories
 * @param variants                exercise and quiz variants
 * @param faqRewrite              rewriting FAQs
 * @param metadataSuggestion      suggesting exercise metadata
 * @param competencyContext       summarizing the competency context of a course
 */
@ConfigurationProperties(prefix = "artemis.hyperion.llm-presets", ignoreUnknownFields = false)
public record HyperionLlmPresets(@Name("default") @Nullable String defaultPreset, @Nullable String problemStatement, @Nullable String problemStatementRewrite,
        @Nullable String consistencyCheck, @Nullable String checklist, @Nullable String assessmentCriteria, @Nullable String quizGeneration, @Nullable String codeGeneration,
        @Nullable String variants, @Nullable String faqRewrite, @Nullable String metadataSuggestion, @Nullable String competencyContext) implements LlmPresetAssignments {

    static final String KEY = "artemis.hyperion.llm-presets";

    /**
     * A Hyperion task that calls an LLM, with its key in {@code artemis.hyperion.llm-presets}.
     */
    public enum Task {

        PROBLEM_STATEMENT("problem-statement"), PROBLEM_STATEMENT_REWRITE("problem-statement-rewrite"), CONSISTENCY_CHECK("consistency-check"), CHECKLIST("checklist"),
        ASSESSMENT_CRITERIA("assessment-criteria"), QUIZ_GENERATION("quiz-generation"), CODE_GENERATION("code-generation"), VARIANTS("variants"), FAQ_REWRITE("faq-rewrite"),
        METADATA_SUGGESTION("metadata-suggestion"), COMPETENCY_CONTEXT("competency-context");

        private final String key;

        Task(String key) {
            this.key = key;
        }

        /** @return the key of the task in {@code artemis.hyperion.llm-presets} */
        public String key() {
            return key;
        }
    }

    @Override
    public List<LlmPresetAssignment> llmPresetAssignments() {
        Map<String, @Nullable String> tasks = new LinkedHashMap<>();
        for (Task task : Task.values()) {
            tasks.put(task.key(), configured(task));
        }
        return LlmPresetAssignment.ofMap(KEY, defaultPreset, tasks);
    }

    /**
     * @param task a Hyperion task
     * @return the preset of the task: its own key, otherwise {@code default}; validated at startup
     */
    public String presetFor(Task task) {
        String preset = LlmPresetAssignment.ofMap(KEY, task.key(), configured(task), defaultPreset).preset();
        if (preset == null) {
            throw new IllegalStateException("No LLM preset for " + KEY + "." + task.key());
        }
        return preset;
    }

    private @Nullable String configured(Task task) {
        return switch (task) {
            case PROBLEM_STATEMENT -> problemStatement;
            case PROBLEM_STATEMENT_REWRITE -> problemStatementRewrite;
            case CONSISTENCY_CHECK -> consistencyCheck;
            case CHECKLIST -> checklist;
            case ASSESSMENT_CRITERIA -> assessmentCriteria;
            case QUIZ_GENERATION -> quizGeneration;
            case CODE_GENERATION -> codeGeneration;
            case VARIANTS -> variants;
            case FAQ_REWRITE -> faqRewrite;
            case METADATA_SUGGESTION -> metadataSuggestion;
            case COMPETENCY_CONTEXT -> competencyContext;
        };
    }
}
