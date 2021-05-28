package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.*;
import java.util.stream.Collectors;

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

    Optional<Set<TextBlock>> findAllByCluster(TextCluster textCluster);

    @EntityGraph(type = LOAD, attributePaths = { "cluster" })
    Set<TextBlock> findAllWithEagerClusterBySubmissionId(Long id);

    Set<TextBlock> findAllBySubmissionId(Long id);

    @EntityGraph(type = LOAD, attributePaths = { "submission" })
    Set<TextBlock> findAllBySubmissionIdIn(Set<Long> submissionIdList);

    void deleteAllBySubmission_Id(Long submissionId);

    /**
     * For the given Submission `id` returns a list of raw object array representing two columns.
     * First index/column corresponds to the TextBlock `id` while the second one corresponds to
     * the number of other blocks in the same cluster as given block with id = `id`.
     * For all TextBlock's of the Submission with the given `id`
     * finds their respective cluster and retrieves the number of other blocks in the same cluster
     * @param id the id of the submission
     * @return the number of other blocks in the same cluster as the block with given `id`
     */
    @Query("""
            SELECT tb.id, COUNT(DISTINCT tball.id)
            FROM TextSubmission s
            LEFT JOIN TextBlock tb ON s.id = tb.submission.id
            LEFT JOIN TextCluster tc ON tb.cluster.id = tc.id
            LEFT JOIN TextBlock tball ON tc.id = tball.cluster.id AND tball.id <> tb.id
            WHERE s.id = :#{#id}
            GROUP BY tb.id""")
    List<Object[]> getNumberOfOtherBlocksInClusterForSubmission(@Param("id") Long id);

    /**
     * Takes a List of Object[] type and converts it into a Map<String, Integer>
     * @param input the id of the submission
     * @return given input in Map<String, Integer> data structure
     */
    default Map<String, Integer> convertListOfObjectArrayToMap(List<Object[]> input) {
        return input.stream().collect(Collectors.toMap(column -> (String) column[0], column -> ((Long) column[1]).intValue()));
    }
}
