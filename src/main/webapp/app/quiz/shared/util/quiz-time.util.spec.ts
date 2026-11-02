import dayjs from 'dayjs/esm';
import { describe, expect, it } from 'vitest';
import { formatQuizRelativeTime, quizCountdown } from 'app/quiz/shared/util/quiz-time.util';

describe('formatQuizRelativeTime', () => {
    it('should format long durations in days', () => {
        expect(formatQuizRelativeTime(24 * 60 * 60)).toBe('1 d');
        expect(formatQuizRelativeTime(7 * 24 * 60 * 60)).toBe('7 d');
    });

    it('should include remaining hours for day-long durations', () => {
        expect(formatQuizRelativeTime(24 * 60 * 60 - 1)).toBe('23 h 59 min');
        expect(formatQuizRelativeTime(7 * 24 * 60 * 60 + 2 * 60 * 60)).toBe('7 d 2 h');
    });

    it('should format hour-long durations', () => {
        expect(formatQuizRelativeTime(60 * 60)).toBe('1 h');
        expect(formatQuizRelativeTime(60 * 60 - 1)).toBe('60 min');
        expect(formatQuizRelativeTime(2 * 60 * 60 + 5 * 60)).toBe('2 h 5 min');
    });

    it('should keep the existing minute and second formatting', () => {
        expect(formatQuizRelativeTime(210)).toBe('3 min 30 s');
        expect(formatQuizRelativeTime(209)).toBe('3 min 29 s');
        expect(formatQuizRelativeTime(250)).toBe('5 min');
        expect(formatQuizRelativeTime(220)).toBe('4 min');
        expect(formatQuizRelativeTime(130)).toBe('2 min 10 s');
        expect(formatQuizRelativeTime(50)).toBe('50 s');
        expect(formatQuizRelativeTime(60)).toBe('1 min 0 s');
        expect(formatQuizRelativeTime(59)).toBe('59 s');
        expect(formatQuizRelativeTime(125)).toBe('2 min 5 s');
        expect(formatQuizRelativeTime(45)).toBe('45 s');
    });
});

describe('quiz countdown', () => {
    const now = dayjs('2026-10-19T10:00:00Z');
    it('keeps submission enabled while the deadline is unknown', () => {
        expect(quizCountdown(undefined, now, 'Ended')).toEqual({ seconds: 0, text: '?' });
    });
    it('marks a deadline at the current instant as ended', () => {
        expect(quizCountdown(now, now, 'Ended')).toEqual({ seconds: -1, text: 'Ended' });
        expect(quizCountdown(now.subtract(1, 'second'), now, 'Ended')).toEqual({ seconds: -1, text: 'Ended' });
    });
    it('formats the remaining seconds before the deadline', () => {
        expect(quizCountdown(now.add(125, 'second'), now, 'Ended')).toEqual({ seconds: 125, text: '2 min 5 s' });
    });
});
