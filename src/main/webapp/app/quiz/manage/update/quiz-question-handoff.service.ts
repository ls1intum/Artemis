import { Service } from '@angular/core';
import { GeneratedQuestion } from 'app/hyperion/quiz/quiz-ai-generation-modal/quiz-ai-generation.types';

/**
 * Carries questions from another page into the next new quiz the editor opens for the same course, so that a page such as Hyperion's
 * quiz question pool can pre-fill a quiz without creating it itself. Questions are handed over once: taking them clears the service,
 * whether or not the course matches.
 */
@Service()
export class QuizQuestionHandoffService {
    private pending?: { offerId: number; courseId: number; questions: GeneratedQuestion[] };

    private lastOfferId = 0;

    /**
     * Stores questions for the next new quiz of a course, replacing any that were stored before.
     *
     * @param courseId  the course whose quiz editor may take the questions
     * @param questions the questions the editor should append
     * @returns the id of this offer, which {@link clear} takes
     */
    offer(courseId: number, questions: GeneratedQuestion[]): number {
        this.lastOfferId += 1;
        this.pending = { offerId: this.lastOfferId, courseId, questions: [...questions] };
        return this.lastOfferId;
    }

    /**
     * Returns the stored questions when they were offered for the given course, and clears them in every case.
     *
     * @param courseId the course of the quiz editor that takes the questions
     */
    take(courseId: number | undefined): GeneratedQuestion[] {
        const pending = this.pending;
        this.pending = undefined;
        return pending && pending.courseId === courseId ? pending.questions : [];
    }

    /**
     * Drops the stored questions of one offer, for when the quiz editor they were offered for does not open. A later offer that replaced
     * it stays stored.
     *
     * @param offerId the id {@link offer} returned
     */
    clear(offerId: number): void {
        if (this.pending?.offerId === offerId) {
            this.pending = undefined;
        }
    }
}
