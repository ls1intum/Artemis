package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;

/**
 * Spring Data repository for the ProgrammingExerciseTestCase entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ProgrammingExerciseTestCaseRepository extends ArtemisJpaRepository<ProgrammingExerciseTestCase, Long> {

    Set<ProgrammingExerciseTestCase> findByExerciseId(long exerciseId);

    Set<ProgrammingExerciseTestCase> findByExerciseIdAndActive(long exerciseId, Boolean active);

    /**
     * Returns the number of test cases marked as {@link Visibility#AFTER_DUE_DATE} for the given exercise.
     *
     * @param exerciseId the exercise which test cases should be considered.
     * @return the number of test cases marked as {@link Visibility#AFTER_DUE_DATE}.
     */
    @Query("""
            SELECT COUNT(DISTINCT testCase)
            FROM ProgrammingExerciseTestCase testCase
            WHERE testCase.exercise.id = :exerciseId
                AND testCase.visibility = de.tum.cit.aet.artemis.assessment.domain.Visibility.AFTER_DUE_DATE
            """)
    long countAfterDueDateByExerciseId(@Param("exerciseId") long exerciseId);
}
