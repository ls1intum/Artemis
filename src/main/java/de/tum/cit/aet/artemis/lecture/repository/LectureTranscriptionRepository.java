package de.tum.cit.aet.artemis.lecture.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;

/**
 * Spring Data JPA repository for the Transcription of a lecture video entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface LectureTranscriptionRepository extends ArtemisJpaRepository<LectureTranscription, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "segments" })
    Optional<LectureTranscription> findOneWithTranscriptionSegmentsById(Long id);

    @EntityGraph(type = LOAD, attributePaths = { "segments" })
    Optional<LectureTranscription> findWithTranscriptionSegmentsByLectureUnitId(Long lectureUnitId);

    @EntityGraph(type = LOAD, attributePaths = {})
    Set<LectureTranscription> findAllByLectureUnitId(Long lectureUnitId);

}
