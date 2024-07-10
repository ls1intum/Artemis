package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.quiz.DragAndDropQuestion;
import de.tum.in.www1.artemis.domain.quiz.QuizQuestion;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the QuizQuestion entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface QuizQuestionRepository extends ArtemisJpaRepository<QuizQuestion, Long> {

    Set<QuizQuestion> findByExercise_Id(long id);

    @Query("""
            SELECT question
            FROM DragAndDropQuestion question
            WHERE question.id = :questionId
            """)
    Optional<DragAndDropQuestion> findDnDQuestionById(@Param("questionId") long questionId);

    default DragAndDropQuestion findDnDQuestionByIdOrElseThrow(Long questionId) {
        return findDnDQuestionById(questionId).orElseThrow(() -> new EntityNotFoundException("DragAndDropQuestion", questionId));
    }
}
