package de.tum.cit.aet.artemis.core.config.llm;

import java.util.List;

/**
 * Implemented by a bean of each module that uses LLMs. The bean exists only where the module runs, so the startup validation checks exactly the
 * assignments of the active modules on this node.
 */
public interface LlmPresetAssignments {

    /**
     * @return the preset assignments the active module needs on this node
     */
    List<LlmPresetAssignment> llmPresetAssignments();
}
