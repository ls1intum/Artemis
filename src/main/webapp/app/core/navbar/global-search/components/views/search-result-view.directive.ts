import { Directive, Signal } from '@angular/core';

export const SEARCH_DEBOUNCE_MS = 300;

@Directive()
export abstract class SearchResultView {
    abstract readonly itemCount: Signal<number>;
}
