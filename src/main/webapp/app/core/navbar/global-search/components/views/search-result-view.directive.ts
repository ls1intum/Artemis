import { Directive, Signal } from '@angular/core';

@Directive()
export abstract class SearchResultView {
    abstract readonly itemCount: Signal<number>;
}
