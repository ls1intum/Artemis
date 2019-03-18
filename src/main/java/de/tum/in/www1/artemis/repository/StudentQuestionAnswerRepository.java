package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.StudentQuestionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data  repository for the StudentQuestionAnswer entity.
 */
@SuppressWarnings("unused")
@Repository
public interface StudentQuestionAnswerRepository extends JpaRepository<StudentQuestionAnswer, Long> {

    @Query("select question_answer from StudentQuestionAnswer question_answer where question_answer.author.login = ?#{principal.username}")
    List<StudentQuestionAnswer> findByAuthorIsCurrentUser();

}
