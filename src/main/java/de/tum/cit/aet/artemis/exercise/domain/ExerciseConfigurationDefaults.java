package de.tum.cit.aet.artemis.exercise.domain;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;

/**
 * Decides which settings a newly persisted exercise starts with.
 * <p>
 * An import builds the new exercise from a loaded one, so it can still carry the source's stored settings: a detached
 * object with an id, which persisting would reject, or an unloaded proxy, which cannot be copied. Both must start from
 * a fresh row of their own, so the new exercise gets a copy of what is loaded or the defaults, never the source's row.
 */
public final class ExerciseConfigurationDefaults {

    private ExerciseConfigurationDefaults() {
    }

    /**
     * Replaces the stored settings of a stored exercise without replacing their row: the replacement takes over the stored id,
     * so saving updates the permanent row in place, and a null replacement leaves the stored settings as they are.
     *
     * @param stored           the settings the exercise currently carries
     * @param replacement      the settings to carry from now on, may be null
     * @param exerciseIsStored whether the exercise itself is already stored
     * @param <T>              the type of the settings
     * @return the settings the exercise carries afterwards
     */
    public static <T extends DomainObject> T replaceKeepingStoredId(@Nullable T stored, @Nullable T replacement, boolean exerciseIsStored) {
        if (!exerciseIsStored || stored == null) {
            return replacement;
        }
        if (replacement == null) {
            return stored;
        }
        replacement.setId(stored.getId());
        return replacement;
    }

    /**
     * The team settings a new exercise is persisted with.
     *
     * @param current what the exercise carries, may be null, a stored row of another exercise or an unloaded proxy
     * @return a settings object without an id: the given one if it is new, a copy if it is loaded, the defaults otherwise
     */
    public static TeamAssignmentConfig forNewExercise(@Nullable TeamAssignmentConfig current) {
        if (current == null || !Hibernate.isInitialized(current)) {
            return new TeamAssignmentConfig();
        }
        return current.getId() == null ? current : current.copyTeamAssignmentConfig();
    }

    /**
     * The plagiarism detection settings a new exercise is persisted with.
     *
     * @param current what the exercise carries, may be null, a stored row of another exercise or an unloaded proxy
     * @return a settings object without an id: the given one if it is new, a copy if it is loaded, the defaults otherwise
     */
    public static PlagiarismDetectionConfig forNewExercise(@Nullable PlagiarismDetectionConfig current) {
        if (current == null || !Hibernate.isInitialized(current)) {
            return PlagiarismDetectionConfig.createDefault();
        }
        return current.getId() == null ? current : new PlagiarismDetectionConfig(current);
    }
}
