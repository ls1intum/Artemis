package de.tum.in.www1.artemis.repository;

import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the Lecture entity.
 */
@SuppressWarnings("unused")
@Repository
public interface LectureRepository extends JpaRepository<Lecture, Long> {

    @Query("""
            SELECT lecture
            FROM Lecture lecture
            LEFT JOIN FETCH lecture.attachments
            WHERE lecture.course.id = :#{#courseId}
            """)
    Set<Lecture> findAllByCourseIdWithAttachments(@Param("courseId") Long courseId);

    @Query("""
            SELECT lecture
            FROM Lecture lecture
            LEFT JOIN FETCH lecture.attachments
            LEFT JOIN FETCH lecture.lectureUnits
            WHERE lecture.course.id = :#{#courseId}
            """)
    Set<Lecture> findAllByCourseIdWithAttachmentsAndLectureUnits(@Param("courseId") Long courseId);

    @Query("""
            SELECT lecture
            FROM Lecture lecture
            LEFT JOIN FETCH lecture.posts
            LEFT JOIN FETCH lecture.lectureUnits lu
            LEFT JOIN FETCH lu.completedUsers cu
            LEFT JOIN FETCH lu.learningGoals
            LEFT JOIN FETCH lu.exercise exercise
            LEFT JOIN FETCH exercise.learningGoals
            WHERE lecture.id = :#{#lectureId}
            """)
    Optional<Lecture> findByIdWithPostsAndLectureUnitsAndLearningGoalsAndCompletions(@Param("lectureId") Long lectureId);

    @Query("""
            SELECT lecture
            FROM Lecture lecture
            LEFT JOIN FETCH lecture.lectureUnits lu
            LEFT JOIN FETCH lu.learningGoals
            LEFT JOIN FETCH lu.exercise exercise
            LEFT JOIN FETCH exercise.learningGoals
            WHERE lecture.id = :#{#lectureId}
            """)
    Optional<Lecture> findByIdWithLectureUnitsAndLearningGoals(@Param("lectureId") Long lectureId);

    @Query("""
            SELECT lecture
            FROM Lecture lecture
            LEFT JOIN FETCH lecture.lectureUnits
            WHERE lecture.id = :#{#lectureId}
            """)
    Optional<Lecture> findByIdWithLectureUnits(@Param("lectureId") Long lectureId);

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
            SELECT lecture FROM Lecture lecture
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
    default Lecture findByIdElseThrow(long lectureId) {
        return findById(lectureId).orElseThrow(() -> new EntityNotFoundException("Lecture", lectureId));
    }

    @NotNull
    default Lecture findByIdWithLectureUnitsAndLearningGoalsElseThrow(Long lectureId) {
        return findByIdWithLectureUnitsAndLearningGoals(lectureId).orElseThrow(() -> new EntityNotFoundException("Lecture", lectureId));
    }

    @NotNull
    default Lecture findByIdWithPostsAndLectureUnitsAndLearningGoalsAndCompletionsElseThrow(Long lectureId) {
        return findByIdWithPostsAndLectureUnitsAndLearningGoalsAndCompletions(lectureId).orElseThrow(() -> new EntityNotFoundException("Lecture", lectureId));
    }

    @NotNull
    default Lecture findByIdWithLectureUnitsElseThrow(Long lectureId) {
        return findByIdWithLectureUnits(lectureId).orElseThrow(() -> new EntityNotFoundException("Lecture", lectureId));
    }
}
