package de.tum.in.www1.artemis.repository;

import java.util.List;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Attachment Unit entity.
 */
@Repository
public interface AttachmentUnitRepository extends JpaRepository<AttachmentUnit, Long> {

    @Query("""
            SELECT attachmentUnit
            FROM Lecture lecture
                LEFT JOIN TREAT(lecture.lectureUnits as AttachmentUnit) attachmentUnit
                LEFT JOIN FETCH attachmentUnit.attachment attachment
            WHERE lecture.id = :lectureId
                AND attachment.attachmentType = :attachmentType
            ORDER BY INDEX(attachmentUnit)
            """)
    // INDEX() is used to retrieve the order saved by @OrderColumn, see https://en.wikibooks.org/wiki/Java_Persistence/JPQL#Special_Operators
    List<AttachmentUnit> findAllByLectureIdAndAttachmentType(@Param("lectureId") Long lectureId, @Param("attachmentType") AttachmentType attachmentType);

    /**
     * Find all attachment units by lecture id and attachment type or throw if ist is empty.
     * The list is sorted according to the order of units in the lecture.
     *
     * @param lectureId      the id of the lecture
     * @param attachmentType the attachment type
     * @return the list of all attachment units with the given lecture id and attachment type
     * @throws EntityNotFoundException if no results are found
     */
    @NotNull
    default List<AttachmentUnit> findAllByLectureIdAndAttachmentTypeElseThrow(@Param("lectureId") Long lectureId, @Param("attachmentType") AttachmentType attachmentType)
            throws EntityNotFoundException {
        List<AttachmentUnit> attachmentUnits = findAllByLectureIdAndAttachmentType(lectureId, attachmentType);
        if (attachmentUnits.isEmpty()) {
            throw new EntityNotFoundException("AttachmentUnit");
        }
        return attachmentUnits;
    }

    @Query("""
            SELECT attachmentUnit
            FROM AttachmentUnit attachmentUnit
                LEFT JOIN FETCH attachmentUnit.slides slides
            WHERE attachmentUnit.id = :attachmentUnitId
            """)
    AttachmentUnit findOneWithSlides(@Param("attachmentUnitId") long attachmentUnitId);

    @NotNull
    default AttachmentUnit findByIdElseThrow(Long attachmentUnitId) throws EntityNotFoundException {
        return findById(attachmentUnitId).orElseThrow(() -> new EntityNotFoundException("AttachmentUnit", attachmentUnitId));
    }

}
