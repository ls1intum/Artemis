package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the GradingInstruction entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface GradingInstructionRepository extends ArtemisJpaRepository<GradingInstruction, Long> {

    /**
     * Counts the referenced instructions that belong to the assessed exercise.
     *
     * @param ids        the distinct instruction ids supplied with the feedback
     * @param exerciseId the exercise being assessed
     * @return the number of matching instructions
     */
    long countByIdInAndGradingCriterionExerciseId(Set<Long> ids, long exerciseId);
}
