import { EntitySearchSource } from 'app/core/navbar/global-search/models/entity-search-source.model';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';

/**
 * WebSocket message pushed by Artemis during an async lecture-search/ask-Iris request.
 *
 * - `isThinking: true`  → Pyris classified the query as a real question; LLM is running. Show thinking animation,
 *   or the streamed `partialResult` draft once it starts arriving.
 * - `isThinking: false` → Pipeline done. Show `answer` card if non-null, hide everything otherwise.
 */
export interface IrisSearchStatusUpdate {
    runId: string;
    isThinking: boolean;
    answer?: string;
    sources?: LectureSearchResult[];
    /** Entity sources (course information) the answer drew on, numbered after `sources`. */
    entitySources?: EntitySearchSource[];
    /** Streamed draft of the answer so far (`isThinking: true` updates while the LLM generates). */
    partialResult?: string;
    /** Monotonic sequence number of the streamed draft; lower numbers are stale. */
    partialSeq?: number;
    /** The provider is retrying and the streamed draft so far is stale; discard it and wait for the next one. */
    clearDraft?: boolean;
    /** A genuine Pyris-side failure, distinct from a successful run with no relevant answer. */
    failed?: boolean;
}
