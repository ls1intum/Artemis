package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
    Set<TextBlock> findAllWithEagerClusterBySubmissionId(Long id);

    Set<TextBlock> findAllBySubmissionId(Long id);

    @EntityGraph(type = LOAD, attributePaths = { "submission" })
    Set<TextBlock> findAllBySubmissionIdIn(Set<Long> submissionIdList);

    void deleteAllBySubmission_Id(Long submissionId);
}
