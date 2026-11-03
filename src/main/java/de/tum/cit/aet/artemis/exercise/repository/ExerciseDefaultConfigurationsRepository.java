package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.function.BooleanSupplier;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * The statements that give an exercise the permanent configuration rows of optional modules. Such a module's own
 * repositories exist only while the module is enabled on this server, but its configuration row is permanent all the same:
 * it is created together with the exercise, also on a server that has the module switched off, so that switching the module
 * on later only changes a flag in a row that is already there. The rows are therefore written here, in the exercise module,
 * with JPQL statements that name the module's entity only by its entity name, so no class of the module is referenced, and
 * every setting comes from the column defaults of the changelog.
 * <p>
 * Today this is the configuration of Metis, the comprehension check, which every programming exercise owns. The rows every
 * server has, the team assignment and the plagiarism detection configuration, are written through their own repositories.
 * <p>
 * The rows are written once, when the exercise is stored, and the exercise module never touches them afterwards: it has no use
 * for them, so neither an update of the exercise nor any other of its paths pays a statement for a module the server may not
 * use. An exercise whose creation failed between storing it and inserting its row is left to the module that owns the row,
 * which reads a missing row as the defaults and adds it before it first writes one.
 * <p>
 * Each statement is a plain, single read or write: no transaction spans them and no lock is held across them. The bound
 * {@link Exercise} type is just the subject the statements share; the inherited CRUD operations are unused.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ExerciseDefaultConfigurationsRepository extends ArtemisJpaRepository<Exercise, Long> {

    /**
     * Inserts the module configuration rows a stored exercise is missing, which for a programming exercise is its Metis
     * configuration. Every path that stores a new exercise makes this call once, right after the first save; for a programming
     * exercise that is one unlocked read and one insert.
     * <p>
     * The read keeps the call idempotent without a refused statement: {@code ExerciseConfigurationService.initialize} promises
     * that a path which ran it already is not an error, and test fixtures call it again on exercises they store a second time.
     * It takes no lock and needs no transaction: a missing row is found by a plain read and added by a plain insert. If two calls
     * add the same row at the same moment, its key lets one insert win and the other finds the row already there. A row that
     * exists is never replaced, so the settings it holds survive any number of calls.
     *
     * @param exercise the stored exercise
     */
    default void ensureDefaultConfigurations(Exercise exercise) {
        long exerciseId = exercise.getId();
        if (exercise.getExerciseType() == ExerciseType.PROGRAMMING && !hasMetisExerciseConfig(exerciseId)) {
            insertUnlessAlreadyThere(() -> initializeMetisExerciseConfig(exerciseId), () -> hasMetisExerciseConfig(exerciseId));
        }
    }

    private static void insertUnlessAlreadyThere(Runnable insert, BooleanSupplier exists) {
        try {
            insert.run();
        }
        catch (DataIntegrityViolationException e) {
            // a concurrent call adding the same row first is what was wanted; any other violation is a real failure
            if (!exists.getAsBoolean()) {
                throw e;
            }
        }
    }

    /**
     * Whether an exercise has its Metis configuration. A plain read: it takes no lock.
     *
     * @param exerciseId the id of the exercise
     * @return true if the row exists
     */
    @Query("""
            SELECT COUNT(config) > 0
            FROM MetisExerciseConfig config
            WHERE config.exerciseId = :exerciseId
            """)
    boolean hasMetisExerciseConfig(@Param("exerciseId") long exerciseId);

    /**
     * Creates the Metis configuration of a newly stored programming exercise. Only the key is written, so the column defaults
     * supply every setting: Metis is switched off, and the row only waits for an instructor to switch it on. A plain insert:
     * callers check {@link #hasMetisExerciseConfig(long)} first, so no statement ever locks an index range.
     *
     * @param exerciseId the id of the programming exercise
     */
    @Modifying
    @Transactional // ok because of the insert
    @Query("""
            INSERT INTO MetisExerciseConfig (exerciseId)
            VALUES (:exerciseId)
            """)
    void initializeMetisExerciseConfig(@Param("exerciseId") long exerciseId);
}
