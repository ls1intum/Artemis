package de.tum.cit.aet.artemis.exam.test_repository;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.exam.repository.ExamRoomRepository;

@Lazy
@Repository
@Primary
public interface ExamRoomTestRepository extends ExamRoomRepository {
}
