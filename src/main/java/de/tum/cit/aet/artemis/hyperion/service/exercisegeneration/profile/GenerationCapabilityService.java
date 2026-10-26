package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationCapabilitiesDTO;
import de.tum.cit.aet.artemis.hyperion.runtime.verification.AresTestHarness;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationSeedService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerRegistryService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;

/** Shared admission and presentation policy; cloning a released course source does not grant permission to modify it. */
@Service
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationCapabilityService {

    private static final String ENTITY_NAME = "hyperionExerciseGeneration";

    private final GenerationRequestService requestService;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final GenerationJobService jobs;

    private final GenerationWorkerRegistryService workers;

    private final GenerationSeedService seeds;

    public GenerationCapabilityService(GenerationRequestService requestService, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, GenerationJobService jobs,
            GenerationWorkerRegistryService workers, GenerationSeedService seeds) {
        this.requestService = requestService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.jobs = jobs;
        this.workers = workers;
        this.seeds = seeds;
    }

    /**
     * Describes supported actions without admitting work or reserving capacity.
     *
     * @param exercise authoritative exercise including its student participations
     * @return capability snapshot; admission rechecks the same policy
     */
    public ExerciseGenerationCapabilitiesDTO describe(ProgrammingExercise exercise) {
        boolean configurationSupported = supportsConfiguration(exercise);
        String harnessRestriction = configurationSupported ? harnessRestriction(exercise) : null;
        boolean supported = configurationSupported && harnessRestriction == null;
        String restriction = !configurationSupported ? "unsupportedGenerationLanguage" : harnessRestriction != null ? harnessRestriction : mutationRestriction(exercise);
        boolean mutable = supported && restriction == null;
        boolean variant = supported;
        return new ExerciseGenerationCapabilitiesDTO(supported, mutable, mutable, variant, jobs.hasActiveJob(exercise.getId()),
                supported && workers.hasAvailableGenerationSandboxSlot(LanguageGenerationProfile.toolchainFor(exercise)), restriction);
    }

    /**
     * Rejects unsupported repository configurations before any worker or provisioner is invoked.
     *
     * @param exercise authoritative exercise
     */
    public void requireSupportedConfiguration(ProgrammingExercise exercise) {
        if (exercise.isExamExercise()) {
            throw new BadRequestAlertException("Whole-exercise generation is available only for course exercises.", ENTITY_NAME, "unsupportedGenerationLanguage");
        }
        if (!requestService.isGenerationSupported(exercise)) {
            throw new BadRequestAlertException("Whole-exercise generation is not available for programming language '" + exercise.getProgrammingLanguage() + "' and project type '"
                    + exercise.getProjectType() + "': the verifier does not support this configuration.", ENTITY_NAME, "unsupportedGenerationLanguage");
        }
        if (!auxiliaryRepositoryRepository.findByExerciseId(exercise.getId()).isEmpty()) {
            throw new BadRequestAlertException("Whole-exercise generation is not available for exercises with auxiliary repositories: the verifier only models the solution, "
                    + "template, and tests repositories.", ENTITY_NAME, "unsupportedGenerationLanguage");
        }
        String harnessRestriction = harnessRestriction(exercise);
        if ("generationHarnessUnavailable".equals(harnessRestriction)) {
            throw new ServiceUnavailableAlertException("The tests repository harness could not be read. Please try again after repository access is restored.", ENTITY_NAME,
                    "generationHarnessUnavailable");
        }
        if (harnessRestriction != null) {
            throw new BadRequestAlertException(
                    "Exercise generation requires an Ares 2 Gradle or Maven test harness. Migrate the exercise harness manually before starting generation; "
                            + "Hyperion does not change build dependencies or security settings automatically.",
                    ENTITY_NAME, "generationHarnessUnsupported");
        }
    }

    /**
     * Checks the course-only lifecycle restriction shared by in-place authoring and undo.
     *
     * @param exercise authoritative exercise including its student participations
     */
    public void requireMutable(ProgrammingExercise exercise) {
        String restriction = mutationRestriction(exercise);
        if (restriction != null) {
            String message = switch (restriction) {
                case "exerciseAlreadyReleased" -> "Hyperion generation can only modify unreleased draft exercises.";
                case "exerciseHasParticipations" -> "Hyperion generation can only modify exercises without student participations.";
                default -> "Whole-exercise generation is available only for course exercises.";
            };
            throw new BadRequestAlertException(message, ENTITY_NAME, restriction);
        }
    }

    private boolean supportsConfiguration(ProgrammingExercise exercise) {
        return !exercise.isExamExercise() && requestService.isGenerationSupported(exercise) && auxiliaryRepositoryRepository.findByExerciseId(exercise.getId()).isEmpty();
    }

    @Nullable
    private String harnessRestriction(ProgrammingExercise exercise) {
        try {
            return AresTestHarness.reasons(LanguageGenerationProfile.buildToolFor(exercise), seeds.readTestHarness(exercise)).isEmpty() ? null : "generationHarnessUnsupported";
        }
        catch (RuntimeException failure) {
            return "generationHarnessUnavailable";
        }
    }

    /**
     * Returns the same restriction used by admission, without checking worker availability or spending a budget.
     *
     * @param exercise authoritative exercise including its student participations
     * @return stable error key, or null when the lifecycle permits mutation
     */
    @Nullable
    public String mutationRestriction(ProgrammingExercise exercise) {
        if (exercise.isExamExercise()) {
            return "unsupportedGenerationLanguage";
        }
        if (GenerationRequestService.hasReleaseDateInThePast(exercise)) {
            return "exerciseAlreadyReleased";
        }
        if (exercise.getStudentParticipations() != null && !exercise.getStudentParticipations().isEmpty()) {
            return "exerciseHasParticipations";
        }
        return null;
    }
}
