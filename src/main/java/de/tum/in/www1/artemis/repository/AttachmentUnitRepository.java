package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;

/**
 * Spring Data JPA repository for the Attachment Unit entity.
 */
@Repository
public interface AttachmentUnitRepository extends JpaRepository<AttachmentUnit, Long> {

    @Query("""
            SELECT a
            FROM AttachmentUnit a
            WHERE a.lecture.id = :lectureId
                AND a.attachment.attachmentType = :attachmentType
            """)
    Set<AttachmentUnit> findAllByLectureIdAndAttachmentType(@Param("lectureId") Long lectureId, @Param("attachmentType") AttachmentType attachmentType);

    /**
     * Find all attachment units by lecture id and attachment type or throw if ist is empty.
     *
     * @param lectureId the id of the lecture
     * @param attachmentType the attachment type
     * @return the list of all attachment units with the given lecture id and attachment type
     * @throws EntityNotFoundException if no results are found
     */
    default Set<AttachmentUnit> findAllByLectureIdAndAttachmentTypeElseThrow(Long lectureId, AttachmentType attachmentType) throws EntityNotFoundException {
        Set<AttachmentUnit> attachmentUnits = findAllByLectureIdAndAttachmentType(lectureId, attachmentType);
        if (attachmentUnits.isEmpty()) {
            throw new EntityNotFoundException("AttachmentUnit");
        }
        return attachmentUnits;
    }

    @NotNull
    default AttachmentUnit findByIdElseThrow(Long attachmentUnitId) throws EntityNotFoundException {
        return findById(attachmentUnitId).orElseThrow(() -> new EntityNotFoundException("AttachmentUnit", attachmentUnitId));
    }

}
