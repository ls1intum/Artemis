package de.tum.in.www1.artemis.repository;

import static java.util.stream.Collectors.toMap;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.*;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    Optional<Set<TextBlock>> findAllByCluster(Map<Long, TextCluster> cluster);

    @Query("""
            SELECT DISTINCT tb
            FROM TextSubmission s
            LEFT JOIN FETCH TextBlock tb ON s.id = tb.submission.id
            LEFT JOIN FETCH tb.cluster tc
            LEFT JOIN FETCH tc.blocks tball
            WHERE s.id = :#{#submissionId} AND tc.exercise.id = :#{#exerciseId}
            """)
    Set<TextBlock> findAllWithEagerClusterBySubmissionAndExerciseIds(@Param("submissionId") Long submissionId, @Param("exerciseId") Long exerciseId);

    Set<TextBlock> findAllBySubmissionId(Long id);

    @EntityGraph(type = LOAD, attributePaths = { "submission" })
    Set<TextBlock> findAllBySubmissionIdIn(Set<Long> submissionIdList);

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
     * @param submissionId the id of the Submission
     * @return the number of other TextBlock's in the same cluster as the block with given `id`
     */
    @Query("""
            SELECT tb.id as blockId, COUNT(DISTINCT tball.id) as numberOfOtherBlocks
            FROM TextSubmission s
            LEFT JOIN TextBlock tb ON s.id = tb.submission.id
            LEFT JOIN tb.cluster tc
            LEFT JOIN tc.blocks tball
            WHERE s.id = :#{#submissionId} AND tball.id <> tb.id AND tc.exercise.id = :#{#exerciseId}
            GROUP BY tb.id
            """)
    List<TextBlockCount> countOtherBlocksInSameClusterForSubmissionId(@Param("submissionId") Long submissionId, @Param("exerciseId") Long exerciseId);

    /**
     * This function calls query `countOtherBlocksInSameClusterForSubmissionId` and converts the result into a Map
     * so that it's values will be easily accessed through key value pairs
     * @param submissionId the `id` of the Submission
     * @return a Map data type representing key value pairs where the key is the TextBlock id
     * and the value is the number of other blocks in the same cluster for that TextBlock.
     */
    default Map<String, Integer> countOtherBlocksInClusterBySubmissionId(Long submissionId, Long exerciseId) {
        return countOtherBlocksInSameClusterForSubmissionId(submissionId, exerciseId).stream()
                .collect(toMap(TextBlockCount::getBlockId, count -> count.getNumberOfOtherBlocks().intValue()));
    }

    /**
     * This function finds all text blocks based on the knowledge id
     * @param knowledgeId Id of the knowledge
     * @return Set of all texts blocks have the same knowledge ID
     */
    List<TextBlock> findAllByKnowledgeId(Long knowledgeId);
}
