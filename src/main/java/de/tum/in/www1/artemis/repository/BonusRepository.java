package de.tum.in.www1.artemis.repository;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Bonus;

/**
 * Spring Data JPA for the BonusSource entity
 */
@Repository
public interface BonusRepository extends JpaRepository<Bonus, Long> {

    /**
     * Find a bonus source by its source grading scale id
     *
     * @param sourceGradindScaleId the source grading scale id
     * @return an Optional with the bonus source if such scale exists and an empty Optional otherwise
     */
    Optional<Bonus> findBySource(@Param("sourceGradindScaleId") Long sourceGradindScaleId);

    /**
     * Find a bonus source with its source grading scale belonging to the course with given id
     *
     * @param courseId the courses id
     * @return an Optional with the bonus source if such scale exists and an empty Optional otherwise
     */
    @Query("""
            SELECT bonus
            FROM Bonus bonus
            WHERE bonus.source.course.id = :#{#courseId}
            """)
    Optional<Bonus> findBySourceCourseId(@Param("courseId") Long courseId);

    /**
     * Find a bonus source with its source grading scale belonging to the exam with given id
     *
     * @param examId the courses id
     * @return an Optional with the bonus source if such scale exists and an empty Optional otherwise
     */
    @Query("""
            SELECT bonus
            FROM Bonus bonus
            WHERE bonus.source.exam.id = :#{#examId}
            """)
    Optional<Bonus> findBySourceExamId(@Param("examId") Long examId);

    @Query("""
            SELECT gs.bonusFrom
            FROM GradingScale gs
            WHERE gs.exam.id = :#{#examId}
            """)
    Set<Bonus> findAllByTargetExamId(@Param("examId") Long examId);

    // /**
    // * Finds a bonus source for course by id or throws an exception if no such bonus source exists.
    // * If there is more the one bonus source for the course, all but the first one saved will get deleted
    // * and the first one saved will be returned. This is necessary to avoid potential concurrency issues
    // * since only one bonus source can exist for a course at a time.
    // *
    // * @param courseId the course to which the bonus source belongs
    // * @return the found bonus source
    // */
    // @NotNull
    // default BonusSource findByCourseIdOrElseThrow(Long courseId) {
    // try {
    // return findByCourseId(courseId).orElseThrow(() -> new EntityNotFoundException("Bonus source with course ID " + courseId + " doesn't exist"));
    // }
    // catch (IncorrectResultSizeDataAccessException exception) {
    // return deleteExcessiveBonusSources(courseId, false);
    // }
    // }
    //
    // /**
    // * Find all bonus sources for a course
    // *
    // * @param courseId the id of the course
    // * @return a list of bonus sources for the course
    // */
    // List<BonusSource> findAllByCourseId(@Param("courseId") Long courseId);
    //
    // /**
    // * Finds a bonus source for exam by id or throws an exception if no such bonus source exists.
    // * If there is more the one bonus source for the exam, all but the first one saved will get deleted
    // * and the first one saved will be returned. This is necessary to avoid potential concurrency issues
    // * since only one bonus source can exist for an exam at a time.
    // *
    // * @param examId the exam to which the bonus source belongs
    // * @return the found bonus source
    // */
    // @NotNull
    // default BonusSource findByExamIdOrElseThrow(Long examId) {
    // try {
    // return findByExamId(examId).orElseThrow(() -> new EntityNotFoundException("Bonus source with exam ID " + examId + " doesn't exist"));
    // }
    // catch (IncorrectResultSizeDataAccessException exception) {
    // return deleteExcessiveBonusSources(examId, true);
    // }
    // }
    //
    // /**
    // * Find all bonus sources for an exam
    // *
    // * @param examId the id of the exam
    // * @return a list of bonus sources for the exam
    // */
    // List<BonusSource> findAllByExamId(@Param("examId") Long examId);
    //
    //
    //
    // /**
    // * Deletes all excessive bonus sources but the first saved for a course/exam
    // *
    // * @param entityId the id of the course/exam
    // * @param isExam determines if the method is handling a bonus source for course or exam
    // * @return the only remaining bonus source for the course/exam
    // */
    // default BonusSource deleteExcessiveBonusSources(Long entityId, boolean isExam) {
    // List<BonusSource> bonusSources;
    // if (isExam) {
    // bonusSources = findAllByExamId(entityId);
    // }
    // else {
    // bonusSources = findAllByCourseId(entityId);
    // }
    // for (int i = 1; i < bonusSources.size(); i++) {
    // deleteById(bonusSources.get(i).getId());
    // }
    // return bonusSources.get(0);
    // }
}
