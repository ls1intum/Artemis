package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActivity;

@Repository
public interface ExamActivityRepository extends JpaRepository<ExamActivity, Long> {

    ExamActivity findByStudentExamId(Long studentExamId);
}
