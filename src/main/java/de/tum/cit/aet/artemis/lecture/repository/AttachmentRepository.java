package de.tum.cit.aet.artemis.lecture.repository;

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

/**
 * Spring Data repository for the Attachment entity.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface AttachmentRepository extends ArtemisJpaRepository<Attachment, Long> {

    /**
     * Finds the attachments that were attached to the given lecture directly rather than through a lecture unit.
     * <p>
     * The lecture no longer maps them, so this is the only way to reach the files of such an attachment. It is kept
     * so that {@code FileResource} can still serve links to those files, which appear in markdown written before
     * lecture attachments were retired. An attachment of an attachment video unit carries the lecture id as well, but
     * its file lives under a different path, so serving it from here would look for it in the wrong place.
     *
     * @param lectureId the lecture to look up
     * @return the attachments attached to that lecture directly
     */
    @Query("""
            SELECT attachment
            FROM Attachment attachment
            WHERE attachment.lecture.id = :lectureId
                AND attachment.attachmentVideoUnit IS NULL
            """)
    List<Attachment> findAllDirectlyAttachedToLecture(@Param("lectureId") Long lectureId);

    /**
     * Deletes every attachment that points at the given lecture and is not owned by a lecture unit.
     * <p>
     * The lecture no longer maps its attachments, so nothing cascades to these rows any more, while
     * {@code attachment.lecture_id} is ON DELETE RESTRICT: leaving them behind makes deleting the lecture fail.
     * Attachments that belong to an attachment video unit also carry the lecture id and are deleted with their unit,
     * so they are excluded here.
     *
     * @param lectureId the lecture whose directly attached files should be removed
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM Attachment attachment
            WHERE attachment.lecture.id = :lectureId
                AND attachment.attachmentVideoUnit IS NULL
            """)
    void deleteAllDirectlyAttachedToLecture(@Param("lectureId") Long lectureId);

    default Attachment findByIdOrElseThrow(Long attachmentId) {
        return getValueElseThrow(findById(attachmentId), attachmentId);
    }

}
