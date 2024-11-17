package de.tum.cit.aet.artemis.lecture.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;

/**
 * Spring Data JPA repository for the Attachment Unit entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface AttachmentUnitRepository extends ArtemisJpaRepository<AttachmentUnit, Long> {

    @Query("""
            SELECT lectureUnit
            FROM Lecture lecture
                LEFT JOIN lecture.lectureUnits lectureUnit
                LEFT JOIN FETCH lectureUnit.attachment attachment
            WHERE lecture.id = :lectureId
                AND TYPE (lectureUnit) = AttachmentUnit
                AND attachment.attachmentType = :attachmentType
            ORDER BY INDEX(lectureUnit)
            """)
    // INDEX() is used to retrieve the order saved by @OrderColumn, see https://en.wikibooks.org/wiki/Java_Persistence/JPQL#Special_Operators
    List<AttachmentUnit> findAllByLectureIdAndAttachmentType(@Param("lectureId") long lectureId, @Param("attachmentType") AttachmentType attachmentType);

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
    default List<AttachmentUnit> findAllByLectureIdAndAttachmentTypeElseThrow(Long lectureId, AttachmentType attachmentType) throws EntityNotFoundException {
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
                LEFT JOIN FETCH attachmentUnit.competencyLinks cl
                LEFT JOIN FETCH cl.competency
            WHERE attachmentUnit.id = :attachmentUnitId
            """)
    AttachmentUnit findOneWithSlidesAndCompetencies(@Param("attachmentUnitId") long attachmentUnitId);
}
