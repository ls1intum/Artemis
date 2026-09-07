package de.tum.cit.aet.artemis.exam.repository;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;

/**
 * Aggregate counts of how widely the optional features of exams are switched on, for the admin feature usage page.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Repository
public interface ExamAdoptionRepository extends ArtemisJpaRepository<Exam, Long> {

    @Query("""
            SELECT COUNT(exam)
            FROM Exam exam
            WHERE exam.testExam IS TRUE
            """)
    long countTestExams();

    @Query("""
            SELECT COUNT(exam)
            FROM Exam exam
            WHERE exam.examWithAttendanceCheck IS TRUE
            """)
    long countWithAttendanceCheck();
}
