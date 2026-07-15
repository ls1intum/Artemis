package de.tum.cit.aet.artemis.lecture.repository;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;

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

    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE Attachment attachment
            SET attachment.name = :name,
                attachment.releaseDate = :releaseDate,
                attachment.uploadDate = :uploadDate,
                attachment.attachmentType = :attachmentType,
                attachment.studentVersion = :studentVersion,
                attachment.link = :link,
                attachment.version = CASE WHEN attachment.version IS NULL THEN 1 ELSE attachment.version + 1 END
            WHERE attachment.id = :attachmentId
                AND (attachment.version = :expectedVersion OR (attachment.version IS NULL AND :expectedVersion IS NULL))
            """)
    int replaceFileAndIncrementVersion(@Param("attachmentId") long attachmentId, @Param("expectedVersion") Integer expectedVersion, @Param("name") String name,
            @Param("releaseDate") ZonedDateTime releaseDate, @Param("uploadDate") ZonedDateTime uploadDate, @Param("attachmentType") AttachmentType attachmentType,
            @Param("studentVersion") String studentVersion, @Param("link") String link);

    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE Attachment attachment
            SET attachment.name = :name,
                attachment.releaseDate = :releaseDate,
                attachment.uploadDate = :uploadDate,
                attachment.attachmentType = :attachmentType,
                attachment.studentVersion = :studentVersion
            WHERE attachment.id = :attachmentId
            """)
    int updateMetadata(@Param("attachmentId") long attachmentId, @Param("name") String name, @Param("releaseDate") ZonedDateTime releaseDate,
            @Param("uploadDate") ZonedDateTime uploadDate, @Param("attachmentType") AttachmentType attachmentType, @Param("studentVersion") String studentVersion);

    default Attachment findByIdOrElseThrow(Long attachmentId) {
        return getValueElseThrow(findById(attachmentId), attachmentId);
    }

}
