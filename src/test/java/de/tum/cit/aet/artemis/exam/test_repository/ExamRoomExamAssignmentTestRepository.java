package de.tum.cit.aet.artemis.exam.test_repository;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoomExamAssignment;
import de.tum.cit.aet.artemis.exam.repository.ExamRoomExamAssignmentRepository;

@Conditional(ExamEnabled.class)
@Lazy
@Repository
@Primary
public interface ExamRoomExamAssignmentTestRepository extends ExamRoomExamAssignmentRepository {

    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = { "exam", "examRoom" })
    Set<ExamRoomExamAssignment> findAllByExamId(long examId);
}
