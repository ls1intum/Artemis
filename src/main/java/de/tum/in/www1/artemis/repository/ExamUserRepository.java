package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.exam.ExamUser;

@Repository
public interface ExamUserRepository extends JpaRepository<ExamUser, Long> {

    @Query("""
            SELECT eu
            FROM ExamUser eu
            WHERE eu.exam.id = :examId
               AND eu.user.id = :userId
            """)
    ExamUser findByExamIdAndUserId(@Param("examId") long examId, @Param("userId") long userId);

    List<ExamUser> findAllByExamId(long examId);
}
