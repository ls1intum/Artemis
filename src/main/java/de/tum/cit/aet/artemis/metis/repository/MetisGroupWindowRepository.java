package de.tum.cit.aet.artemis.metis.repository;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.domain.MetisGroupWindow;

/**
 * Spring Data JPA repository for the {@link MetisGroupWindow} entity, the per-tutorial-group time windows that override
 * the default window of an exercise's Metis configuration.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Repository
public interface MetisGroupWindowRepository extends ArtemisJpaRepository<MetisGroupWindow, Long> {

    /**
     * All tutorial-group windows configured for one exercise.
     *
     * @param exerciseId the exercise whose configuration the windows belong to
     * @return the windows, empty when every group uses the default window of the configuration
     */
    List<MetisGroupWindow> findAllByExerciseId(long exerciseId);
}
