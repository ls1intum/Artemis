package de.tum.cit.aet.artemis.lecture.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;

/**
 * Spring Data repository for the Attachment entity.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface AttachmentRepository extends ArtemisJpaRepository<Attachment, Long> {

    @Query("""
            SELECT a
            FROM Attachment a
            WHERE a.lecture.id = :lectureId
            """)
    List<Attachment> findAllByLectureId(@Param("lectureId") Long lectureId);

    @Query("""
            SELECT DISTINCT a
            FROM Attachment a
                JOIN a.attachmentVideoUnit unit
                JOIN unit.slides slide
            WHERE a.studentVersion IS NULL
                AND slide.hidden IS NOT NULL
                AND a.id > :afterId
            ORDER BY a.id
            """)
    List<Attachment> findAllRequiringStudentVersionRegeneration(@Param("afterId") long afterId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Attachment a WHERE a.id = :attachmentId")
    Optional<Attachment> findByIdWithPessimisticWriteLock(@Param("attachmentId") long attachmentId);

    default Attachment findByIdOrElseThrow(Long attachmentId) {
        return getValueElseThrow(findById(attachmentId), attachmentId);
    }

}
