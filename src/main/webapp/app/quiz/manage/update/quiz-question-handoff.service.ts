import { Service } from '@angular/core';
import { GeneratedQuestion } from 'app/hyperion/quiz/quiz-ai-generation-modal/quiz-ai-generation.types';

/**
 * Carries questions from another page into the next new quiz the editor opens, so that a page such as Hyperion's quiz question
 * pool can pre-fill a quiz without creating it itself. Questions are handed over once: taking them clears the service.
 */
@Service()
export class QuizQuestionHandoffService {
    private pending: GeneratedQuestion[] = [];

    /**
     * Stores questions for the next new quiz, replacing any that were stored before.
     *
     * @param questions the questions the editor should append
     */
    offer(questions: GeneratedQuestion[]): void {
        this.pending = [...questions];
    }

    /**
     * Returns the stored questions and clears them.
     */
    take(): GeneratedQuestion[] {
        const questions = this.pending;
        this.pending = [];
        return questions;
    }
}
