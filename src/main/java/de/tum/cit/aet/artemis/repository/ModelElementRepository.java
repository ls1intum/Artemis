package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.modeling.ModelElement;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the ModelElement entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ModelElementRepository extends ArtemisJpaRepository<ModelElement, Long> {

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
     *
     * @param submissionId the id of the Submission
     * @return the number of other TextBlock's in the same cluster as the block with given `id`
     */
    @Query("""
            SELECT element.modelElementId AS elementId,
                COUNT(DISTINCT allElements.modelElementId) AS numberOfOtherElements
            FROM ModelingSubmission submission
                LEFT JOIN ModelElement element ON submission.id = element.submission.id
                LEFT JOIN ModelCluster cluster ON element.cluster.id = cluster.id
                LEFT JOIN ModelElement allElements ON cluster.id = allElements.cluster.id
                    AND allElements.modelElementId <> element.modelElementId
            WHERE submission.id = :submissionId
            GROUP BY element.modelElementId
            """)
    List<ModelElementRepository.ModelElementCount> countOtherElementsInSameClusterForSubmissionId(@Param("submissionId") Long submissionId);
}
