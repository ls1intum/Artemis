package de.tum.cit.aet.artemis.exercise.service;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.exception.ExerciseVersioningException;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExercisePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ExercisePersistenceService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseVersionService exerciseVersionService;


    public ExercisePersistenceService(ProgrammingExerciseRepository programmingExerciseRepository,
                                      ExerciseRepository exerciseRepository,
                                      ExerciseVersionService exerciseVersionService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.exerciseRepository = exerciseRepository;
        this.exerciseVersionService = exerciseVersionService;
    }

    /**
     * Polymorphic save for any Exercise subtype. Spring Data's JpaRepository exposes a generic save method
     * that returns the concrete subtype. This works regardless of inheritance mapping (@SecondaryTable, discriminator, etc.).
     * Automatically handles versioning for both new and existing exercises.
     *
     * @param exercise the exercise to save
     * @return the saved exercise
     * @throws ExerciseVersioningException if versioning fails
     */
    public <S extends Exercise> S save(S exercise) {
        return isNewExercise(exercise) ? saveNewExercise(exercise) : saveExistingExercise(exercise);
    }

    /**
     * Polymorphic saveAndFlush for any Exercise subtype.
     * Automatically handles versioning for both new and existing exercises.
     *
     * @param exercise the exercise to save and flush
     * @return the saved exercise
     * @throws ExerciseVersioningException if versioning fails
     */
    public <S extends Exercise> S saveAndFlush(S exercise) {
        return isNewExercise(exercise) ? saveAndFlushNewExercise(exercise) : saveAndFlushExistingExercise(exercise);
    }

    /**
     * Saves a new programming exercise using the specialized creation repository method.
     * This method is only intended for new programming exercise creation.
     *
     * @param exercise the programming exercise to save (should be new)
     * @return the saved programming exercise
     */
    public ProgrammingExercise saveProgrammingExerciseWithEagerRefetch(ProgrammingExercise exercise) {
        if (!isNewExercise(exercise)) {
            return saveProgrammingExerciseForCreationExisting(exercise);
        }
        return saveProgrammingExerciseForCreationNew(exercise);
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Determines if an exercise is new (doesn't have an ID yet).
     */
    private boolean isNewExercise(Exercise exercise) {
        return exercise.getId() == null;
    }

    /**
     * Saves a new exercise with post-save versioning.
     *
     * @throws ExerciseVersioningException if versioning fails
     */
    private <S extends Exercise> S saveNewExercise(S exercise) {
        // Save new exercise (no pre-save versioning needed)
        S savedExercise = exerciseRepository.save(exercise);

        // Create initial version after the exercise gets its ID
        exerciseVersionService.onExerciseCreated(savedExercise);

        return savedExercise;
    }

    /**
     * Saves an existing exercise with pre-save versioning.
     *
     * @throws ExerciseVersioningException if versioning fails
     */
    private <S extends Exercise> S saveExistingExercise(S exercise) {
        // Pre-save versioning for existing exercises
        exerciseVersionService.onSaveExercise(exercise);

        // Save the updated exercise
        return exerciseRepository.save(exercise);
    }

    /**
     * Saves and flushes a new exercise with post-save versioning.
     *
     * @throws ExerciseVersioningException if versioning fails
     */
    private <S extends Exercise> S saveAndFlushNewExercise(S exercise) {
        // Save and flush new exercise (no pre-save versioning needed)
        S savedExercise = exerciseRepository.saveAndFlush(exercise);

        // Create initial version after the exercise gets its ID
        exerciseVersionService.onExerciseCreated(savedExercise);

        return savedExercise;
    }

    /**
     * Saves and flushes an existing exercise with pre-save versioning.
     *
     * @throws ExerciseVersioningException if versioning fails
     */
    private <S extends Exercise> S saveAndFlushExistingExercise(S exercise) {
        // Pre-save versioning for existing exercises
        exerciseVersionService.onSaveExercise(exercise);

        // Save and flush the updated exercise
        return exerciseRepository.saveAndFlush(exercise);
    }

    /**
     * Saves a new programming exercise for creation with post-save versioning.
     *
     * @throws ExerciseVersioningException if versioning fails
     */
    private ProgrammingExercise saveProgrammingExerciseForCreationNew(ProgrammingExercise exercise) {
        // Save new programming exercise for creation (no pre-save versioning needed)
        ProgrammingExercise savedExercise = programmingExerciseRepository.saveForCreation(exercise);

        // Create initial version after the exercise gets its ID
        exerciseVersionService.onExerciseCreated(savedExercise);

        return savedExercise;
    }

    private ProgrammingExercise saveProgrammingExerciseForCreationExisting(ProgrammingExercise exercise) {
        // Pre-save versioning for existing programming exercises
        exerciseVersionService.onSaveExercise(exercise);

        // Save the updated programming exercise
        return programmingExerciseRepository.saveForCreation(exercise);
    }

}
