package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationCapabilitiesDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerRegistryService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/** Shared admission and presentation policy; cloning a released course source does not grant permission to modify it. */
@Service
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationCapabilityService {

    private static final String ENTITY_NAME = "hyperionExerciseGeneration";

    private final GenerationRequestService requestService;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final ProgrammingExerciseRepository exerciseRepository;

    private final GenerationJobService jobs;

    private final GenerationWorkerRegistryService workers;

    public GenerationCapabilityService(GenerationRequestService requestService, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository,
            ProgrammingExerciseRepository exerciseRepository, GenerationJobService jobs, GenerationWorkerRegistryService workers) {
        this.requestService = requestService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.exerciseRepository = exerciseRepository;
        this.jobs = jobs;
        this.workers = workers;
    }

    /**
     * Describes supported actions without admitting work or reserving capacity.
     *
     * @param exercise authoritative exercise including its student participations
     * @return capability snapshot; admission rechecks the same policy
     */
    public ExerciseGenerationCapabilitiesDTO describe(ProgrammingExercise exercise) {
        boolean supported = supportsConfiguration(exercise);
        String restriction = supported ? mutationRestriction(exercise) : "unsupportedGenerationLanguage";
        boolean mutable = supported && restriction == null;
        boolean variant = supported && (!exercise.isExamExercise() || mutable);
        return new ExerciseGenerationCapabilitiesDTO(supported, mutable, mutable, variant, jobs.hasActiveJob(exercise.getId()),
                supported && workers.hasAvailableGenerationSandboxSlot(LanguageGenerationProfile.toolchainFor(exercise)), restriction);
    }

    /**
     * Rejects unsupported repository configurations before any worker or provisioner is invoked.
     *
     * @param exercise authoritative exercise
     */
    public void requireSupportedConfiguration(ProgrammingExercise exercise) {
        if (!requestService.isGenerationSupported(exercise)) {
            throw new BadRequestAlertException("Whole-exercise generation is not available for programming language '" + exercise.getProgrammingLanguage() + "' and project type '"
                    + exercise.getProjectType() + "': the verifier does not support this configuration.", ENTITY_NAME, "unsupportedGenerationLanguage");
        }
        if (!auxiliaryRepositoryRepository.findByExerciseId(exercise.getId()).isEmpty()) {
            throw new BadRequestAlertException("Whole-exercise generation is not available for exercises with auxiliary repositories: the verifier only models the solution, "
                    + "template, and tests repositories.", ENTITY_NAME, "unsupportedGenerationLanguage");
        }
    }

    /**
     * Checks the lifecycle restriction shared by in-place authoring and undo. A mutation slot still fences concurrent assignment and participation starts.
     *
     * @param exercise authoritative exercise including its student participations
     */
    public void requireMutable(ProgrammingExercise exercise) {
        String restriction = mutationRestriction(exercise);
        if (restriction != null) {
            String message = switch (restriction) {
                case "exerciseAlreadyReleased" -> "Hyperion generation can only modify unreleased draft exercises.";
                case "exerciseHasParticipations" -> "Hyperion generation can only modify exercises without student participations.";
                default -> "Hyperion can only modify an exam before student exams or test runs are assigned.";
            };
            throw new BadRequestAlertException(message, ENTITY_NAME, restriction);
        }
    }

    private boolean supportsConfiguration(ProgrammingExercise exercise) {
        return requestService.isGenerationSupported(exercise) && auxiliaryRepositoryRepository.findByExerciseId(exercise.getId()).isEmpty();
    }

    /**
     * Returns the same restriction used by admission, without checking worker availability or spending a budget.
     *
     * @param exercise authoritative exercise including its student participations
     * @return stable error key, or null when the lifecycle permits mutation
     */
    @Nullable
    public String mutationRestriction(ProgrammingExercise exercise) {
        if (GenerationRequestService.hasReleaseDateInThePast(exercise)) {
            return "exerciseAlreadyReleased";
        }
        if (exercise.getStudentParticipations() != null && !exercise.getStudentParticipations().isEmpty()) {
            return "exerciseHasParticipations";
        }
        if (exercise.isExamExercise() && !exerciseRepository.isUnreleasedAndWithoutStudentParticipations(exercise.getId())) {
            return "exerciseAlreadyAssigned";
        }
        return null;
    }
}
