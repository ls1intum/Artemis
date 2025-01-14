package de.tum.cit.aet.artemis.lecture.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.domain.Transcription;

/**
 * Spring Data JPA repository for the Transcription of a lecture video entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface TranscriptionRepository extends ArtemisJpaRepository<Transcription, Long> {

    @Query("""
            SELECT t
            FROM Transcription t
            WHERE t.lecture.id = :lectureId
            """)
    Optional<Transcription> findByLectureId(@Param("lectureId") Long lectureId);

    @Query("""
            SELECT t
            FROM Transcription t
            WHERE t.lecture.id = :lectureId
            """)
    List<Transcription> findAllByLectureId(@Param("lectureId") Long lectureId);

    default Transcription findByIdOrElseThrow(Long id) {
        return getValueElseThrow(findById(id), id);
    }

}
