import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { secondsToMilliseconds } from 'app/shared/util/utils';

/**
 * Keep this value in sync with the CSS file of {@link QuizParticipationComponent} and the respective "question-highlight" class.
 */
const HIGHLIGHT_DURATION_IN_MILLISECONDS = secondsToMilliseconds(1.5);

export function addTemporaryHighlightToQuestion(questionToBeHighlighted: QuizQuestion) {
    questionToBeHighlighted.isHighlighted = true;
    setTimeout(() => {
        questionToBeHighlighted.isHighlighted = false;
    }, HIGHLIGHT_DURATION_IN_MILLISECONDS);
}
