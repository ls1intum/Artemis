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
     * Finds the attachments of the given lecture whose file still lies under {@code uploads/attachments/lecture/{lectureId}}.
     * <p>
     * These are the attachments the migration in {@code 20260905235721_changelog.xml} turned into attachment video units without moving their files, which is why
     * {@code FileResource} keeps serving them under the lecture path that markdown written years ago points at. Naming a lecture is what makes an attachment one of them: the
     * lecture is the directory its file is in, so an attachment whose file was later replaced through the unit editor drops out of this set because that write clears the
     * lecture, and one that was uploaded into a unit never named a lecture to begin with. Nothing here reads the shape of the stored value.
     * <p>
     * The lecture and its course are fetched because the caller resolves the course from them for its authorization check.
     *
     * @param lectureId the lecture to look up
     * @return the attachments of that lecture whose file lies under the lecture attachment directory
     */
    @Query("""
            SELECT attachment
            FROM Attachment attachment
                JOIN FETCH attachment.lecture lecture
                JOIN FETCH lecture.course
            WHERE lecture.id = :lectureId
            """)
    List<Attachment> findAllStoredUnderLecturePath(@Param("lectureId") Long lectureId);

}
