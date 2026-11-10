import { Directive } from '@angular/core';

@Directive({
    selector: '[jhiPresentationLabel]',
    host: { class: 'text-xs tracking-wider text-(--text-body-secondary) uppercase' },
})
export class PresentationLabelDirective {}
