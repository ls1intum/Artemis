import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProblemStatementSsrRenderService } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr-render.service';
import { RenderedProblemStatement } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';

describe('ProblemStatementSsrRenderService', () => {
    let service: ProblemStatementSsrRenderService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), ProblemStatementSsrRenderService] });
        service = TestBed.inject(ProblemStatementSsrRenderService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    describe('mapFeedbacksToTestInputs', () => {
        it('returns undefined when there is no result', () => {
            expect(service.mapFeedbacksToTestInputs(undefined)).toBeUndefined();
        });

        it('returns undefined when the result has no feedbacks', () => {
            expect(service.mapFeedbacksToTestInputs({ id: 1, feedbacks: [] } as Result)).toBeUndefined();
        });

        it('returns undefined for a successful result without feedbacks, whose outcome travels in allTestsPassed instead', () => {
            // `successful: true` with no feedbacks is the legacy renderer's "all tests passed" case
            // (ProgrammingExerciseInstructionService.testStatusForTask, case 1): every task renders GREEN. There is
            // no feedback to map here, so this mapping cannot express it; the caller sends `allTestsPassed: true`
            // alongside the resulting `testResults: null` and the server colours the tasks from that flag.
            const result = { id: 1, successful: true, feedbacks: [] } as Result;

            expect(service.mapFeedbacksToTestInputs(result)).toBeUndefined();
        });

        it('returns an empty array when feedbacks exist but none map to a test case', () => {
            const result = { id: 1, feedbacks: [{ text: 'SCA issue', positive: false }] } as Result;
            expect(service.mapFeedbacksToTestInputs(result)).toEqual([]);
        });

        it('maps positive true/false/undefined to tri-state passed', () => {
            const result = {
                id: 1,
                feedbacks: [
                    { testCase: { id: 1, testName: 'testA' }, positive: true },
                    { testCase: { id: 2, testName: 'testB' }, positive: false },
                    { testCase: { id: 3, testName: 'testC' }, positive: undefined },
                ],
            } as Result;
            expect(service.mapFeedbacksToTestInputs(result)).toEqual([
                { testId: 1, testName: 'testA', passed: true },
                { testId: 2, testName: 'testB', passed: false },
                { testId: 3, testName: 'testC', passed: null },
            ]);
        });

        it('keeps the first entry when a test case id appears twice', () => {
            const result = {
                id: 1,
                feedbacks: [
                    { testCase: { id: 1, testName: 'testA' }, positive: true },
                    { testCase: { id: 1, testName: 'testA' }, positive: false },
                ],
            } as Result;
            expect(service.mapFeedbacksToTestInputs(result)).toEqual([{ testId: 1, testName: 'testA', passed: true }]);
        });

        it('skips feedback without a test case id or name', () => {
            const result = {
                id: 1,
                feedbacks: [{ testCase: { id: undefined, testName: 'noId' }, positive: true }, { testCase: { id: 5 }, positive: true }, { positive: true }],
            } as Result;
            expect(service.mapFeedbacksToTestInputs(result)).toEqual([]);
        });
    });

    describe('render', () => {
        it('posts the request and returns the rendered document', () => {
            let received: string | undefined;
            service.render({ markdown: '# Hi', testResults: undefined, allTestsPassed: false, locale: 'en', darkMode: false }).subscribe((r) => (received = r.html));

            const req = httpMock.expectOne((r) => r.url.endsWith('exercise/problem-statement/render'));
            expect(req.request.method).toBe('POST');
            expect(req.request.body.testResults).toBeNull();
            expect(req.request.body.includeJs).toBe(false);
            expect(req.request.body.includeCss).toBe(true);
            expect(req.request.body.inlineImages).toBe(false);
            expect(req.request.body.allTestsPassed).toBe(false);
            req.flush({ html: '<p>Hi</p>', contentHash: 'abc', rendererVersion: '1.1.0' });

            expect(received).toBe('<p>Hi</p>');
        });

        it('sends the all-tests-passed signal to the server', () => {
            service.render({ markdown: '# Hi', testResults: undefined, allTestsPassed: true, locale: 'en', darkMode: false }).subscribe();

            const req = httpMock.expectOne((r) => r.url.endsWith('exercise/problem-statement/render'));
            expect(req.request.body.allTestsPassed).toBe(true);
            expect(req.request.body.testResults).toBeNull();
            req.flush({ html: '<p>Hi</p>', contentHash: 'abc', rendererVersion: '1.1.0' });
        });

        it('distinguishes all-tests-passed from no result in the cache key', () => {
            // Both requests send `testResults: null`; only the flag decides between all-green and neutral tasks, so
            // they must not collide on one cache entry.
            service.render({ markdown: '# Hi', testResults: undefined, allTestsPassed: true, locale: 'en', darkMode: false }).subscribe();
            httpMock.expectOne((r) => r.url.endsWith('exercise/problem-statement/render')).flush({ html: '<p>green</p>', contentHash: 'g', rendererVersion: '1.1.0' });

            let second: string | undefined;
            service.render({ markdown: '# Hi', testResults: undefined, allTestsPassed: false, locale: 'en', darkMode: false }).subscribe((r) => (second = r.html));
            httpMock.expectOne((r) => r.url.endsWith('exercise/problem-statement/render')).flush({ html: '<p>neutral</p>', contentHash: 'n', rendererVersion: '1.1.0' });

            expect(second).toBe('<p>neutral</p>');
        });

        it('serves a repeated identical request from cache without a second request', () => {
            const request = { markdown: '# Hi', testResults: undefined, allTestsPassed: false, locale: 'en', darkMode: false };
            service.render(request).subscribe();
            httpMock.expectOne((r) => r.url.endsWith('exercise/problem-statement/render')).flush({ html: '<p>Hi</p>', contentHash: 'abc', rendererVersion: '1.1.0' });

            let second: string | undefined;
            service.render(request).subscribe((r) => (second = r.html));
            httpMock.expectNone((r) => r.url.endsWith('exercise/problem-statement/render'));
            expect(second).toBe('<p>Hi</p>');
        });

        it('distinguishes an empty test result list from no test results in the cache key', () => {
            service.render({ markdown: '# Hi', testResults: [], allTestsPassed: false, locale: 'en', darkMode: false }).subscribe();
            const first = httpMock.expectOne((r) => r.url.endsWith('exercise/problem-statement/render'));
            expect(first.request.body.testResults).toEqual([]);
            first.flush({ html: '<p>empty</p>', contentHash: 'e', rendererVersion: '1.1.0' });

            service.render({ markdown: '# Hi', testResults: undefined, allTestsPassed: false, locale: 'en', darkMode: false }).subscribe();
            const second = httpMock.expectOne((r) => r.url.endsWith('exercise/problem-statement/render'));
            expect(second.request.body.testResults).toBeNull();
            second.flush({ html: '<p>none</p>', contentHash: 'n', rendererVersion: '1.1.0' });
        });

        it('defaults html to an empty string when the server omits it for a blank rendering', () => {
            let received: RenderedProblemStatement | undefined;
            service.render({ markdown: '', testResults: undefined, allTestsPassed: false, locale: 'en', darkMode: false }).subscribe((r) => (received = r));

            const req = httpMock.expectOne((r) => r.url.endsWith('exercise/problem-statement/render'));
            // The server serializes with @JsonInclude(NON_EMPTY), so a blank rendering omits `html` from the JSON entirely.
            req.flush({ contentHash: 'empty', rendererVersion: '1.1.0' });

            expect(received?.html).toBe('');
        });

        it('retries a transient failure once instead of putting a banner in front of the reader', () => {
            vi.useFakeTimers();
            try {
                let received: string | undefined;
                service.render({ markdown: '# Hi', testResults: undefined, allTestsPassed: false, locale: 'en', darkMode: false }).subscribe((r) => (received = r.html));

                httpMock.expectOne((r) => r.url.endsWith('exercise/problem-statement/render')).flush('boom', { status: 503, statusText: 'Service Unavailable' });
                vi.advanceTimersByTime(300);
                httpMock.expectOne((r) => r.url.endsWith('exercise/problem-statement/render')).flush({ html: '<p>Hi</p>', contentHash: 'abc', rendererVersion: '1.1.0' });

                expect(received).toBe('<p>Hi</p>');
            } finally {
                vi.useRealTimers();
            }
        });

        it.each([
            [429, 'Too Many Requests'],
            [422, 'Unprocessable Content'],
        ])('does not retry a %i, which a second identical request cannot change', (status, statusText) => {
            let failedWith: number | undefined;
            service
                .render({ markdown: '# Hi', testResults: undefined, allTestsPassed: false, locale: 'en', darkMode: false })
                .subscribe({ error: (error) => (failedWith = error.status) });

            httpMock.expectOne((r) => r.url.endsWith('exercise/problem-statement/render')).flush('nope', { status, statusText });

            // The afterEach verify() is what proves no second request was issued.
            expect(failedWith).toBe(status);
        });

        it('does not cache a failed render', () => {
            const request = { markdown: '# Hi', testResults: undefined, allTestsPassed: false, locale: 'en', darkMode: false };
            service.render(request).subscribe({ error: () => {} });
            httpMock.expectOne((r) => r.url.endsWith('exercise/problem-statement/render')).flush('nope', { status: 422, statusText: 'Unprocessable Content' });

            service.render(request).subscribe({ error: () => {} });
            httpMock.expectOne((r) => r.url.endsWith('exercise/problem-statement/render')).flush('nope', { status: 422, statusText: 'Unprocessable Content' });
        });
    });
});
