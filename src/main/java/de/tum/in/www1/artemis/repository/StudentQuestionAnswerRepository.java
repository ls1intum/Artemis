package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.StudentQuestionAnswer;

/**
 * Spring Data repository for the StudentQuestionAnswer entity.
 */
@SuppressWarnings("unused")
@Repository
public interface StudentQuestionAnswerRepository extends JpaRepository<StudentQuestionAnswer, Long> {

}
