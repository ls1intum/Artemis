import { DOCUMENT } from '@angular/common';
import { Directive, Injector, afterNextRender, inject } from '@angular/core';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';

/**
 * Keeps the option that the arrow keys activate in an ng-bootstrap typeahead visible.
 * NgbTypeahead moves the active option but never scrolls it into view, so in a dropdown with a
 * max-height, or one that reaches below the edge of a modal, the active option leaves the visible area.
 * Options activated with the pointer are already visible and are not scrolled.
 */
@Directive({
    selector: 'input[ngbTypeahead][jhiTypeaheadScrollActiveOption]',
    host: { '(keydown)': 'scrollActiveOptionIntoView($event)' },
})
export class TypeaheadScrollActiveOptionDirective {
    private readonly typeahead = inject(NgbTypeahead);
    private readonly document = inject(DOCUMENT);
    private readonly injector = inject(Injector);

    protected scrollActiveOptionIntoView(event: KeyboardEvent): void {
        if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') {
            return;
        }
        // NgbTypeahead handles the same keydown; wait for it and for the render so that the active option is known and marked.
        afterNextRender(
            () => {
                const activeOptionId = this.typeahead.activeDescendant;
                if (activeOptionId) {
                    this.document.getElementById(activeOptionId)?.scrollIntoView({ block: 'nearest' });
                }
            },
            { injector: this.injector },
        );
    }
}
