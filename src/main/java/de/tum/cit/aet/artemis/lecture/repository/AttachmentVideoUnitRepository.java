package de.tum.cit.aet.artemis.lecture.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;

/**
 * Spring Data JPA repository for the Attachment Unit entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface AttachmentVideoUnitRepository extends ArtemisJpaRepository<AttachmentVideoUnit, Long> {

    @Query("""
            SELECT lectureUnit
            FROM Lecture lecture
                LEFT JOIN lecture.lectureUnits lectureUnit
                LEFT JOIN FETCH lectureUnit.attachment attachment
            WHERE lecture.id = :lectureId
                AND TYPE (lectureUnit) = AttachmentVideoUnit
                AND attachment.attachmentType = :attachmentType
            ORDER BY lectureUnit.lectureUnitOrder
            """)
    List<AttachmentVideoUnit> findAllByLectureIdAndAttachmentType(@Param("lectureId") long lectureId, @Param("attachmentType") AttachmentType attachmentType);

    /**
     * Find all attachment video units by lecture id and attachment type or throw if ist is empty.
     * The list is sorted according to the order of units in the lecture.
     *
     * @param lectureId      the id of the lecture
     * @param attachmentType the attachment type
     * @return the list of all attachment video units with the given lecture id and attachment type
     * @throws EntityNotFoundException if no results are found
     */
    @NonNull
    default List<AttachmentVideoUnit> findAllByLectureIdAndAttachmentTypeElseThrow(Long lectureId, AttachmentType attachmentType) throws EntityNotFoundException {
        List<AttachmentVideoUnit> attachmentVideoUnits = findAllByLectureIdAndAttachmentType(lectureId, attachmentType);
        if (attachmentVideoUnits.isEmpty()) {
            throw new EntityNotFoundException("AttachmentVideoUnit");
        }
        return attachmentVideoUnits;
    }

    @Query("""
            SELECT attachmentVideoUnit
            FROM AttachmentVideoUnit attachmentVideoUnit
                LEFT JOIN FETCH attachmentVideoUnit.slides slides
                LEFT JOIN FETCH attachmentVideoUnit.competencyLinks cl
                LEFT JOIN FETCH cl.competency
            WHERE attachmentVideoUnit.id = :attachmentVideoUnitId
            """)
    Optional<AttachmentVideoUnit> findWithSlidesAndCompetenciesById(@Param("attachmentVideoUnitId") long attachmentVideoUnitId);

    default AttachmentVideoUnit findWithSlidesAndCompetenciesByIdElseThrow(long attachmentVideoUnitId) {
        return getValueElseThrow(findWithSlidesAndCompetenciesById(attachmentVideoUnitId), attachmentVideoUnitId);
    }

    /**
     * Find AttachmentVideoUnits from active, non-test courses that don't have a processing state yet.
     * Used by the backfill scheduler to process legacy units that existed before the processing pipeline was deployed.
     *
     * @param now      the current time for determining active courses
     * @param pageable pagination to limit results
     * @return list of unprocessed AttachmentVideoUnits
     */
    @Query("""
            SELECT avu FROM AttachmentVideoUnit avu
            JOIN avu.lecture l
            JOIN l.course c
            LEFT JOIN LectureUnitProcessingState ps ON ps.lectureUnit.id = avu.id
            WHERE ps.id IS NULL
                AND (c.startDate <= :now OR c.startDate IS NULL)
                AND (c.endDate >= :now OR c.endDate IS NULL)
                AND c.testCourse = FALSE
                AND (l.tutorialLecture IS NULL OR l.tutorialLecture = FALSE)
                AND (avu.videoSource IS NOT NULL OR avu.attachment IS NOT NULL)
            ORDER BY avu.id
            """)
    List<AttachmentVideoUnit> findUnprocessedUnitsFromActiveCourses(@Param("now") ZonedDateTime now, Pageable pageable);
}
