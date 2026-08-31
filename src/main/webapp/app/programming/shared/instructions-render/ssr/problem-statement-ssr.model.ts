/**
 * Wire contract of the stateless problem-statement rendering endpoint.
 * Mirrors ProblemStatementRenderRequestDTO / RenderedProblemStatementDTO on the server.
 */

/**
 * A single test result. `passed: null` means the test is known but was not executed.
 *
 * `message` and `credits` are part of the endpoint's request contract: the server collects them into the document's
 * `data-feedback` attribute, which it writes once on the statement container for its interactive script to read, and
 * which each task references by test id. They stay declared even though this client never populates them: it renders
 * with `includeJs: false` and opens the Artemis feedback dialog instead.
 */
export interface TestFeedbackInput {
    testId: number;
    testName: string;
    passed: boolean | null;
    message?: string;
    credits?: number;
}

export interface ProblemStatementRenderRequest {
    markdown: string;
    /** `undefined` is sent as `null` (no result at all); `[]` means a result exists but carries no mappable test feedback. */
    testResults: TestFeedbackInput[] | undefined;
    /**
     * A successful result that carries no feedback at all: every test passed. This cannot be expressed through
     * `testResults`, which would be `undefined` ("no result") in exactly that case. The server only honours the flag
     * when `testResults` is `undefined`; individual test outcomes always win over it.
     */
    allTestsPassed: boolean;
    locale: string;
    darkMode: boolean;
}

export interface RenderedProblemStatement {
    html: string;
    contentHash: string;
    rendererVersion: string;
}

/**
 * The exact `data-test-status` values the server renderer emits
 * (`ProblemStatementRenderingService.computeTaskTestStatus`). Typed as a union so a template branch or an i18n key
 * built from a misspelled status is a compile error rather than a silently grey circle and a missing translation.
 */
export type SsrTaskStatus = 'success' | 'fail' | 'not-executed' | 'no-result' | 'no-tests';

export const SSR_TASK_STATUSES: readonly SsrTaskStatus[] = ['success', 'fail', 'not-executed', 'no-result', 'no-tests'];

/** A task parsed from the server-rendered markup, built purely from the `data-*` metadata the server emits. */
export interface SsrTask {
    /** Position in document order. Task names are not unique, so the index identifies a task. */
    index: number;
    taskName: string;
    testIds: number[];
    status: SsrTaskStatus;
    authoredCount: number;
    notExecutedCount: number;
}
