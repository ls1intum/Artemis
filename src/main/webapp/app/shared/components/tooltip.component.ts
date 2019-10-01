import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-tooltip',
    template: `
        <fa-icon icon="question-circle" class="text-secondary" [placement]="placement" ngbTooltip="{{ text | translate }}"></fa-icon>
    `,
})
export class TooltipComponent {
    @Input() placement: string;
    @Input() text: string;
}
