import { GlobalSearchSource } from 'app/core/navbar/global-search/models/global-search-source.model';

/**
 * WebSocket message pushed by Artemis during an async Iris global search request.
 *
 * - `isThinking: true`  → Pyris classified the query as a real question; LLM is running. Show thinking animation.
 * - `isThinking: false` → Pipeline done. Show `answer` card if non-null, hide everything otherwise.
 */
export interface IrisSearchStatusUpdate {
    runId: string;
    isThinking: boolean;
    answer?: string;
    sources?: GlobalSearchSource[];
}
