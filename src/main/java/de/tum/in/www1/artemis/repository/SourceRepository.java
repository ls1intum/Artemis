package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.competency.Source;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the {@link Source} entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface SourceRepository extends JpaRepository<Source, Long> {

    @NotNull
    default Source findByIdElseThrow(long sourceId) throws EntityNotFoundException {
        return findById(sourceId).orElseThrow(() -> new EntityNotFoundException("Source", sourceId));
    }
}
