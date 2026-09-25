package de.tum.cit.aet.artemis.lecture.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;

/**
 * Spring Data JPA repository for the Attachment Unit entity.
 */
@Conditional(LectureEnabled.class)
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

    @Query("""
            SELECT attachmentVideoUnit
            FROM AttachmentVideoUnit attachmentVideoUnit
                LEFT JOIN FETCH attachmentVideoUnit.attachment
                JOIN FETCH attachmentVideoUnit.lecture lecture
                JOIN FETCH lecture.course
            WHERE attachmentVideoUnit.id = :attachmentVideoUnitId
            """)
    Optional<AttachmentVideoUnit> findWithLectureAndCourseAndAttachmentById(@Param("attachmentVideoUnitId") long attachmentVideoUnitId);

    @Query("""
            SELECT attachmentVideoUnit
            FROM AttachmentVideoUnit attachmentVideoUnit
                LEFT JOIN FETCH attachmentVideoUnit.attachment
            WHERE attachmentVideoUnit.id = :attachmentVideoUnitId
            """)
    Optional<AttachmentVideoUnit> findWithAttachmentById(@Param("attachmentVideoUnitId") long attachmentVideoUnitId);

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
            LEFT JOIN avu.attachment attachment
            LEFT JOIN LectureUnitProcessingState ps ON ps.lectureUnit.id = avu.id
            WHERE ps.id IS NULL
                AND (c.startDate <= :now OR c.startDate IS NULL)
                AND (c.endDate >= :now OR c.endDate IS NULL)
                AND c.testCourse = FALSE
                AND l.isTutorialLecture = FALSE
                AND (
                    (avu.videoSource IS NOT NULL AND avu.videoSource <> '')
                    OR
                    (attachment IS NOT NULL AND LOWER(attachment.link) LIKE '%.pdf')
                )
            ORDER BY avu.id
            """)
    List<AttachmentVideoUnit> findUnprocessedUnitsFromActiveCourses(@Param("now") ZonedDateTime now, Pageable pageable);

    /**
     * Finds active attachment video units for which no Iris synchronization state exists yet.
     * This supports a bounded rollout backfill for units created before retryable synchronization
     * was introduced.
     *
     * <p>
     * The attachment is joined explicitly rather than navigated to as {@code avu.attachment.link}, which is an implicit
     * inner join: it dropped every unit without an attachment before the surrounding {@code OR} was evaluated, so a unit
     * carrying only a video was never backfilled.
     *
     * <p>
     * Only units whose content processing finished are considered. Pyris answers a synchronization for anything else
     * with "lecture unit has not been ingested", so creating a state for one manufactures work that can only fail:
     * before this condition existed, every eligible unit that had never been ingested was pushed once an hour for as
     * long as its course stayed active. Reaching {@code DONE} is a good indication rather than a guarantee, since a
     * unit whose course has Iris disabled also completes without being ingested; such a unit reports its
     * synchronization as skipped instead of failing.
     *
     * @param now      the current time for determining active courses
     * @param pageable pagination to limit results
     * @return attachment video units without an Iris synchronization state
     */
    @Query("""
            SELECT avu FROM AttachmentVideoUnit avu
            JOIN avu.lecture l
            JOIN l.course c
            LEFT JOIN avu.attachment attachment
            LEFT JOIN IrisLectureUnitSyncState syncState ON syncState.lectureUnitId = avu.id AND syncState.visibilityHash IS NOT NULL
            WHERE syncState.id IS NULL
                AND EXISTS (
                    SELECT 1 FROM LectureUnitProcessingState processingState
                    WHERE processingState.lectureUnit.id = avu.id
                        AND processingState.phase = de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase.DONE
                )
                AND (c.startDate <= :now OR c.startDate IS NULL)
                AND (c.endDate >= :now OR c.endDate IS NULL)
                AND c.testCourse = FALSE
                AND l.isTutorialLecture = FALSE
                AND (
                    (avu.videoSource IS NOT NULL AND avu.videoSource <> '')
                    OR
                    (attachment IS NOT NULL AND LOWER(attachment.link) LIKE '%.pdf')
                )
            ORDER BY avu.id
            """)
    List<AttachmentVideoUnit> findUnitsMissingIrisSyncStateFromActiveCourses(@Param("now") ZonedDateTime now, Pageable pageable);
}
