package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Collections;
import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the Attachment entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    @Query("""
            SELECT a
            FROM Attachment a
            WHERE a.lecture.id = :lectureId
            """)
    List<Attachment> findAllByLectureId(@Param("lectureId") Long lectureId);

    default Attachment findByIdOrElseThrow(Long attachmentId) {
        return findById(attachmentId).orElseThrow(() -> new EntityNotFoundException("Attachment", attachmentId));
    }

    @Query("""
            SELECT a.attachmentUnit
            FROM Attachment a
            WHERE a.uploadDate = CURRENT_DATE
            """)
    List<AttachmentUnit> findAllWithUploadDateTodayAndSlides(AttachmentType attachmentType);

    @NotNull
    default List<AttachmentUnit> findAllUpdatedAttachmentUnits() {
        List<AttachmentUnit> attachmentUnits = findAllWithUploadDateTodayAndSlides(AttachmentType.FILE);
        if (attachmentUnits == null) {
            return Collections.emptyList();
        }
        return attachmentUnits;
    }
}
