package de.tum.cit.aet.artemis.exercise.repository;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;

/**
 * Spring Data JPA repository for the Exercise entity for Tests.
 */
@Primary
@Lazy
@Repository
public interface ExerciseVersionTestRepository extends ExerciseVersionRepository {

    List<ExerciseVersion> findAllByExerciseId(Long exerciseId);

    /**
     * Replaces the stored snapshot with raw json.
     * <p>
     * A snapshot of an older shape cannot be produced through the record, which writes only the fields it declares
     * today, so a test that needs one has to put it into the column directly.
     *
     * @param exerciseVersionId the id of the exercise version to overwrite
     * @param snapshot          the json to store
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query(value = "UPDATE exercise_version SET exercise_snapshot = CAST(:snapshot AS json) WHERE id = :exerciseVersionId", nativeQuery = true)
    void overwriteSnapshot(@Param("exerciseVersionId") long exerciseVersionId, @Param("snapshot") String snapshot);
}
