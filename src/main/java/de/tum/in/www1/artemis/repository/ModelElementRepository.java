package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.modeling.ModelElement;

/**
 * Spring Data JPA repository for the ModelElement entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ModelElementRepository extends JpaRepository<ModelElement, Long> {

    @Query("select element from ModelElement element left join fetch element.cluster cluster where element.modelElementId = :#{#modelElementId}")
    ModelElement findByModelElementIdWithCluster(@Param("modelElementId") String elementId);

    List<ModelElement> findByModelElementIdIn(List<String> elementIds);

    /**
     * Interface used to define return type for `countOtherElementsInClusterBySubmissionId`
     */
    interface ModelElementCount {

        String getElementId();

        Long getNumberOfOtherElements();
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
            SELECT element.modelElementId as elementId, COUNT(DISTINCT allElements.modelElementId) as numberOfOtherElements
            FROM ModelingSubmission submission
            LEFT JOIN ModelElement element ON submission.id = element.submission.id
            LEFT JOIN ModelCluster cluster ON element.cluster.id = cluster.id
            LEFT JOIN ModelElement allElements ON cluster.id = allElements.cluster.id AND allElements.modelElementId <> element.modelElementId
            WHERE submission.id = :#{#submissionId}
            GROUP BY element.modelElementId
            """)
    List<ModelElementRepository.ModelElementCount> countOtherElementsInSameClusterForSubmissionId(@Param("submissionId") Long submissionId);
}
