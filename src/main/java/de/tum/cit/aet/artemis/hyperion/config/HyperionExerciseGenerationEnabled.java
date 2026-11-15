package de.tum.cit.aet.artemis.hyperion.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition guarding the agentic exercise-generation feature specifically (not all of Hyperion).
 * <p>
 * On top of {@link HyperionEnabled} it requires the dedicated {@code artemis.hyperion.exercise-generation.enabled} opt-in and the {@code core}, {@code localci}, and
 * {@code localvc} profiles, because generation uses the integrated LocalCI / LocalVC lifecycle and persists generated exercise content through core repository/database services.
 * None of that exists on a Jenkins deployment or a build-agent-only node, so the REST controller and orchestration engine are not registered there. The other Hyperion features
 * (problem statement, quiz, FAQ) stay gated on {@link HyperionEnabled} alone.
 */
public class HyperionExerciseGenerationEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper = new ArtemisConfigHelper();

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        return artemisConfigHelper.isHyperionExerciseGenerationEnabled(environment);
    }
}
