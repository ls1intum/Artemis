package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.web.rest.dto.ExamUserAttendanceCheckDTO;

@Repository
public interface ExamUserRepository extends JpaRepository<ExamUser, Long> {

    @Query("""
            SELECT eu
            FROM ExamUser eu
            WHERE eu.exam.id = :examId
               AND eu.user.id = :userId
            """)
    Optional<ExamUser> findByExamIdAndUserId(@Param("examId") long examId, @Param("userId") long userId);

    @EntityGraph(type = LOAD, attributePaths = { "exam" })
    Optional<ExamUser> findWithExamById(long examUserId);

    List<ExamUser> findAllByExamId(long examId);

    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.ExamUserAttendanceCheckDTO(examUser.id, examUser.studentImagePath, examUser.user.login,
            examUser.user.registrationNumber, examUser.signingImagePath,
            studentExams.started, studentExams.submitted)
            FROM ExamUser examUser
                LEFT JOIN examUser.exam exam
                LEFT JOIN exam.studentExams studentExams ON studentExams.user.id = examUser.user.id
            WHERE exam.id = :examId
            AND studentExams.started = true
            AND (examUser.signingImagePath IS NULL
                OR examUser.signingImagePath = ''
                OR examUser.didCheckImage = false
                OR examUser.didCheckLogin = false
                OR examUser.didCheckRegistrationNumber = false
                OR examUser.didCheckName = false)
            """)
    Set<ExamUserAttendanceCheckDTO> findAllExamUsersWhoDidNotSign(@Param("examId") long examId);
}
