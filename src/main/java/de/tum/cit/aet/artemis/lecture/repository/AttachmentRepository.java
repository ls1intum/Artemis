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
     * An attachment carries a lecture id when its file lies under {@code uploads/attachments/lecture/{lectureId}}, so
     * this is the set of files still stored there for that lecture. The migration in
     * {@code 20260905235721_changelog.xml} gave each of them an attachment video unit and left the files where they
     * were, which is why {@code FileResource} keeps serving them by lecture id. A row without a unit means the
     * migration never saw it: importing a lecture on a node of the previous version copies these attachments into the
     * new lecture, so a lecture imported during a rolling deployment can hold one until a later changelog converts it.
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
