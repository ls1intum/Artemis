import { Directive, Signal } from '@angular/core';

/**
 * Debounce time in milliseconds for search input
 */
export const SEARCH_DEBOUNCE_MS = 300;

/**
 * Minimum number of characters required before a search request is sent to the server.
 * Weaviate uses trigram tokenization (3-char sliding windows), so queries shorter than
 * this produce zero trigrams and yield no BM25 results.
 */
export const MIN_SEARCH_QUERY_LENGTH = 3;

/**
 * Maximum query length considered "short". Queries between {@link MIN_SEARCH_QUERY_LENGTH}
 * and this value may produce poor results due to few trigrams, so a hint is shown.
 */
export const SHORT_QUERY_MAX_LENGTH = 5;

/**
 * Base class for search result view components that report their item count
 * and handle selection to the modal.
 */
@Directive()
export abstract class SearchResultView {
    /**
     * Total number of selectable items in this view.
     * Used by the modal to bound ArrowDown/ArrowUp navigation.
     */
    abstract readonly itemCount: Signal<number>;
}
