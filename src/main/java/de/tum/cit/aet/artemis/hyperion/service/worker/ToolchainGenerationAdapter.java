package de.tum.cit.aet.artemis.hyperion.service.worker;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationToolchain;

/** Qualified language/toolchain implementation, including its authoring and mandatory verification policy. */
public interface ToolchainGenerationAdapter extends GenerationEngine {

    /** @return the exact capability this adapter implements */
    GenerationToolchain toolchain();
}
