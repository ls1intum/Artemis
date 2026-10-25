package de.tum.cit.aet.artemis.exam.repository;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;

@Repository
@Lazy
@Conditional(ExamEnabled.class)
public interface ExamHomeRepository extends ArtemisJpaRepository<Exam, Long> {

    interface Item {

        long getId();

        String getTitle();

        ZonedDateTime getStartDate();
    }

    @Query("""
            SELECT e.id AS id,e.title AS title,e.startDate AS startDate FROM Exam e
            WHERE e.course.id=:courseId AND e.testExam=FALSE AND e.visibleDate<=:asOf AND e.startDate>:asOf AND e.startDate<=:soon
            AND EXISTS(SELECT registration.id FROM ExamUser registration WHERE registration.exam=e AND registration.user.id=:userId)
            ORDER BY e.startDate,e.id
            """)
    List<Item> findUpcoming(@Param("courseId") long courseId, @Param("userId") long userId, @Param("asOf") ZonedDateTime asOf, @Param("soon") ZonedDateTime soon,
            Pageable pageable);
}
