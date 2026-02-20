package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;

/**
 * Spring Data repository for the AuxiliaryRepository entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface AuxiliaryRepositoryRepository extends ArtemisJpaRepository<AuxiliaryRepository, Long> {

    List<AuxiliaryRepository> findByExerciseId(Long exerciseId);

    /**
     * Finds the branch for the given auxiliary repository id.
     *
     * @param auxiliaryRepositoryId the id of the auxiliary repository for which to find the branch of the corresponding programming exercise
     * @return the branch name, potentially null if no branch is set or if the exercise does not exist
     */
    @Nullable
    @Query("""
            SELECT DISTINCT a.exercise.buildConfig.branch
            FROM AuxiliaryRepository a
            WHERE a.id = :auxiliaryRepositoryId
            """)
    String findBranchByRepoId(@Param("auxiliaryRepositoryId") long auxiliaryRepositoryId);

    @Query("""
            SELECT a
            FROM AuxiliaryRepository a
            WHERE a.exercise.id = :programmingExerciseId
            """)
    List<AuxiliaryRepository> findByProgrammingExerciseId(@Param("programmingExerciseId") long programmingExerciseId);
}
