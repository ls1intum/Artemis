package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.exam.ExamUser;

@Repository
public interface ExamUserRepository extends JpaRepository<ExamUser, Long> {

    @Query("select eu from ExamUser eu where eu.exam.id = :#{#examId} and eu.user.id = :#{#userId}")
    ExamUser findByExamIdAndUserId(long examId, long userId);

    List<ExamUser> findAllByExamId(long examId);
}
