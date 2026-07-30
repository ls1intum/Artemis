import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, of, tap } from 'rxjs';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ProblemStatementRenderRequest, RenderedProblemStatement, TestFeedbackInput } from 'app/programming/shared/instructions-render/ssr/problem-statement-ssr.model';

const RENDER_URL = 'api/exercise/problem-statement/render';
const CACHE_LIMIT = 10;

@Injectable({ providedIn: 'root' })
export class ProblemStatementSsrRenderService {
    private http = inject(HttpClient);

    private cache = new Map<string, RenderedProblemStatement>();

    /**
     * Maps the feedbacks of a result to the endpoint's test-result inputs.
     *
     * Returns `undefined` when no result exists or the result carries no feedback at all: both must render as
     * "no result", matching the legacy client renderer. Returns an empty array when feedback exists but none of it
     * belongs to a test case (e.g. only static-code-analysis feedback), which renders as "not executed".
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
            locale: request.locale,
            darkMode: request.darkMode,
            includeJs: false,
            includeCss: true,
            inlineImages: false,
        };
        return this.http.post<RenderedProblemStatement>(RENDER_URL, body).pipe(
            // The server serializes with NON_EMPTY, so an empty rendering omits `html` entirely.
            // Built field by field rather than with object spread, per the client guideline on copying objects.
            map((rendered) => ({ html: rendered.html ?? '', contentHash: rendered.contentHash, rendererVersion: rendered.rendererVersion })),
            tap((rendered) => this.putInCache(key, rendered)),
        );
    }

    private cacheKey(request: ProblemStatementRenderRequest): string {
        // `null` and `[]` must produce different keys: they render differently (no-result vs not-executed).
        // Sorted by test id (not just concatenated in input order) so two logically equal requests whose test
        // results arrive in a different order still produce the same key.
        const tests =
            request.testResults === undefined
                ? 'none'
                : [...request.testResults]
                      .sort((a, b) => a.testId - b.testId)
                      .map((t) => `${t.testId}:${t.testName}:${t.passed}`)
                      .join('|');
        return `${request.locale}|${request.darkMode}|${tests}|${request.markdown}`;
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
