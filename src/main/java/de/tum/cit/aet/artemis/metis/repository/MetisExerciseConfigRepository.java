package de.tum.cit.aet.artemis.metis.repository;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;

/**
 * Spring Data JPA repository for the {@link MetisExerciseConfig} entity. The id of the entity is the exercise id, so the
 * inherited lookup by id is the lookup by exercise and no custom method is needed.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Repository
public interface MetisExerciseConfigRepository extends ArtemisJpaRepository<MetisExerciseConfig, Long> {
}
