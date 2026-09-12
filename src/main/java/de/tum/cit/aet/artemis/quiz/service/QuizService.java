package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizConfiguration;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;

@Profile(PROFILE_CORE)
@Lazy
@Service
public abstract class QuizService<T extends QuizConfiguration> {

    /**
     * Save the given QuizConfiguration to the database according to the implementor.
     *
     * @param quizConfiguration the QuizConfiguration to be saved.
     * @return the saved QuizConfiguration
     */
    protected abstract T saveAndFlush(T quizConfiguration);

    protected QuizService() {
    }

    /**
     * Save the given QuizConfiguration
     *
     * @param quizConfiguration the QuizConfiguration to be saved
     * @return saved QuizConfiguration
     */
    public T save(T quizConfiguration) {
        // Component ids are question-scoped and referenced by submitted-answer selections. Ensure new components receive
        // stable ids before the question content is serialized.
        for (var quizQuestion : quizConfiguration.getQuizQuestions()) {
            switch (quizQuestion) {
                case MultipleChoiceQuestion multipleChoiceQuestion -> multipleChoiceQuestion.assignMissingComponentIds();
                case DragAndDropQuestion dragAndDropQuestion -> dragAndDropQuestion.assignMissingComponentIds();
                case ShortAnswerQuestion shortAnswerQuestion -> shortAnswerQuestion.assignMissingComponentIds();
                default -> {
                }
            }
        }

        return saveAndFlush(quizConfiguration);
    }

}
