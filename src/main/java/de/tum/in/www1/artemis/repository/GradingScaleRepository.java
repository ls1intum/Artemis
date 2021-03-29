package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.GradingScale;

@Repository
public interface GradingScaleRepository extends JpaRepository<GradingScale, Long> {

    Optional<GradingScale> findByCourse_Id(Long courseId);

    Optional<GradingScale> findByExam_Id(Long examId);

}
