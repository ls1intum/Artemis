package de.tum.in.www1.artemis.repository;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture.VideoUnit;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Video Unit entity.
 */
@Repository
public interface VideoUnitRepository extends JpaRepository<VideoUnit, Long> {

    @NotNull
    default VideoUnit findByIdElseThrow(Long videoUnitId) throws EntityNotFoundException {
        return findById(videoUnitId).orElseThrow(() -> new EntityNotFoundException("VideoUnit", videoUnitId));
    }

}
