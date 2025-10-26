package de.tum.cit.aet.artemis.lecture.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.dto.calendar.LectureCalendarEventDTO;
import de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

/**
 * Spring Data repository for the Lecture entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface LectureRepository extends ArtemisJpaRepository<Lecture, Long> {

    @Query("""
            SELECT lecture
            FROM Lecture lecture
            WHERE lecture.course.id = :courseId AND NOT lecture.isTutorialLecture
            """)
    Set<Lecture> findAllNonTutorialLecturesByCourseId(@Param("courseId") Long courseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.calendar.LectureCalendarEventDTO(
                lecture.id,
                lecture.title,
                lecture.visibleDate,
                lecture.startDate,
                lecture.endDate
            )
            FROM Lecture lecture
            WHERE lecture.course.id = :courseId AND (lecture.startDate IS NOT NULL OR lecture.endDate IS NOT NULL) AND NOT lecture.isTutorialLecture
            """)
    Set<LectureCalendarEventDTO> getLectureCalendarEventDTOsForCourseId(@Param("courseId") long courseId);

    @Query("""
            SELECT lecture
            FROM Lecture lecture
            LEFT JOIN FETCH lecture.lectureUnits
            WHERE lecture.course.id = :courseId
                AND (lecture.visibleDate IS NULL OR lecture.visibleDate <= :now) AND NOT lecture.isTutorialLecture
            """)
    Set<Lecture> findAllVisibleNonTutorialLecturesByCourseIdWithEagerLectureUnits(@Param("courseId") long courseId, @Param("now") ZonedDateTime now);

    @Query("""
            SELECT lecture
            FROM Lecture lecture
                LEFT JOIN FETCH lecture.attachments
            WHERE lecture.course.id = :courseId AND NOT lecture.isTutorialLecture
            """)
    Set<Lecture> findAllNonTutorialLecturesByCourseIdWithAttachments(@Param("courseId") Long courseId);

    @Query("""
            SELECT lecture
            FROM Lecture lecture
                LEFT JOIN FETCH lecture.attachments
                LEFT JOIN FETCH lecture.lectureUnits lu
                LEFT JOIN FETCH lu.attachment
            WHERE lecture.course.id = :courseId
            """)
    Set<Lecture> findAllNonTutorialLecturesByCourseIdWithAttachmentsAndLectureUnits(@Param("courseId") Long courseId);

    // TODO: this query loads too much data, we should reduce the number of left join fetches
    @Query("""
            SELECT lecture
            FROM Lecture lecture
                LEFT JOIN FETCH lecture.lectureUnits lu
                LEFT JOIN FETCH lu.competencyLinks cl
                LEFT JOIN FETCH cl.competency
                LEFT JOIN FETCH lu.exercise e
                LEFT JOIN FETCH e.competencyLinks ecl
                LEFT JOIN FETCH ecl.competency
            WHERE lecture.id = :lectureId
            """)
    Optional<Lecture> findByIdWithLectureUnitsAndCompetencies(@Param("lectureId") Long lectureId);

    @Query("""
            SELECT lecture
            FROM Lecture lecture
                LEFT JOIN FETCH lecture.lectureUnits
            WHERE lecture.id = :lectureId
            """)
    Optional<Lecture> findByIdWithLectureUnits(@Param("lectureId") Long lectureId);

    @Query("""
            SELECT lecture
            FROM Lecture lecture
                LEFT JOIN FETCH lecture.lectureUnits
                LEFT JOIN FETCH lecture.attachments
            WHERE lecture.id = :lectureId
            """)
    Optional<Lecture> findByIdWithLectureUnitsAndAttachments(@Param("lectureId") Long lectureId);

    @Query("""
            SELECT lecture
            FROM Lecture lecture
                LEFT JOIN FETCH lecture.lectureUnits
            WHERE lecture.title = :title AND lecture.course.id = :courseId AND NOT lecture.isTutorialLecture
            """)
    Set<Lecture> findAllNonTutorialLecturesByTitleAndCourseIdWithLectureUnits(@Param("title") String title, @Param("courseId") long courseId);

    /**
     * Finds a lecture by its title and course id and throws a NoUniqueQueryException if multiple lectures are found.
     *
     * @param title    the title of the lecture
     * @param courseId the id of the course
     * @return the lecture with the given title and course id
     * @throws NoUniqueQueryException if multiple lectures are found with the same title
     */
    default Optional<Lecture> findUniqueNonTutorialLecturesByTitleAndCourseIdWithLectureUnitsElseThrow(String title, long courseId) throws NoUniqueQueryException {
        Set<Lecture> allLectures = findAllNonTutorialLecturesByTitleAndCourseIdWithLectureUnits(title, courseId);
        if (allLectures.size() > 1) {
            throw new NoUniqueQueryException("Found multiple lectures with title " + title + " in course with id " + courseId);
        }
        return allLectures.stream().findFirst();
    }

    @Query("""
            SELECT lecture
            FROM Lecture lecture
            WHERE NOT lecture.isTutorialLecture AND (LOWER(lecture.title) LIKE LOWER(CONCAT('%', :partialTitle, '%')) OR LOWER(lecture.course.title) LIKE LOWER(CONCAT('%', :partialCourseTitle, '%')))
            """)
    Page<Lecture> findNonTutorialLecturesByTitleOrCourseTitle(@Param("partialTitle") String partialTitle, @Param("partialCourseTitle") String partialCourseTitle,
            Pageable pageable);

    /**
     * Query which fetches all lectures for which the user is editor or instructor in the course and
     * matching the search criteria.
     *
     * @param partialTitle       lecture title search term
     * @param partialCourseTitle course title search term
     * @param groups             user groups
     * @param pageable           Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT lecture
            FROM Lecture lecture
            WHERE (lecture.course.instructorGroupName IN :groups OR lecture.course.editorGroupName IN :groups)
                AND (lecture.title LIKE %:partialTitle% OR lecture.course.title LIKE %:partialCourseTitle%) AND NOT lecture.isTutorialLecture
            """)
    Page<Lecture> findNonTutorialLecturesByTitleInLectureOrCourseAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle,
            @Param("partialCourseTitle") String partialCourseTitle, @Param("groups") Set<String> groups, Pageable pageable);

    /**
     * Returns the title of the lecture with the given id.
     *
     * @param lectureId the id of the lecture
     * @return the name/title of the lecture or null if the lecture does not exist
     */
    @Query("""
            SELECT lecture.title
            FROM Lecture lecture
            WHERE lecture.id = :lectureId
            """)
    @Cacheable(cacheNames = "lectureTitle", key = "#lectureId", unless = "#result == null")
    String getLectureTitle(@Param("lectureId") Long lectureId);

    @NotNull
    default Lecture findByIdWithLectureUnitsElseThrow(Long lectureId) {
        return getValueElseThrow(findByIdWithLectureUnits(lectureId), lectureId);
    }

    @NotNull
    default Lecture findByIdWithLectureUnitsAndCompetenciesElseThrow(Long lectureId) {
        return getValueElseThrow(findByIdWithLectureUnitsAndCompetencies(lectureId), lectureId);
    }

    @NotNull
    default Lecture findByIdWithLectureUnitsAndAttachmentsElseThrow(Long lectureId) {
        return getValueElseThrow(findByIdWithLectureUnitsAndAttachments(lectureId), lectureId);
    }

    @NotNull
    default Lecture findByIdWithLectureUnitsWithCompetencyLinksAndAttachmentsElseThrow(Long lectureId) {
        return getValueElseThrow(findByIdWithLectureUnitsWithCompetencyLinksAndAttachments(lectureId), lectureId);
    }

    @Query("""
            SELECT lecture
            FROM Lecture lecture
                LEFT JOIN FETCH lecture.lectureUnits lu
                LEFT JOIN FETCH lecture.attachments
                LEFT JOIN FETCH lu.competencyLinks
            WHERE lecture.id = :lectureId
            """)
    Optional<Lecture> findByIdWithLectureUnitsWithCompetencyLinksAndAttachments(@Param("lectureId") Long lectureId);

    long countByCourse_Id(long courseId);
}
