package de.tum.cit.aet.artemis.modeling.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.repository.ModelingSubmissionRepository;

@Repository
public interface ModelingSubmissionTestRepository extends ModelingSubmissionRepository {

    @Query("""
            SELECT DISTINCT submission
            FROM ModelingSubmission submission
                LEFT JOIN FETCH submission.results r
                LEFT JOIN FETCH r.assessor
            WHERE submission.id = :submissionId
            """)
    Optional<ModelingSubmission> findByIdWithEagerResult(@Param("submissionId") Long submissionId);

    @EntityGraph(type = LOAD, attributePaths = { "results" })
    Optional<ModelingSubmission> findWithEagerResultById(Long submissionId);
}
