import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';

const HIGHLIGHT_DURATION_IN_MILLISECONDS = 1500;

export function addTemporaryHighlightToQuestion(questionToBeHighlighted: QuizQuestion) {
    questionToBeHighlighted.isHighlighted = true;
    setTimeout(() => {
        questionToBeHighlighted.isHighlighted = false;
    }, HIGHLIGHT_DURATION_IN_MILLISECONDS);
}
