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
 * <p>
 * Every programming exercise owns exactly one row, also while this module is disabled and this repository does not exist:
 * the row is inserted by the exercise module when the exercise is stored ({@code ExerciseDefaultConfigurationsRepository}),
 * and the next update of the exercise adds it if a creation that failed half-way left it out. Settings are changed in that
 * row in place, never by inserting or deleting one, and switching Metis on or off only changes its enabled flag.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Repository
public interface MetisExerciseConfigRepository extends ArtemisJpaRepository<MetisExerciseConfig, Long> {
}
