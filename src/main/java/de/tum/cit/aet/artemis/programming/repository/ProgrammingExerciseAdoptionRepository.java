package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Aggregate counts of how widely the optional features of programming exercises are switched on, for the admin feature
 * usage page.
 * <p>
 * A repository of its own rather than more methods on {@link ProgrammingExerciseRepository}: these queries share nothing
 * with the ones there, they are only ever used by one caller, and that repository is already one of the largest classes in
 * the code base.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ProgrammingExerciseAdoptionRepository extends ArtemisJpaRepository<ProgrammingExercise, Long> {

    @Query("""
            SELECT COUNT(exercise)
            FROM ProgrammingExercise exercise
            WHERE exercise.staticCodeAnalysisEnabled IS TRUE
            """)
    long countWithStaticCodeAnalysis();

    @Query("""
            SELECT COUNT(exercise)
            FROM ProgrammingExercise exercise
            WHERE exercise.allowOnlineEditor IS TRUE
            """)
    long countWithOnlineEditor();

    @Query("""
            SELECT COUNT(exercise)
            FROM ProgrammingExercise exercise
            WHERE exercise.allowOfflineIde IS TRUE
            """)
    long countWithOfflineIde();

    @Query("""
            SELECT COUNT(exercise)
            FROM ProgrammingExercise exercise
            WHERE exercise.allowOnlineIde IS TRUE
            """)
    long countWithOnlineIde();

    @Query("""
            SELECT COUNT(exercise)
            FROM ProgrammingExercise exercise
            WHERE exercise.releaseTestsWithExampleSolution IS TRUE
            """)
    long countWithReleasedTests();

    @Query("""
            SELECT COUNT(exercise)
            FROM ProgrammingExercise exercise
            WHERE exercise.submissionPolicy IS NOT NULL
            """)
    long countWithSubmissionPolicy();

    @Query("""
            SELECT COUNT(DISTINCT repository.exercise.id)
            FROM AuxiliaryRepository repository
            WHERE repository.exercise IS NOT NULL
            """)
    long countWithAuxiliaryRepositories();
}
