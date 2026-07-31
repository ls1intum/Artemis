import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ProblemStatementResultHydrationService } from 'app/programming/shared/instructions-render/ssr/problem-statement-result-hydration.service';
import { ResultService } from 'app/exercise/result/result.service';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { MockResultService } from 'test/helpers/mocks/service/mock-result.service';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';

describe('ProblemStatementResultHydrationService', () => {
    let service: ProblemStatementResultHydrationService;
    let resultService: ResultService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
            ],
        });
        service = TestBed.inject(ProblemStatementResultHydrationService);
        resultService = TestBed.inject(ResultService);
        programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);
    });

    afterEach(() => vi.clearAllMocks());

    describe('initialResult', () => {
        it('returns undefined when there is no participation', () => {
            let received: Result | undefined = { id: -1 };
            service.initialResult(undefined).subscribe((r) => (received = r));
            expect(received).toBeUndefined();
        });

        it('returns undefined when the participation has no id', () => {
            let received: Result | undefined = { id: -1 };
            service.initialResult({}).subscribe((r) => (received = r));
            expect(received).toBeUndefined();
        });

        it('returns the latest submitted result untouched when it already has feedbacks, without an HTTP call', () => {
            const getFeedbackDetailsSpy = vi.spyOn(resultService, 'getFeedbackDetailsForResult');
            const olderResult: Result = { id: 1, feedbacks: [{ testCase: { id: 1, testName: 'testA' }, positive: true }] };
            const latestResult: Result = { id: 2, feedbacks: [{ testCase: { id: 2, testName: 'testB' }, positive: false }] };
            const participation: Participation = { id: 5, submissions: [{ id: 10, results: [olderResult, latestResult] }] };

            let received: Result | undefined;
            service.initialResult(participation).subscribe((r) => (received = r));

            expect(received).toBe(latestResult);
            expect(getFeedbackDetailsSpy).not.toHaveBeenCalled();
        });

        it('fetches and attaches feedback details when the latest submitted result has none', () => {
            const latestResult: Result = { id: 2 };
            const participation: Participation = { id: 5, submissions: [{ id: 10, results: [latestResult] }] };
            const feedbacks: Feedback[] = [{ testCase: { id: 2, testName: 'testB' }, positive: true }];
            vi.spyOn(resultService, 'getFeedbackDetailsForResult').mockReturnValue(of(new HttpResponse({ body: feedbacks })));

            let received: Result | undefined;
            service.initialResult(participation).subscribe((r) => (received = r));

            expect(received).toBe(latestResult);
            expect(received?.feedbacks).toEqual(feedbacks);
        });

        it('uses getLatestResultWithFeedback when the participation has no submissions', () => {
            const latestResult: Result = { id: 7, feedbacks: [{ testCase: { id: 7, testName: 'testG' }, positive: true }] };
            const participation: Participation = { id: 5 };
            const getLatestResultSpy = vi.spyOn(programmingExerciseParticipationService, 'getLatestResultWithFeedback').mockReturnValue(of(latestResult));

            let received: Result | undefined;
            service.initialResult(participation).subscribe((r) => (received = r));

            expect(getLatestResultSpy).toHaveBeenCalledWith(5);
            expect(received).toBe(latestResult);
        });

        it('errors when getLatestResultWithFeedback fails, instead of emitting "no result"', () => {
            const participation: Participation = { id: 5 };
            vi.spyOn(programmingExerciseParticipationService, 'getLatestResultWithFeedback').mockReturnValue(throwError(() => new Error('fatal')));

            let errored: unknown;
            let emitted = false;
            service.initialResult(participation).subscribe({
                next: () => (emitted = true),
                error: (e) => (errored = e),
            });

            // A swallowed failure would emit undefined here, which downstream is indistinguishable from "this
            // participation has no result" and renders neutral task statuses instead of the load-failure banner.
            expect(emitted).toBe(false);
            expect(errored).toBeDefined();
        });

        it('errors when the feedback details of the fetched latest result cannot be loaded', () => {
            const participation: Participation = { id: 5 };
            vi.spyOn(programmingExerciseParticipationService, 'getLatestResultWithFeedback').mockReturnValue(of({ id: 7 }));
            vi.spyOn(resultService, 'getFeedbackDetailsForResult').mockReturnValue(throwError(() => new Error('fatal')));

            let errored: unknown;
            let emitted = false;
            service.initialResult(participation).subscribe({
                next: () => (emitted = true),
                error: (e) => (errored = e),
            });

            expect(emitted).toBe(false);
            expect(errored).toBeDefined();
        });
    });

    describe('withFeedbackDetails', () => {
        it('returns the result untouched when feedbacks are already loaded', () => {
            const getFeedbackDetailsSpy = vi.spyOn(resultService, 'getFeedbackDetailsForResult');
            const result: Result = { id: 1, feedbacks: [] };
            const participation: Participation = { id: 5 };

            let received: Result | undefined;
            service.withFeedbackDetails(participation, result).subscribe((r) => (received = r));

            expect(received).toBe(result);
            expect(getFeedbackDetailsSpy).not.toHaveBeenCalled();
        });

        it('fetches and attaches feedback details when missing', () => {
            const result: Result = { id: 1 };
            const participation: Participation = { id: 5 };
            const feedbacks: Feedback[] = [{ testCase: { id: 1, testName: 'testA' }, positive: true }];
            vi.spyOn(resultService, 'getFeedbackDetailsForResult').mockReturnValue(of(new HttpResponse({ body: feedbacks })));

            let received: Result | undefined;
            service.withFeedbackDetails(participation, result).subscribe((r) => (received = r));

            expect(received).toBe(result);
            expect(received?.feedbacks).toEqual(feedbacks);
        });

        it('errors when the details fetch fails, instead of emitting a result with unloaded feedbacks', () => {
            const result: Result = { id: 1 };
            const participation: Participation = { id: 5 };
            vi.spyOn(resultService, 'getFeedbackDetailsForResult').mockReturnValue(throwError(() => new Error('fatal')));

            let errored: unknown;
            let received: Result | undefined;
            service.withFeedbackDetails(participation, result).subscribe({
                next: (r) => (received = r),
                error: (e) => (errored = e),
            });

            expect(received).toBeUndefined();
            expect(errored).toBeDefined();
        });
    });
});
