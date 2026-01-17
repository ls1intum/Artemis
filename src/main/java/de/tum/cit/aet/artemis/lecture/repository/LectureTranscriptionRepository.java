package de.tum.cit.aet.artemis.lecture.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;

/**
 * Spring Data JPA repository for the Transcription of a lecture video entity.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface LectureTranscriptionRepository extends ArtemisJpaRepository<LectureTranscription, Long> {

    Optional<LectureTranscription> findByLectureUnit_Id(Long lectureUnitId);

    List<LectureTranscription> findByTranscriptionStatusAndJobIdIsNotNull(TranscriptionStatus status);

    Optional<LectureTranscription> findByJobId(String jobId);

    /**
     * Find all transcriptions for a lecture.
     *
     * @param lectureId the ID of the lecture
     * @return list of transcriptions for all units in the lecture
     */
    @Query("""
            SELECT t FROM LectureTranscription t
            WHERE t.lectureUnit.lecture.id = :lectureId
            """)
    List<LectureTranscription> findByLectureId(@Param("lectureId") Long lectureId);
}
