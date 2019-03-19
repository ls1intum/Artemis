package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.StudentQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data  repository for the StudentQuestion entity.
 */
@SuppressWarnings("unused")
@Repository
public interface StudentQuestionRepository extends JpaRepository<StudentQuestion, Long> {

    @Query("select student_question from StudentQuestion student_question where student_question.author.login = ?#{principal.username}")
    List<StudentQuestion> findByAuthorIsCurrentUser();

}
