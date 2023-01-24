package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.ExamUser;

@Repository
public interface ExamUserRepository extends JpaRepository<ExamUser, Long> {

    ExamUser findByExamIdAndUser(long examId, User user);
}
