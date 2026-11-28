package de.tum.cit.aet.artemis.deimos.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import de.tum.cit.aet.artemis.core.config.llm.LlmPresetAssignment;
import de.tum.cit.aet.artemis.core.config.llm.LlmPresetAssignments;

/**
 * The LLM preset of Deimos under {@code artemis.deimos.llm-preset}. Deimos sends student code to this model, so the administrator always assigns
 * it explicitly; there is no {@code default}.
 */
@Configuration
@Conditional(DeimosEnabled.class)
@Lazy
public class DeimosLlmConfiguration implements LlmPresetAssignments {

    /** The key that assigns the preset of Deimos. */
    public static final String KEY = "artemis.deimos.llm-preset";

    private final String preset;

    public DeimosLlmConfiguration(@Value("${" + KEY + ":}") String preset) {
        this.preset = preset;
    }

    @Override
    public List<LlmPresetAssignment> llmPresetAssignments() {
        return List.of(LlmPresetAssignment.ofSingle(KEY, preset, LlmPresetAssignment.Usage.CHAT));
    }
}
