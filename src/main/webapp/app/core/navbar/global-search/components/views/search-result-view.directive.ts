import { Directive, Signal } from '@angular/core';

/**
 * Debounce time in milliseconds for search input
 */
export const SEARCH_DEBOUNCE_MS = 300;

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
