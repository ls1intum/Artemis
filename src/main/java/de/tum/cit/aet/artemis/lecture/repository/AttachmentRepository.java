package de.tum.cit.aet.artemis.lecture.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT attachment FROM Attachment attachment WHERE attachment.id = :attachmentId")
    Optional<Attachment> findByIdForUpdate(@Param("attachmentId") Long attachmentId);

    default Attachment findByIdOrElseThrow(Long attachmentId) {
        return getValueElseThrow(findById(attachmentId), attachmentId);
    }

    default Attachment findByIdForUpdateElseThrow(Long attachmentId) {
        return getValueElseThrow(findByIdForUpdate(attachmentId), attachmentId);
    }
}
