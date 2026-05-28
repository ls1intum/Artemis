package de.tum.cit.aet.artemis.proof.grader;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.proof.config.ProofEnabled;

/**
 * Collects every {@link ProofGrader} bean and exposes lookup by {@link GraderType}.
 * Adding a new grader at M3+ is one bean declaration; no dispatcher changes required.
 */
@Conditional(ProofEnabled.class)
@Lazy
@Service
public class GraderRegistry {

    private final List<ProofGrader> graders;

    private Map<GraderType, ProofGrader> byType = Map.of();

    public GraderRegistry(List<ProofGrader> graders) {
        this.graders = graders;
    }

    /**
     * Builds the {@link GraderType}-keyed lookup table. Invoked automatically by Spring;
     * exposed as {@code public} so tests can construct the registry outside the container.
     */
    @PostConstruct
    public void index() {
        Map<GraderType, ProofGrader> map = new EnumMap<>(GraderType.class);
        for (ProofGrader grader : graders) {
            if (map.put(grader.getType(), grader) != null) {
                throw new IllegalStateException("Multiple ProofGrader beans registered for type " + grader.getType());
            }
        }
        this.byType = Map.copyOf(map);
    }

    /**
     * @param type the grader discriminator
     * @return the bean registered for the type
     * @throws IllegalArgumentException if no grader is registered for the type
     */
    public ProofGrader getGrader(GraderType type) {
        ProofGrader grader = byType.get(type);
        if (grader == null) {
            throw new IllegalArgumentException("No ProofGrader registered for type " + type);
        }
        return grader;
    }
}
