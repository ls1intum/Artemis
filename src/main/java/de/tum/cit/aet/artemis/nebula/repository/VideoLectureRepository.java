package de.tum.cit.aet.artemis.nebula.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;
import de.tum.cit.aet.artemis.nebula.domain.VideoLecture;

/**
 * Spring Data JPA repository for the VideoLecture entity.
 */
@Conditional(NebulaEnabled.class)
@Profile(PROFILE_CORE)
@Repository
public interface VideoLectureRepository extends ArtemisJpaRepository<VideoLecture, Long> {

    /**
     * Find a video lecture by lecture ID.
     *
     * @param lectureId the ID of the lecture
     * @return an Optional containing the VideoLecture if found
     */
    Optional<VideoLecture> findByLectureId(Long lectureId);

    /**
     * Find a video lecture by video ID.
     *
     * @param videoId the video ID from Nebula service
     * @return an Optional containing the VideoLecture if found
     */
    Optional<VideoLecture> findByVideoId(String videoId);

    /**
     * Delete video lecture by lecture ID.
     *
     * @param lectureId the ID of the lecture
     */
    void deleteByLectureId(Long lectureId);

    /**
     * Check if a lecture has an associated video.
     *
     * @param lectureId the ID of the lecture
     * @return true if the lecture has a video, false otherwise
     */
    boolean existsByLectureId(Long lectureId);
}
