import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';

/**
 * WebSocket message pushed by Artemis during an async lecture-search/ask-Iris request.
 *
 * - `isThinking: true`  → Pyris classified the query as a real question; LLM is running. Show thinking animation.
 * - `isThinking: false` → Pipeline done. Show `answer` card if non-null, hide everything otherwise.
 */
export interface IrisSearchStatusUpdate {
    runId: string;
    isThinking: boolean;
    answer?: string;
    sources?: LectureSearchResult[];
}
