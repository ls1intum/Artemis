package de.tum.cit.aet.artemis.exam.repository;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.room.LayoutStrategy;

/**
 * Spring Data JPA repository for the {@link de.tum.cit.aet.artemis.exam.domain.room.LayoutStrategy} entity.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Repository
public interface LayoutStrategyRepository extends ArtemisJpaRepository<LayoutStrategy, Long> {
}
