import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, map, of, retry, tap, throwError, timer } from 'rxjs';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ProblemStatementRenderRequest, RenderedProblemStatement, TestFeedbackInput } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr.model';

const RENDER_URL = 'api/exercise/problem-statement/render';
const CACHE_LIMIT = 10;
const RETRY_DELAY_MS = 300;

/**
 * Whether a second attempt could plausibly succeed: the request never reached the server, or the server failed in a
 * way it may not fail again. A 429 is excluded on purpose, retrying it spends the very budget it complains about, and
 * so is a 422, which is a deterministic rejection of this exact request.
 */
function isTransient(error: unknown): boolean {
    return error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);
}

@Injectable({ providedIn: 'root' })
export class ProblemStatementSsrRenderService {
    private http = inject(HttpClient);

    private cache = new Map<string, RenderedProblemStatement>();

    /**
     * Maps the feedbacks of a result to the endpoint's test-result inputs.
     *
     * Returns `undefined` when no result exists or the result carries no feedback at all: neither can map a single
     * test. A *successful* result without feedback means every test passed, which the caller carries separately in
     * `allTestsPassed` (this mapping has no way to express it). Returns an empty array when feedback exists but none
     * of it belongs to a test case (e.g. only static-code-analysis feedback), which renders as "not executed".
     * Callers must load feedback details before calling this; an unloaded `feedbacks` array is indistinguishable
     * from an empty one here.
     */
    mapFeedbacksToTestInputs(result: Result | undefined): TestFeedbackInput[] | undefined {
        const feedbacks = result?.feedbacks;
        if (!feedbacks?.length) {
            return undefined;
        }
        const byId = new Map<number, TestFeedbackInput>();
        for (const feedback of feedbacks) {
            const testId = feedback.testCase?.id;
            const testName = feedback.testCase?.testName;
            if (testId === undefined || !testName) {
                continue;
            }
            if (byId.has(testId)) {
                // The legacy renderer uses the first matching feedback per test case, so we keep first-wins.
                continue;
            }
            const positive = feedback.positive;
            byId.set(testId, { testId, testName, passed: positive === true ? true : positive === false ? false : null });
        }
        return [...byId.values()].sort((a, b) => a.testId - b.testId);
    }

    /**
     * Renders the given problem statement server-side, caching identical requests.
     */
    render(request: ProblemStatementRenderRequest): Observable<RenderedProblemStatement> {
        const key = this.cacheKey(request);
        const cached = this.cache.get(key);
        if (cached) {
            return of(cached);
        }
        const body = {
            markdown: request.markdown,
            testResults: request.testResults ?? null,
            allTestsPassed: request.allTestsPassed,
            locale: request.locale,
            darkMode: request.darkMode,
            includeJs: false,
            includeCss: true,
            inlineImages: false,
        };
        return this.http.post<RenderedProblemStatement>(RENDER_URL, body).pipe(
            // One retry for a transient failure, as ProgrammingExercisePlantUmlService does for the diagram of the
            // legacy renderer next door. A single hiccup should not put a failure banner in front of the reader.
            retry({ count: 1, delay: (error) => (isTransient(error) ? timer(RETRY_DELAY_MS) : throwError(() => error)) }),
            // The server serializes with NON_EMPTY, so an empty rendering omits `html` entirely.
            // Built field by field rather than with object spread, per the client guideline on copying objects.
            map((rendered) => ({ html: rendered.html ?? '', contentHash: rendered.contentHash, rendererVersion: rendered.rendererVersion })),
            tap((rendered) => this.putInCache(key, rendered)),
        );
    }

    private cacheKey(request: ProblemStatementRenderRequest): string {
        // `undefined` and `[]` must produce different keys: they render differently (no-result vs not-executed).
        // JSON.stringify serializes the former as `null`, so the distinction survives.
        // Sorted by test id (not just concatenated in input order) so two logically equal requests whose test
        // results arrive in a different order still produce the same key.
        const tests = request.testResults === undefined ? undefined : [...request.testResults].sort((a, b) => a.testId - b.testId).map((t) => [t.testId, t.testName, t.passed]);
        // Structured rather than delimiter-joined: a test name or a markdown body containing the delimiter could
        // otherwise shift the field boundaries and make two different requests collide on one cache entry.
        // `allTestsPassed` is part of the key: with the same (absent) test results it decides between all-green and
        // neutral tasks, so leaving it out would serve one rendering for two different requests.
        return JSON.stringify([request.locale, request.darkMode, request.allTestsPassed, tests, request.markdown]);
    }

    /** FIFO eviction: insertion order is not refreshed on a hit, which is sufficient for this small cache. */
    private putInCache(key: string, rendered: RenderedProblemStatement): void {
        if (this.cache.size >= CACHE_LIMIT) {
            const oldest = this.cache.keys().next().value;
            if (oldest !== undefined) {
                this.cache.delete(oldest);
            }
        }
        this.cache.set(key, rendered);
    }
}
