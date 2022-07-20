package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;

@Repository
public interface ExamActionRepository extends JpaRepository<ExamAction, Long> {

    List<ExamAction> findByExamActivityId(Long examActivityId);
}
