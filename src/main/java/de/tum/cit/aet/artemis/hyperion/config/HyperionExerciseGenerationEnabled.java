package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;

import de.tum.cit.aet.artemis.core.config.ArtemisConfigHelper;

/**
 * Condition guarding the agentic exercise-generation feature specifically (not all of Hyperion).
 * <p>
 * On top of {@link HyperionEnabled} it also requires the {@code localci} profile, because generation is built entirely on the integrated LocalCI / LocalVC lifecycle: it drives a
 * hardened sandbox on a LocalCI build agent, persists through LocalVC repositories with a LocalCI trigger, and verifies with the production LocalCI test/SCA parsers. None of that
 * exists on a Jenkins deployment, so the feature is inert there — the REST controller and orchestration engine are not registered, and the client hides the entry point by checking
 * the same {@code localci} profile. The other Hyperion features (problem statement, quiz, FAQ) do not depend on LocalCI and stay gated on {@link HyperionEnabled} alone.
 */
public class HyperionExerciseGenerationEnabled implements Condition {

    private final ArtemisConfigHelper artemisConfigHelper = new ArtemisConfigHelper();

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        return artemisConfigHelper.isHyperionEnabled(environment) && environment.acceptsProfiles(Profiles.of(PROFILE_LOCALCI));
    }
}
