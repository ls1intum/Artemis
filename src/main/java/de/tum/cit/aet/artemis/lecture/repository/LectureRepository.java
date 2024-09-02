package de.tum.cit.aet.artemis.lecture.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotNull;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.dto.CourseContentCount;
import de.tum.in.www1.artemis.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;

/**
 * Spring Data repository for the Lecture entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface LectureRepository extends ArtemisJpaRepository<Lecture, Long> {

    @Query("""
            SELECT lecture
            FROM Lecture lecture
                LEFT JOIN FETCH lecture.attachments
            WHERE lecture.course.id = :courseId
            """)
    Set<Lecture> findAllByCourseIdWithAttachments(@Param("courseId") Long courseId);

    @Query("""
            SELECT lecture
            FROM Lecture lecture
                LEFT JOIN FETCH lecture.attachments
                LEFT JOIN FETCH lecture.lectureUnits
            WHERE lecture.course.id = :courseId
            """)
    Set<Lecture> findAllByCourseIdWithAttachmentsAndLectureUnits(@Param("courseId") Long courseId);

    @Query("""
            SELECT lecture
            FROM Lecture lecture
                LEFT JOIN FETCH lecture.attachments attachment
                LEFT JOIN FETCH lecture.lectureUnits lectureUnit
                LEFT JOIN FETCH lectureUnit.attachment luAttachment
                LEFT JOIN FETCH lectureUnit.slides slides
            WHERE lecture.course.id = :courseId
            """)
    Set<Lecture> findAllByCourseIdWithAttachmentsAndLectureUnitsAndSlides(@Param("courseId") Long courseId);

    @Query("""
            SELECT lecture
            FROM Lecture lecture
                LEFT JOIN FETCH lecture.attachments
                LEFT JOIN FETCH lecture.posts
                LEFT JOIN FETCH lecture.lectureUnits lu
                LEFT JOIN FETCH lu.completedUsers cu
                LEFT JOIN FETCH lu.competencies
                LEFT JOIN FETCH lu.exercise exercise
                LEFT JOIN FETCH exercise.competencies
            WHERE lecture.id = :lectureId
            """)
    Optional<Lecture> findByIdWithAttachmentsAndPostsAndLectureUnitsAndCompetenciesAndCompletions(@Param("lectureId") Long lectureId);

    @Query("""
            SELECT lecture
            FROM Lecture lecture
                LEFT JOIN FETCH lecture.lectureUnits lu
                LEFT JOIN FETCH lu.competencies
                LEFT JOIN FETCH lu.exercise exercise
                LEFT JOIN FETCH exercise.competencies
            WHERE lecture.id = :lectureId
            """)
    Optional<Lecture> findByIdWithLectureUnitsAndCompetencies(@Param("lectureId") Long lectureId);

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
                LEFT JOIN FETCH lecture.lectureUnits lectureUnit
                LEFT JOIN FETCH lectureUnit.attachment luAttachment
                LEFT JOIN FETCH lectureUnit.slides slides
                LEFT JOIN FETCH lecture.attachments
            WHERE lecture.id = :lectureId
            """)
    Optional<Lecture> findByIdWithLectureUnitsAndSlidesAndAttachments(@Param("lectureId") long lectureId);

    @Query("""
            SELECT lecture
            FROM Lecture lecture
                LEFT JOIN FETCH lecture.lectureUnits
            WHERE lecture.title = :title AND lecture.course.id = :courseId
            """)
    Set<Lecture> findAllByTitleAndCourseIdWithLectureUnits(@Param("title") String title, @Param("courseId") long courseId);

    /**
     * Finds a lecture by its title and course id and throws a NoUniqueQueryException if multiple lectures are found.
     *
     * @param title    the title of the lecture
     * @param courseId the id of the course
     * @return the lecture with the given title and course id
     * @throws NoUniqueQueryException if multiple lectures are found with the same title
     */
    default Optional<Lecture> findUniqueByTitleAndCourseIdWithLectureUnitsElseThrow(String title, long courseId) throws NoUniqueQueryException {
        Set<Lecture> allLectures = findAllByTitleAndCourseIdWithLectureUnits(title, courseId);
        if (allLectures.size() > 1) {
            throw new NoUniqueQueryException("Found multiple lectures with title " + title + " in course with id " + courseId);
        }
        return allLectures.stream().findFirst();
    }

    @SuppressWarnings("PMD.MethodNamingConventions")
    Page<Lecture> findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContaining(String partialTitle, String partialCourseTitle, Pageable pageable);

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
                AND (lecture.title LIKE %:partialTitle% OR lecture.course.title LIKE %:partialCourseTitle%)
            """)
    Page<Lecture> findByTitleInLectureOrCourseAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle, @Param("partialCourseTitle") String partialCourseTitle,
            @Param("groups") Set<String> groups, Pageable pageable);

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
    default Lecture findByIdWithLectureUnitsAndCompetenciesElseThrow(Long lectureId) {
        return getValueElseThrow(findByIdWithLectureUnitsAndCompetencies(lectureId), lectureId);
    }

    @NotNull
    default Lecture findByIdWithAttachmentsAndPostsAndLectureUnitsAndCompetenciesAndCompletionsElseThrow(Long lectureId) {
        return getValueElseThrow(findByIdWithAttachmentsAndPostsAndLectureUnitsAndCompetenciesAndCompletions(lectureId), lectureId);
    }

    @NotNull
    default Lecture findByIdWithLectureUnitsAndAttachmentsElseThrow(Long lectureId) {
        return getValueElseThrow(findByIdWithLectureUnitsAndAttachments(lectureId), lectureId);
    }

    @NotNull
    default Lecture findByIdWithLectureUnitsAndSlidesAndAttachmentsElseThrow(long lectureId) {
        return getValueElseThrow(findByIdWithLectureUnitsAndSlidesAndAttachments(lectureId), lectureId);
    }

    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.CourseContentCount(
                COUNT(l.id),
                l.course.id
            )
            FROM Lecture l
            WHERE l.course.id IN :courseIds
                AND (l.visibleDate IS NULL OR l.visibleDate <= :now)
            GROUP BY l.course.id
            """)
    Set<CourseContentCount> countVisibleLectures(@Param("courseIds") Set<Long> courseIds, @Param("now") ZonedDateTime now);
}
