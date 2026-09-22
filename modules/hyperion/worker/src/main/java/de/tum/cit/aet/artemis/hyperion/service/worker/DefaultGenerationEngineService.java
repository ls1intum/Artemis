package de.tum.cit.aet.artemis.hyperion.service.worker;

import java.util.HashMap;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.protocol.ExecutionIdentity;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationAssignment;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationToolchain;

/** Selects one qualified adapter for the worker's immutable toolchain/image pair; no language-specific behavior lives here. */
@Service
@Primary
public class DefaultGenerationEngineService implements GenerationEngine {

    private final ToolchainGenerationAdapter adapter;

    public DefaultGenerationEngineService(@Value("${artemis.aiworker.profile}") String profile, List<ToolchainGenerationAdapter> adapters) {
        var installed = new HashMap<GenerationToolchain, ToolchainGenerationAdapter>();
        for (var candidate : adapters) {
            if (installed.putIfAbsent(candidate.toolchain(), candidate) != null) {
                throw new IllegalArgumentException("Duplicate generation adapter: " + candidate.toolchain().id());
            }
        }
        adapter = installed.get(new GenerationToolchain(profile));
        if (adapter == null) {
            throw new IllegalArgumentException("No installed generation adapter for toolchain: " + profile);
        }
    }

    @Override
    public GenerationOutput generate(GenerationAssignment assignment, BooleanSupplier cancelled, GenerationObserver progress, Consumer<GenerationOutput> checkpoint) {
        if (!adapter.toolchain().equals(assignment.brief().toolchain())) {
            throw new IllegalArgumentException("Assignment does not match this worker's toolchain");
        }
        return adapter.generate(assignment, cancelled, progress, checkpoint);
    }

    @Override
    public boolean requestCancel(ExecutionIdentity identity) {
        return adapter.requestCancel(identity);
    }
}
