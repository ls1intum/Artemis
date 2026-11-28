package de.tum.cit.aet.artemis.atlas.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

import de.tum.cit.aet.artemis.core.config.llm.LlmPresetAssignment;
import de.tum.cit.aet.artemis.core.config.llm.LlmPresetAssignments;

/**
 * The LLM presets of the LLM-backed part of Atlas, under {@code artemis.atlas.atlasllm.llm-presets}. A task without its own key uses {@code default}.
 *
 * @param defaultPreset the preset of every task without its own key
 * @param chat          the interactive chat agent for instructors
 * @param orchestrator  the autonomous competency orchestrator
 * @param worker        the stateless workers the orchestrator delegates to
 * @param flavorStrip   the removal of narrative text from exercises before extraction
 */
@ConfigurationProperties(prefix = "artemis.atlas.atlasllm.llm-presets", ignoreUnknownFields = false)
public record AtlasLlmPresets(@Name("default") @Nullable String defaultPreset, @Nullable String chat, @Nullable String orchestrator, @Nullable String worker,
        @Nullable String flavorStrip) implements LlmPresetAssignments {

    static final String KEY = "artemis.atlas.atlasllm.llm-presets";

    @Override
    public List<LlmPresetAssignment> llmPresetAssignments() {
        Map<String, @Nullable String> tasks = new LinkedHashMap<>();
        tasks.put("chat", chat);
        tasks.put("orchestrator", orchestrator);
        tasks.put("worker", worker);
        tasks.put("flavor-strip", flavorStrip);
        return LlmPresetAssignment.ofMap(KEY, defaultPreset, tasks);
    }

    /** @return the preset of the chat agent; validated at startup */
    public String chatPreset() {
        return resolve("chat", chat);
    }

    /** @return the preset of the orchestrator; validated at startup */
    public String orchestratorPreset() {
        return resolve("orchestrator", orchestrator);
    }

    /** @return the preset of the workers; validated at startup */
    public String workerPreset() {
        return resolve("worker", worker);
    }

    /** @return the preset of flavor stripping; validated at startup */
    public String flavorStripPreset() {
        return resolve("flavor-strip", flavorStrip);
    }

    private String resolve(String task, @Nullable String taskPreset) {
        String preset = LlmPresetAssignment.ofMap(KEY, task, taskPreset, defaultPreset).preset();
        if (preset == null) {
            throw new IllegalStateException("No LLM preset for " + KEY + "." + task);
        }
        return preset;
    }
}
