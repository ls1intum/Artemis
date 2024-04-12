package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import jakarta.annotation.Nonnull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture.VideoUnit;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Video Unit entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface VideoUnitRepository extends JpaRepository<VideoUnit, Long> {

    @Nonnull
    default VideoUnit findByIdElseThrow(Long videoUnitId) throws EntityNotFoundException {
        return findById(videoUnitId).orElseThrow(() -> new EntityNotFoundException("VideoUnit", videoUnitId));
    }

}
