/**
 * Wire contract of the stateless problem-statement rendering endpoint.
 * Mirrors ProblemStatementRenderRequestDTO / RenderedProblemStatementDTO on the server.
 */

/** A single test result. `passed: null` means the test is known but was not executed. */
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
    locale: string;
    darkMode: boolean;
}

export interface RenderedProblemStatement {
    html: string;
    contentHash: string;
    rendererVersion: string;
}

/** A task parsed from the server-rendered markup, built purely from the `data-*` metadata the server emits. */
export interface SsrTask {
    /** Position in document order. Task names are not unique, so the index identifies a task. */
    index: number;
    taskName: string;
    testIds: number[];
    status: string;
    authoredCount: number;
    notExecutedCount: number;
}
