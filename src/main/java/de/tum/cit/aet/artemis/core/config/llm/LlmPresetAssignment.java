package de.tum.cit.aet.artemis.core.config.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * One place where a module uses an LLM preset, as resolved from its {@code llm-preset} or {@code llm-presets} configuration.
 *
 * @param propertyKey     the key an administrator sets, for example {@code artemis.hyperion.llm-presets.checklist}
 * @param fallbackKey     the {@code default} key of the same {@code llm-presets} map, or null for a single {@code llm-preset} key
 * @param preset          the resolved preset name, or null when neither key is set
 * @param usesDefault     whether the preset comes from the {@code default} key
 * @param usage           what the preset is used for
 * @param requiresNoRetry whether calls must not be retried by the HTTP client (exercise generation retries and accounts on its own)
 */
public record LlmPresetAssignment(String propertyKey, @Nullable String fallbackKey, @Nullable String preset, boolean usesDefault, Usage usage, boolean requiresNoRetry) {

    /**
     * What an assigned preset is used for.
     */
    public enum Usage {
        /** Chat calls through the Responses API. */
        CHAT,
        /** Embedding calls. */
        EMBEDDING
    }

    /**
     * Resolves one task of an {@code llm-presets} map: its own key wins, otherwise the map's {@code default} key applies.
     *
     * @param mapKey        the key of the map, for example {@code artemis.hyperion.llm-presets}
     * @param task          the task key inside the map, for example {@code checklist}
     * @param taskPreset    the preset set for the task, or null
     * @param defaultPreset the preset set under {@code default}, or null
     * @return the assignment
     */
    public static LlmPresetAssignment ofMap(String mapKey, String task, @Nullable String taskPreset, @Nullable String defaultPreset) {
        String own = blankToNull(taskPreset);
        String fallback = blankToNull(defaultPreset);
        return new LlmPresetAssignment(mapKey + "." + task, mapKey + ".default", own != null ? own : fallback, own == null && fallback != null, Usage.CHAT, false);
    }

    /**
     * Resolves every task of an {@code llm-presets} map with the same rule and also checks a configured {@code default} value, even when every task has
     * its own key.
     *
     * @param mapKey        the key of the map
     * @param defaultPreset the preset set under {@code default}, or null
     * @param tasks         the task keys in their documented order, mapped to their configured presets (values may be null)
     * @return one assignment per task, plus one for the {@code default} key when it is set
     */
    public static List<LlmPresetAssignment> ofMap(String mapKey, @Nullable String defaultPreset, Map<String, @Nullable String> tasks) {
        List<LlmPresetAssignment> assignments = new ArrayList<>();
        tasks.forEach((task, preset) -> assignments.add(ofMap(mapKey, task, preset, defaultPreset)));
        if (blankToNull(defaultPreset) != null) {
            assignments.add(ofSingle(mapKey + ".default", defaultPreset, Usage.CHAT));
        }
        return List.copyOf(assignments);
    }

    /**
     * A single {@code llm-preset} key, which has no default.
     *
     * @param key    the key
     * @param preset the configured preset, or null
     * @param usage  what the preset is used for
     * @return the assignment
     */
    public static LlmPresetAssignment ofSingle(String key, @Nullable String preset, Usage usage) {
        return new LlmPresetAssignment(key, null, blankToNull(preset), false, usage, false);
    }

    /**
     * @return a copy that requires calls without transport retries
     */
    public LlmPresetAssignment withoutRetries() {
        return new LlmPresetAssignment(propertyKey, fallbackKey, preset, usesDefault, usage, true);
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
