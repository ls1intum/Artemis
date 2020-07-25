package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;

/**
 * Spring Data repository for the TextBlock entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TextBlockRepository extends JpaRepository<TextBlock, String> {

    Optional<Set<TextBlock>> findAllByCluster(TextCluster textCluster);

    @EntityGraph(type = LOAD, attributePaths = { "cluster" })
    List<TextBlock> findAllWithEagerClusterBySubmissionId(Long id);

    /*
    @Query("SELECT distinct b FROM TextBlock b " +
        "LEFT JOIN FETCH Submission s on b.submission = s " +
        "LEFT JOIN Participation p ON s.participation = p " +
        "LEFT JOIN TextExercise e ON p.exercise = e " +
        "WHERE e.id = :#{#exerciseId} AND b.treeId IS NOT NULL")
    List<TextBlock> findAllByTreeIdExistsAndExerciseId(@Param("id") Long exerciseId);
     */

    // TODO: Test this query
    List<TextBlock> findAllBySubmission_Participation_Exercise_IdAndTreeIdNotNull(Long exerciseId);

    List<TextBlock> findAllBySubmissionId(Long id);
}
