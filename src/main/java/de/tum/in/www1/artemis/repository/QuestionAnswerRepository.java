package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.QuestionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data  repository for the QuestionAnswer entity.
 */
@SuppressWarnings("unused")
@Repository
public interface QuestionAnswerRepository extends JpaRepository<QuestionAnswer, Long> {

    @Query("select question_answer from QuestionAnswer question_answer where question_answer.author.login = ?#{principal.username}")
    List<QuestionAnswer> findByAuthorIsCurrentUser();

}
