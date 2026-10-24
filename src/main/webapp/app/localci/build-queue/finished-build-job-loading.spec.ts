import { signal } from '@angular/core';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { UI_RELOAD_TIME } from 'app/foundation/constants/exercise-exam-constants';
import { FinishedBuildJob } from 'app/localci/shared/entities/build-job.model';
import { subscribeToFinishedBuildJobSearch, subscribeToFinishedBuildJobs } from './finished-build-job-loading';

describe('finished build job loading', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('debounces search before loading and preserves pagination headers with empty bodies', () => {
        const trigger = new Subject<void>();
        const response = new Subject<HttpResponse<FinishedBuildJob[]>>();
        const loading = signal(false);
        let fetches = 0;
        let jobs: FinishedBuildJob[] | undefined;
        let hasMore: string | null = null;
        const subscription = subscribeToFinishedBuildJobSearch(
            trigger,
            () => {
                fetches++;
                return response;
            },
            {
                loading,
                apply: (result, headers) => {
                    jobs = result;
                    hasMore = headers.get('x-has-next');
                },
                error: () => {},
            },
        );

        trigger.next();
        vi.advanceTimersByTime(UI_RELOAD_TIME - 1);
        expect(fetches).toBe(0);
        expect(loading()).toBe(false);
        trigger.next();
        vi.advanceTimersByTime(UI_RELOAD_TIME);
        expect(fetches).toBe(1);
        expect(loading()).toBe(true);
        response.next(new HttpResponse<FinishedBuildJob[]>({ body: null, headers: new HttpHeaders({ 'x-has-next': 'true' }) }));
        expect(jobs).toEqual([]);
        expect(hasMore).toBe('true');
        expect(loading()).toBe(false);
        subscription.unsubscribe();
    });

    it('cancels an older search so its response cannot replace the requested results', () => {
        const trigger = new Subject<void>();
        const first = new Subject<HttpResponse<FinishedBuildJob[]>>();
        const second = new Subject<HttpResponse<FinishedBuildJob[]>>();
        const responses = [first, second];
        const loading = signal(false);
        let hasMore: string | null = null;
        const subscription = subscribeToFinishedBuildJobSearch(trigger, () => responses.shift()!, {
            loading,
            apply: (_result, headers) => {
                hasMore = headers.get('x-has-next');
            },
            error: () => {},
        });
        trigger.next();
        vi.advanceTimersByTime(UI_RELOAD_TIME);
        expect(first.observed).toBe(true);
        trigger.next();
        vi.advanceTimersByTime(UI_RELOAD_TIME);
        expect(first.observed).toBe(false);
        first.next(new HttpResponse({ body: [], headers: new HttpHeaders({ 'x-has-next': 'stale' }) }));
        expect(hasMore).toBeNull();
        expect(loading()).toBe(true);
        second.next(new HttpResponse({ body: [], headers: new HttpHeaders({ 'x-has-next': 'false' }) }));
        expect(hasMore).toBe('false');
        expect(loading()).toBe(false);
        subscription.unsubscribe();
    });

    it('clears loading and reports a failed initial request without replacing existing jobs', () => {
        const response = new Subject<HttpResponse<FinishedBuildJob[]>>();
        const loading = signal(true);
        const failures: unknown[] = [];
        let applied = false;
        subscribeToFinishedBuildJobs(response, {
            loading,
            apply: () => {
                applied = true;
            },
            error: (error) => failures.push(error),
        });
        const failure = new Error('request failed');
        response.error(failure);
        expect(loading()).toBe(false);
        expect(applied).toBe(false);
        expect(failures).toEqual([failure]);
    });

    it('unsubscribes a pending debounce without starting a request', () => {
        const trigger = new Subject<void>();
        let fetches = 0;
        const subscription = subscribeToFinishedBuildJobSearch(
            trigger,
            () => {
                fetches++;
                return new Subject<HttpResponse<FinishedBuildJob[]>>();
            },
            {
                loading: signal(false),
                apply: () => {},
                error: () => {},
            },
        );
        trigger.next();
        subscription.unsubscribe();
        vi.advanceTimersByTime(UI_RELOAD_TIME);
        expect(fetches).toBe(0);
        expect(trigger.observed).toBe(false);
    });
});
