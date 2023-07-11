package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.*;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.TextBlock;

/**
 * Spring Data repository for the TextBlock entity.
 */
@Repository
public interface TextBlockRepository extends JpaRepository<TextBlock, String> {

    @EntityGraph(type = LOAD, attributePaths = { "cluster" })
    Set<TextBlock> findAllWithEagerClusterBySubmissionId(Long id);

    Set<TextBlock> findAllBySubmissionId(Long id);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllBySubmission_Id(Long submissionId);

    /**
     * Interface used to define return type for `countOtherBlocksInClusterBySubmissionId`
     */
    interface TextBlockCount {

        String getBlockId();

        Long getNumberOfOtherBlocks();
    }

    /**
     * For the given Submission `id` returns a list of raw object array representing two columns.
     * First index/column corresponds to the TextBlock `id` while the second one corresponds to
     * the number of other blocks in the same cluster as given block with id = `id`.
     * For all TextBlock's of the Submission with the given `id`
     * finds their respective cluster and retrieves the number of other blocks in the same cluster
     *
     * @param submissionId the id of the Submission
     * @return the number of other TextBlock's in the same cluster as the block with given `id`
     */
    @Query("""
            SELECT tb.id as blockId, COUNT(DISTINCT tball.id) as numberOfOtherBlocks
            FROM TextSubmission s
            LEFT JOIN TextBlock tb ON s.id = tb.submission.id
            LEFT JOIN TextCluster tc ON tb.cluster.id = tc.id
            LEFT JOIN TextBlock tball ON tc.id = tball.cluster.id AND tball.id <> tb.id
            WHERE s.id = :#{#submissionId}
            GROUP BY tb.id
            """)
    List<TextBlockCount> countOtherBlocksInSameClusterForSubmissionId(@Param("submissionId") Long submissionId);
}
