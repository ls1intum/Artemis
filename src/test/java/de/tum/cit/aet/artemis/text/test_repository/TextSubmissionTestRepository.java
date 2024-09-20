package de.tum.cit.aet.artemis.text.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextSubmissionRepository;

@Repository
@Primary
public interface TextSubmissionTestRepository extends TextSubmissionRepository {

    /**
     * Gets all TextSubmissions which are submitted and loads all blocks
     *
     * @param exerciseId the ID of the exercise
     * @return Set of Text Submissions
     */
    @EntityGraph(type = LOAD, attributePaths = { "blocks" })
    Set<TextSubmission> findByParticipation_ExerciseIdAndSubmittedIsTrue(long exerciseId);

    @NotNull
    default TextSubmission findByIdWithParticipationExerciseResultAssessorElseThrow(long submissionId) {
        return getValueElseThrow(findWithEagerParticipationExerciseResultAssessorById(submissionId), submissionId);
    }
}
