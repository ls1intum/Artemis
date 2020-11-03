package de.tum.in.www1.artemis.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Lecture;

/**
 * Spring Data repository for the Lecture entity.
 */
@SuppressWarnings("unused")
@Repository
public interface LectureRepository extends JpaRepository<Lecture, Long> {

    @Query("select lecture from Lecture lecture left join fetch lecture.attachments WHERE lecture.course.id = :#{#courseId}")
    Set<Lecture> findAllByCourseId(@Param("courseId") Long courseId);

    @Query("""
            SELECT lecture
            FROM Lecture lecture
            LEFT JOIN FETCH lecture.studentQuestions
            LEFT JOIN FETCH lecture.lectureUnits
            WHERE lecture.id = :#{#lectureId}
            """)
    Optional<Lecture> findByIdWithStudentQuestionsAndLectureUnits(@Param("lectureId") Long lectureId);

}
