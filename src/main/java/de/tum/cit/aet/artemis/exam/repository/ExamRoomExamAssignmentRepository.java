package de.tum.cit.aet.artemis.exam.repository;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoomExamAssignment;

/**
 * Spring Data JPA repository for the {@link de.tum.cit.aet.artemis.exam.domain.room.ExamRoomExamAssignment} entity.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Repository
public interface ExamRoomExamAssignmentRepository extends ArtemisJpaRepository<ExamRoomExamAssignment, Long> {

    @Transactional // ok because of modifying query
    @Modifying
    void deleteAllByExamId(long examId);
}
