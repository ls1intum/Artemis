import { describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';
import { createCleanupOperation } from './cleanup-operation.model';

describe('cleanup operation defaults', () => {
    it('initializes independent validation state and a six month range for date-based operations', () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date('2026-10-19T10:00:00Z'));
        try {
            const first = createCleanupOperation('deleteNonRatedResults');
            const second = createCleanupOperation('deleteOldRatedResults');
            expect(first.deleteFrom?.isSame(dayjs().subtract(12, 'months'))).toBe(true);
            expect(first.deleteTo?.isSame(dayjs().subtract(6, 'months'))).toBe(true);
            first.datesValid.set(false);
            first.deleteFromValid.set(false);
            expect(second.datesValid()).toBe(true);
            expect(second.deleteFromValid()).toBe(true);
            expect(first.deleteToValid()).toBe(true);
        } finally {
            vi.useRealTimers();
        }
    });

    it('leaves age-based ranges unset while retaining each action', () => {
        const operation = createCleanupOperation('warnOldCoursesReset', { ageBased: true, action: 'warn' });
        expect(operation.action).toBe('warn');
        expect(operation.ageBased).toBe(true);
        expect(operation.deleteFrom).toBeUndefined();
        expect(operation.deleteTo).toBeUndefined();
        expect(operation.datesValid()).toBe(true);
    });
});
