import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject, of } from 'rxjs';
import { ExamConductionCountsState } from './exam-conduction-counts-state';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamChecklist } from 'app/exam/shared/entities/exam-checklist.model';
import { ExamConductionCounts } from 'app/exam/shared/entities/exam-conduction-counts.model';

const counts = (generated: number, started: number, submitted = 2): ExamConductionCounts => ({
    numberOfGeneratedStudentExams: generated,
    numberOfExamsStarted: started,
    numberOfExamsSubmitted: submitted,
});
const checklist = (generated: number, started: number, submitted = 2) => Object.assign(new ExamChecklist(), counts(generated, started, submitted));

describe('ExamConductionCountsState', () => {
    const exam: Exam = { id: 1, testExam: true };
    let state: ExamConductionCountsState;
    let fetchCounts: ReturnType<typeof vi.fn<(exam: Exam) => Subject<ExamConductionCounts>>>;

    beforeEach(() => {
        vi.useFakeTimers();
        fetchCounts = vi.fn(() => new Subject<ExamConductionCounts>());
        state = new ExamConductionCountsState(fetchCounts);
        state.reset(exam);
    });

    afterEach(() => {
        state.destroy();
        vi.useRealTimers();
    });

    it.each([5, 7])('reconciles a delayed initial baseline of %s starts without adding overlapping events twice', (initialStarted) => {
        state.started();
        state.started();
        expect(fetchCounts).not.toHaveBeenCalled();
        state.initialize(checklist(8, initialStarted));
        expect(state.numberOfStarted()).toBe(initialStarted);
        expect(state.numberOfSubmitted()).toBe(2);
        expect(fetchCounts).toHaveBeenCalledExactlyOnceWith(exam);
        fetchCounts.mock.results[0].value.next(counts(8, 7));
        fetchCounts.mock.results[0].value.complete();
        expect(state.numberOfStarted()).toBe(7);
        expect(state.numberOfGeneratedStudentExams()).toBe(8);
    });

    it('lets slow requests complete and retains one trailing refresh through a continuous burst', () => {
        state.initialize(checklist(6, 5));
        state.started();
        const first = fetchCounts.mock.results[0].value;
        for (let i = 0; i < 20; i++) {
            vi.advanceTimersByTime(200);
            state.started();
        }
        expect(first.observed).toBe(true);
        expect(fetchCounts).toHaveBeenCalledTimes(1);
        first.next(counts(7, 6));
        // A snapshot requested before the new events must not erase their optimistic increments.
        expect(state.numberOfStarted()).toBe(26);
        expect(state.numberOfGeneratedStudentExams()).toBe(7);
        first.complete();
        expect(fetchCounts).toHaveBeenCalledTimes(2);
        const trailing = fetchCounts.mock.results[1].value;
        trailing.next(counts(27, 26));
        trailing.complete();
        vi.advanceTimersByTime(5000);
        expect(fetchCounts).toHaveBeenCalledTimes(2);
        expect(state.numberOfGeneratedStudentExams()).toBe(27);
        expect(state.numberOfStarted()).toBe(26);
    });

    it('limits fast responses to one request per second while retaining the last event', () => {
        const fetch = vi.fn().mockReturnValue(of(counts(6, 5)));
        state = new ExamConductionCountsState(fetch);
        state.reset(exam);
        state.initialize(checklist(6, 5));
        state.started();
        for (let i = 0; i < 100; i++) {
            state.started();
        }
        expect(fetch).toHaveBeenCalledTimes(1);
        fetch.mockReturnValue(of(counts(107, 106)));
        vi.advanceTimersByTime(999);
        expect(fetch).toHaveBeenCalledTimes(1);
        vi.advanceTimersByTime(1);
        expect(fetch).toHaveBeenCalledTimes(2);
        expect(state.numberOfStarted()).toBe(106);
        expect(state.numberOfGeneratedStudentExams()).toBe(107);
    });

    it('retries a failed final refresh without another event', () => {
        state.initialize(checklist(6, 5));
        state.started();
        fetchCounts.mock.results[0].value.error(new Error('offline'));
        vi.advanceTimersByTime(999);
        expect(fetchCounts).toHaveBeenCalledTimes(1);
        vi.advanceTimersByTime(1);
        expect(fetchCounts).toHaveBeenCalledTimes(2);
        fetchCounts.mock.results[1].value.next(counts(7, 6));
        fetchCounts.mock.results[1].value.complete();
        expect(state.numberOfStarted()).toBe(6);
        expect(state.numberOfGeneratedStudentExams()).toBe(7);
    });

    it('stops after two retries and restores the budget for a new event', () => {
        state.initialize(checklist(6, 5));
        state.started();
        for (let attempt = 0; attempt < 3; attempt++) {
            expect(fetchCounts).toHaveBeenCalledTimes(attempt + 1);
            fetchCounts.mock.results[attempt].value.error(new Error('offline'));
            vi.advanceTimersByTime(1000);
        }
        vi.advanceTimersByTime(10000);
        expect(fetchCounts).toHaveBeenCalledTimes(3);
        state.submitted();
        expect(fetchCounts).toHaveBeenCalledTimes(4);
        fetchCounts.mock.results[3].value.next(counts(7, 6, 3));
        expect(state.numberOfSubmitted()).toBe(3);
    });

    it.each(['reset', 'destroy'])('cancels a pending retry on %s', (action) => {
        state.initialize(checklist(6, 5));
        state.started();
        fetchCounts.mock.results[0].value.error(new Error('offline'));
        if (action === 'reset') {
            state.reset({ id: 2, testExam: false });
            state.initialize(checklist(1, 0, 0));
        } else {
            state.destroy();
        }
        vi.advanceTimersByTime(5000);
        expect(fetchCounts).toHaveBeenCalledTimes(1);
    });

    it('can recover counts when the initial checklist fails', () => {
        state.started();
        state.initialize();
        fetchCounts.mock.results[0].value.next(counts(8, 6));
        expect(state.numberOfStarted()).toBe(6);
        expect(state.numberOfSubmitted()).toBe(2);
    });

    it('unsubscribes in-flight requests on exam changes and ignores their stale responses', () => {
        state.initialize(checklist(6, 5));
        state.started();
        const previous = fetchCounts.mock.results[0].value;
        state.reset({ id: 2, testExam: true });
        expect(previous.observed).toBe(false);
        state.initialize(checklist(1, 0, 0));
        previous.next(counts(8, 7));
        expect(state.numberOfStarted()).toBe(0);
        expect(state.numberOfGeneratedStudentExams()).toBe(1);
    });

    it.each(['in-flight', 'scheduled'])('cancels %s work on destroy', (kind) => {
        state.initialize(checklist(6, 5));
        state.started();
        const request = fetchCounts.mock.results[0].value;
        state.started();
        if (kind === 'scheduled') {
            request.complete();
        }
        state.destroy();
        expect(request.observed).toBe(false);
        request.next(counts(100, 100));
        vi.advanceTimersByTime(5000);
        expect(fetchCounts).toHaveBeenCalledTimes(1);
        expect(state.numberOfGeneratedStudentExams()).toBe(6);
    });

    it.each(['snapshot-before-event', 'event-before-response'])('reconciles regular exam counters for %s', (ordering) => {
        const regularExam = { id: 1, testExam: false };
        state.reset(regularExam);
        if (ordering === 'snapshot-before-event') {
            // The snapshot already includes the persisted start whose notification arrives later.
            state.initialize(checklist(12, 4, 3));
            state.started();
            state.submitted();
        } else {
            state.started();
            state.submitted();
            state.initialize(checklist(12, 3, 2));
        }
        expect(fetchCounts).toHaveBeenCalledExactlyOnceWith(regularExam);
        fetchCounts.mock.results[0].value.next(counts(12, 4, 3));
        fetchCounts.mock.results[0].value.complete();
        vi.advanceTimersByTime(1000);
        if (fetchCounts.mock.results.length > 1) {
            fetchCounts.mock.results[1].value.next(counts(12, 4, 3));
            fetchCounts.mock.results[1].value.complete();
        }
        expect(state.numberOfGeneratedStudentExams()).toBe(12);
        expect(state.numberOfStarted()).toBe(4);
        expect(state.numberOfSubmitted()).toBe(3);
    });
});
