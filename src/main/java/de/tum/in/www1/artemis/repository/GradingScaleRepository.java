package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.GradingScale;

@Repository
public interface GradingScaleRepository extends JpaRepository<GradingScale, Long> {

    Optional<GradingScale> findByCourse_Id(Long courseId);

    Optional<GradingScale> findByExam_Id(Long examId);

    @Query("delete from GradingScale gs where gs.course.id=:courseId")
    void deleteGradingScaleForCourse(@Param("courseId") Long courseId);

    @Query("delete from GradingScale gs where gs.exam.id=:examId")
    void deleteGradingScaleForExam(@Param("examId") Long examId);

}
