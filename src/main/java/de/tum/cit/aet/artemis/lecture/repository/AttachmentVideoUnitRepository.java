package de.tum.cit.aet.artemis.lecture.repository;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
            LEFT JOIN LectureUnitProcessingState ps ON ps.lectureUnit.id = avu.id
            WHERE ps.id IS NULL
                AND (c.startDate <= :now OR c.startDate IS NULL)
                AND (c.endDate >= :now OR c.endDate IS NULL)
                AND c.testCourse = FALSE
                AND l.isTutorialLecture = FALSE
                AND (
                    (avu.videoSource IS NOT NULL AND avu.videoSource <> '')
                    OR
                    (avu.attachment IS NOT NULL AND LOWER(avu.attachment.link) LIKE '%.pdf')
                )
            ORDER BY avu.id
            """)
    List<AttachmentVideoUnit> findUnprocessedUnitsFromActiveCourses(@Param("now") ZonedDateTime now, Pageable pageable);

    /**
     * Finds active attachment video units for which no Iris synchronization state exists yet.
     * This supports a bounded rollout backfill for units created before retryable synchronization
     * was introduced.
     *
     * @param now      the current time for determining active courses
     * @param pageable pagination to limit results
     * @return attachment video units without an Iris synchronization state
     */
    @Query("""
            SELECT avu FROM AttachmentVideoUnit avu
            JOIN avu.lecture l
            JOIN l.course c
            LEFT JOIN IrisLectureUnitSyncState syncState ON syncState.lectureUnitId = avu.id AND syncState.visibilityHash IS NOT NULL
            WHERE syncState.id IS NULL
                AND (c.startDate <= :now OR c.startDate IS NULL)
                AND (c.endDate >= :now OR c.endDate IS NULL)
                AND c.testCourse = FALSE
                AND l.isTutorialLecture = FALSE
                AND (
                    (avu.videoSource IS NOT NULL AND avu.videoSource <> '')
                    OR
                    (avu.attachment IS NOT NULL AND LOWER(avu.attachment.link) LIKE '%.pdf')
                )
            ORDER BY avu.id
            """)
    List<AttachmentVideoUnit> findUnitsMissingIrisSyncStateFromActiveCourses(@Param("now") ZonedDateTime now, Pageable pageable);

    /**
     * Find the next course ids that hold attachment video units, ordered by id, starting after the given cursor.
     * Drives the ingestion reconciler's round-robin walk over all courses, including inactive and archived ones.
     *
     * @param courseId the course id to continue after (exclusive); pass 0 to start from the beginning
     * @param pageable pagination to limit the number of courses per walk
     * @return the next course ids after the cursor
     */
    @Query("""
            SELECT DISTINCT c.id FROM AttachmentVideoUnit avu
            JOIN avu.lecture l
            JOIN l.course c
            WHERE c.id > :courseId
                AND c.testCourse = FALSE
                AND l.isTutorialLecture = FALSE
            ORDER BY c.id
            """)
    List<Long> findCourseIdsWithAttachmentVideoUnitsAfter(@Param("courseId") long courseId, Pageable pageable);

    /**
     * Find every attachment video unit of a course with its attachment, lecture, and course fetched.
     * Used by the ingestion reconciler, which computes content fingerprints (attachment link, video source)
     * for all units of the walked course.
     *
     * @param courseId the ID of the course
     * @return all attachment video units of the course
     */
    @Query("""
            SELECT avu FROM AttachmentVideoUnit avu
                LEFT JOIN FETCH avu.attachment
                JOIN FETCH avu.lecture l
                JOIN FETCH l.course c
            WHERE c.id = :courseId
                AND l.isTutorialLecture = FALSE
            ORDER BY avu.id
            """)
    List<AttachmentVideoUnit> findAllWithAttachmentByCourseId(@Param("courseId") long courseId);

    /**
     * From the given ids, return those that still correspond to an existing attachment video unit.
     * Used by the ingestion reconciler to batch its orphan check into a single query instead of one
     * {@code existsById} per census row.
     *
     * @param ids candidate lecture unit ids
     * @return the subset of ids that exist as attachment video units
     */
    @Query("""
            SELECT avu.id FROM AttachmentVideoUnit avu
            WHERE avu.id IN :ids
            """)
    Set<Long> findExistingIds(@Param("ids") Collection<Long> ids);
}
