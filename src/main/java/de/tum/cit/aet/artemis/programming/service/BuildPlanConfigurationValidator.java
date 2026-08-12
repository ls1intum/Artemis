package de.tum.cit.aet.artemis.programming.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.programming.dto.BuildContainerDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;

/**
 * Validates the build plan configuration of a programming exercise.
 * <p>
 * The same rules apply wherever a build plan is saved, i.e. both when the whole exercise is updated and when only the
 * build plan is updated from the build plan editor, so both paths validate through this class. A violation names the
 * container, and where applicable the phase, it occurred in, so that an instructor can tell which part of a build plan
 * with several containers has to be fixed.
 */
public final class BuildPlanConfigurationValidator {

    private static final String ENTITY_NAME = "buildConfig";

    private BuildPlanConfigurationValidator() {
    }

    /**
     * Creates a bad request for a build plan violation that names the offending part of the build plan.
     * <p>
     * The client resolves the text of the alert from the "message" property and interpolates the values of the "params"
     * property into it, so a parameterized error has to carry both: without "message" the client falls back to showing
     * the untranslated default message.
     *
     * @param defaultMessage        the message shown if the error key has no translation
     * @param errorKey              the key of the translation to show
     * @param translationParameters the values interpolated into the translation, e.g. the name of the container
     * @return the exception to throw
     */
    private static BadRequestAlertException badRequest(String defaultMessage, String errorKey, Map<String, Object> translationParameters) {
        return new BadRequestAlertException(ErrorConstants.PARAMETERIZED_TYPE, defaultMessage, ENTITY_NAME, errorKey,
                Map.of("message", "error." + errorKey, "params", translationParameters));
    }

    /**
     * Validates that a build plan can be executed, i.e. that it defines at least one container, that the container names
     * are unique, and that every container has valid build phases. A legacy build plan that carries a flat list of phases
     * is validated as the single container it is normalized into.
     *
     * @param buildPlan the build plan to validate
     * @throws BadRequestAlertException if the build plan violates any of the rules above
     */
    public static void validate(BuildPlanPhasesDTO buildPlan) {
        final List<BuildContainerDTO> containers = buildPlan.effectiveContainers();
        // an empty build plan would leave the exercise without any way to build a submission
        if (containers.isEmpty()) {
            throw new BadRequestAlertException("A build plan must contain at least one build phase", ENTITY_NAME, "emptyBuildPlan");
        }

        final Set<String> containerNames = new HashSet<>();
        for (final BuildContainerDTO container : containers) {
            validateContainerName(container, containerNames);
            validatePhasesOf(container);
        }
    }

    private static void validateContainerName(BuildContainerDTO container, Set<String> alreadyUsedNames) {
        if (container.name() == null || !BuildContainerDTO.BUILD_CONTAINER_NAME_PATTERN.matcher(container.name()).matches()) {
            throw badRequest("Invalid build container name", "invalidBuildContainerName", Map.of("container", String.valueOf(container.name())));
        }
        if (!alreadyUsedNames.add(container.name().toLowerCase(Locale.ROOT))) {
            throw badRequest("Build container names must be unique", "duplicateBuildContainerName", Map.of("container", container.name()));
        }
    }

    private static void validatePhasesOf(BuildContainerDTO container) {
        if (container.phases() == null || container.phases().isEmpty()) {
            throw badRequest("A build container must contain at least one build phase", "emptyBuildContainer", Map.of("container", container.name()));
        }

        // phase names only have to be unique within their container, as containers execute independently of each other
        final Set<String> phaseNames = new HashSet<>();
        for (final BuildPhaseDTO phase : container.phases()) {
            if (phase == null || phase.name() == null || !BuildPhaseDTO.BUILD_PHASE_NAME_PATTERN.matcher(phase.name()).matches()) {
                throw badRequest("Invalid build phase name", "invalidBuildPhaseName",
                        Map.of("container", container.name(), "phase", phase == null ? "" : String.valueOf(phase.name())));
            }

            final String normalizedName = phase.name().toLowerCase(Locale.ROOT);
            if (BuildPhaseDTO.RESERVED_PHASE_NAMES.contains(normalizedName)) {
                throw badRequest("Build phase names must not use reserved names", "reservedBuildPhaseName", Map.of("container", container.name(), "phase", phase.name()));
            }
            if (!phaseNames.add(normalizedName)) {
                throw badRequest("Build phase names must be unique", "duplicateBuildPhaseName", Map.of("container", container.name(), "phase", phase.name()));
            }

        }
        // A blank script is intentionally accepted: rejecting it would also apply to the full exercise update, which
        // re-validates the exercise's already-stored build phases on every save, and to importing a file whose build phases
        // predate this check, neither of which has an in-place way to fix the offending phase. A blank script is dropped on
        // write by @JsonInclude(NON_EMPTY) on BuildPhaseDTO and defaulted back to '' on read by the client parser
        // (isBuildPhase in build-plan-phases.model.ts), so it is harmless: a no-op phase, not a corrupted plan.
    }
}
