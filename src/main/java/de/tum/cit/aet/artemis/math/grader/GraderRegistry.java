package de.tum.cit.aet.artemis.math.grader;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.math.config.MathEnabled;

/**
 * Collects every {@link MathGrader} bean and exposes lookup by {@link GraderType}.
 * Adding a new grader at M3+ is one bean declaration; no dispatcher changes required.
 */
@Conditional(MathEnabled.class)
@Lazy
@Service
public class GraderRegistry {

    private final List<MathGrader> graders;

    private Map<GraderType, MathGrader> byType = Map.of();

    public GraderRegistry(List<MathGrader> graders) {
        this.graders = graders;
    }

    /**
     * Builds the {@link GraderType}-keyed lookup table. Invoked automatically by Spring;
     * exposed as {@code public} so tests can construct the registry outside the container.
     */
    @PostConstruct
    public void index() {
        Map<GraderType, MathGrader> map = new EnumMap<>(GraderType.class);
        for (MathGrader grader : graders) {
            if (map.put(grader.getType(), grader) != null) {
                throw new IllegalStateException("Multiple MathGrader beans registered for type " + grader.getType());
            }
        }
        this.byType = Map.copyOf(map);
    }

    /**
     * @param type the grader discriminator
     * @return the bean registered for the type
     * @throws IllegalArgumentException if no grader is registered for the type
     */
    public MathGrader getGrader(GraderType type) {
        MathGrader grader = byType.get(type);
        if (grader == null) {
            throw new IllegalArgumentException("No MathGrader registered for type " + type);
        }
        return grader;
    }
}
