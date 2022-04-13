package de.tum.in.www1.artemis.repository;

import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
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
            LEFT JOIN FETCH lu.learningGoals
            WHERE lecture.id = :#{#lectureId}
            """)
    Optional<Lecture> findByIdWithPostsAndLectureUnitsAndLearningGoals(@Param("lectureId") Long lectureId);

    @Query("""
            SELECT lecture
            FROM Lecture lecture
            LEFT JOIN FETCH lecture.lectureUnits
            WHERE lecture.id = :#{#lectureId}
            """)
    Optional<Lecture> findByIdWithLectureUnits(@Param("lectureId") Long lectureId);

    Page<Lecture> findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContaining(String partialTitle, String partialCourseTitle, Pageable pageable);

    /**
     * Query which fetches all lectures for which the user is instructor in the course and matching the search criteria.
     *
     * @param partialTitle       lecture title search term
     * @param partialCourseTitle course title search term
     * @param groups             user groups
     * @param pageable           Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT l FROM Lecture l
            WHERE l.id IN
             (SELECT courseL.id FROM Lecture courseL
              WHERE (courseL.course.instructorGroupName IN :groups OR courseL.course.editorGroupName IN :groups)
              AND (courseL.title LIKE %:partialTitle% OR courseL.course.title LIKE %:partialCourseTitle%))
              """)
    Page<ModelingExercise> findByTitleInLectureOrCourseAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle, @Param("partialCourseTitle") String partialCourseTitle,
            @Param("groups") Set<String> groups, Pageable pageable);

    /**
     * Returns the title of the lecture with the given id
     *
     * @param lectureId the id of the lecture
     * @return the name/title of the lecture or null if the lecture does not exist
     */
    @Query("""
            SELECT l.title
            FROM Lecture l
            WHERE l.id = :lectureId
            """)
    String getLectureTitle(@Param("lectureId") Long lectureId);

    @NotNull
    default Lecture findByIdElseThrow(long lectureId) {
        return findById(lectureId).orElseThrow(() -> new EntityNotFoundException("Lecture", lectureId));
    }

    @NotNull
    default Lecture findByIdWithPostsAndLectureUnitsAndLearningGoalsElseThrow(Long lectureId) {
        return findByIdWithPostsAndLectureUnitsAndLearningGoals(lectureId).orElseThrow(() -> new EntityNotFoundException("Lecture", lectureId));
    }

    @NotNull
    default Lecture findByIdWithLectureUnitsElseThrow(Long lectureId) {
        return findByIdWithLectureUnits(lectureId).orElseThrow(() -> new EntityNotFoundException("Lecture", lectureId));
    }
}
