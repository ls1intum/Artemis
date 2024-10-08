import { Component, input } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-help-icon',
    template: ` <fa-icon [icon]="faQuestionCircle" class="text-secondary" [placement]="placement()" ngbTooltip="{{ text() | artemisTranslate }}" /> `,
})
export class HelpIconComponent {
    protected readonly faQuestionCircle = faQuestionCircle;

    placement = input<string>('auto');
    text = input.required<string>();
}
