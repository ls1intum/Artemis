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

    it('retains pending work after an error and recovers on later events', () => {
        state.initialize(checklist(6, 5));
        state.started();
        state.started();
        fetchCounts.mock.results[0].value.error(new Error('offline'));
        vi.advanceTimersByTime(1000);
        expect(fetchCounts).toHaveBeenCalledTimes(2);
        fetchCounts.mock.results[1].value.error(new Error('offline'));
        vi.advanceTimersByTime(1000);
        expect(fetchCounts).toHaveBeenCalledTimes(2);
        state.submitted();
        fetchCounts.mock.results[2].value.next(counts(8, 7, 3));
        fetchCounts.mock.results[2].value.complete();
        expect(state.numberOfStarted()).toBe(7);
        expect(state.numberOfSubmitted()).toBe(3);
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

    it('leaves regular exam totals unchanged and never refreshes on events', () => {
        state.reset({ id: 1, testExam: false });
        state.initialize(checklist(12, 3));
        state.started();
        state.submitted();
        expect(state.numberOfGeneratedStudentExams()).toBe(12);
        expect(state.numberOfStarted()).toBe(4);
        expect(state.numberOfSubmitted()).toBe(3);
        expect(fetchCounts).not.toHaveBeenCalled();
    });
});
