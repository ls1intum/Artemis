package de.tum.cit.aet.artemis.lecture.repository;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
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

    /**
     * Finds the attachments that name the given lecture.
     * <p>
     * Only an attachment whose file lies under the lecture attachment path carries a lecture id, so this is the set of
     * files still stored under {@code uploads/attachments/lecture/{lectureId}}. Every one of them belongs to an
     * attachment video unit since the migration in {@code 20260905235721_changelog.xml} created one per attachment that
     * used to hang off a lecture directly. The files themselves stayed where they were, which is why
     * {@code FileResource} keeps serving them by lecture id.
     *
     * @param lectureId the lecture to look up
     * @return the attachments that name that lecture
     */
    @Query("""
            SELECT attachment
            FROM Attachment attachment
            WHERE attachment.lecture.id = :lectureId
            """)
    List<Attachment> findAllByLectureId(@Param("lectureId") Long lectureId);

    default Attachment findByIdOrElseThrow(Long attachmentId) {
        return getValueElseThrow(findById(attachmentId), attachmentId);
    }

}
