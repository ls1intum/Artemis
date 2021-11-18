package de.tum.in.www1.artemis.repository;

import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

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
