import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { addTemporaryHighlightToQuestion } from './quiz-stepwizard.util';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';

describe('QuizStepwizardUtil', () => {
    setupTestBed({ zoneless: true });

    let mockQuestion: QuizQuestion;

    beforeEach(() => {
        mockQuestion = {
            isHighlighted: false,
        } as QuizQuestion;

        vi.useFakeTimers(); // Mock timers
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    describe('addTemporaryHighlightToQuestion', () => {
        it('should immediately set isHighlighted to true', () => {
            addTemporaryHighlightToQuestion(mockQuestion);

            expect(mockQuestion.isHighlighted).toBe(true);
        });

        it('should set isHighlighted to false after 1500ms', () => {
            addTemporaryHighlightToQuestion(mockQuestion);

            expect(mockQuestion.isHighlighted).toBe(true);

            vi.advanceTimersByTime(1500);

            expect(mockQuestion.isHighlighted).toBe(false);
        });

        it('should not set isHighlighted to false before 1500ms', () => {
            addTemporaryHighlightToQuestion(mockQuestion);

            expect(mockQuestion.isHighlighted).toBe(true);

            // Fast-forward time by 1499ms (just before the timeout)
            vi.advanceTimersByTime(1499);

            expect(mockQuestion.isHighlighted).toBe(true);
        });

        it('should handle multiple calls correctly', () => {
            addTemporaryHighlightToQuestion(mockQuestion);
            expect(mockQuestion.isHighlighted).toBe(true);

            // Call again before first timeout completes
            vi.advanceTimersByTime(500);
            addTemporaryHighlightToQuestion(mockQuestion);
            expect(mockQuestion.isHighlighted).toBe(true);

            // First timeout should still trigger
            vi.advanceTimersByTime(1000);
            expect(mockQuestion.isHighlighted).toBe(false);

            // But second timeout should set it back to false again
            vi.advanceTimersByTime(500);
            expect(mockQuestion.isHighlighted).toBe(false);
        });
    });
});
