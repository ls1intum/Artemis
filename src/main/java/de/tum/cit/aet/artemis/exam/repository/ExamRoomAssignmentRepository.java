package de.tum.cit.aet.artemis.exam.repository;

import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoomExamAssignment;

/**
 * Spring Data JPA repository for the {@link ExamRoomExamAssignment} entity.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Repository
public interface ExamRoomAssignmentRepository extends ArtemisJpaRepository<ExamRoomExamAssignment, Long> {

    List<ExamRoomExamAssignment> findByExam_Id(@NotNull Long examId);

    List<ExamRoomExamAssignment> findByExamRoom_Id(@NotNull Long examRoomId);

    Optional<ExamRoomExamAssignment> findByExam_IdAndExamRoom_Id(@NotNull Long examId, @NotNull Long examRoomId);
}
