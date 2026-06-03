import { describe, expect, it } from 'vitest';
import { formatQuizRelativeTime } from 'app/quiz/shared/util/quiz-time.util';

describe('formatQuizRelativeTime', () => {
    it('should format long durations in days', () => {
        expect(formatQuizRelativeTime(7 * 24 * 60 * 60)).toBe('7 d');
    });

    it('should include remaining hours for day-long durations', () => {
        expect(formatQuizRelativeTime(7 * 24 * 60 * 60 + 2 * 60 * 60)).toBe('7 d 2 h');
    });

    it('should format hour-long durations', () => {
        expect(formatQuizRelativeTime(2 * 60 * 60 + 5 * 60)).toBe('2 h 5 min');
    });

    it('should keep the existing minute and second formatting', () => {
        expect(formatQuizRelativeTime(250)).toBe('5 min');
        expect(formatQuizRelativeTime(125)).toBe('2 min 5 s');
        expect(formatQuizRelativeTime(45)).toBe('45 s');
    });
});
