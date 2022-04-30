package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActivity;

public interface ExamActivityRepository extends JpaRepository<ExamActivity, Long> {
}
