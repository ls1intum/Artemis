package de.tum.cit.aet.artemis.metis.repository;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;

/**
 * Spring Data JPA repository for the {@link MetisQuestion} entity, the main and follow-up questions of a Metis check
 * with their answers and ratings.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Repository
public interface MetisQuestionRepository extends ArtemisJpaRepository<MetisQuestion, Long> {

    /**
     * All questions of one check, main questions and follow-ups alike, in display order.
     *
     * @param checkId the check the questions belong to
     * @return the questions ordered by {@code questionOrder}, empty while none have been generated
     */
    List<MetisQuestion> findAllByCheckIdOrderByQuestionOrderAsc(long checkId);
}
