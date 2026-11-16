import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';
import { MetisCheckStatus, MetisDashboardRowDTO, MetisQuestionDTO } from 'app/metis/shared/entities/metis.model';
import { reviewStatusOf } from 'app/metis/shared/review-status.util';

const approvedAt = dayjs('2026-11-02T23:59:00');

function question(id: number, approved: boolean, parentQuestionId?: number): MetisQuestionDTO {
    return {
        id,
        checkId: 1,
        questionOrder: id,
        parentQuestionId,
        questionText: `Question ${id}`,
        timeLimitSeconds: 240,
        regenerationCount: 0,
        approvedAt: approved ? approvedAt : undefined,
    };
}

function row(status: MetisCheckStatus, questions: MetisQuestionDTO[]): MetisDashboardRowDTO {
    return { participationId: 1, studentName: 'Anna Huber', studentLogin: 'anna.huber', check: { id: 1, participationId: 1, status }, questions };
}

describe('reviewStatusOf', () => {
    it('should report a failed or skipped check whatever its questions say', () => {
        expect(reviewStatusOf(row(MetisCheckStatus.FAILED, []))).toBe('FAILED');
        expect(reviewStatusOf(row(MetisCheckStatus.SKIPPED, [question(1, true)]))).toBe('SKIPPED');
    });

    it('should report a check whose questions are not generated yet as being prepared', () => {
        expect(reviewStatusOf(row(MetisCheckStatus.PENDING, []))).toBe('PREPARING');
        expect(reviewStatusOf(row(MetisCheckStatus.GENERATING, []))).toBe('PREPARING');
    });

    it('should keep a check in review waiting while a main question is open', () => {
        expect(reviewStatusOf(row(MetisCheckStatus.IN_REVIEW, [question(1, true), question(2, false)]))).toBe('AWAITING');
        expect(reviewStatusOf(row(MetisCheckStatus.IN_REVIEW, []))).toBe('AWAITING');
    });

    it('should release a check in review once every main question is approved, whatever the follow-ups say', () => {
        expect(reviewStatusOf(row(MetisCheckStatus.IN_REVIEW, [question(1, true), question(2, false, 1), question(3, true)]))).toBe('RELEASED');
    });

    it('should report a released or started check as released, approved or not', () => {
        expect(reviewStatusOf(row(MetisCheckStatus.READY, [question(1, false)]))).toBe('RELEASED');
        expect(reviewStatusOf(row(MetisCheckStatus.IN_PROGRESS, [question(1, false), question(2, true)]))).toBe('RELEASED');
        expect(reviewStatusOf(row(MetisCheckStatus.COMPLETED, [question(1, true)]))).toBe('RELEASED');
    });
});
