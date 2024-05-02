import { Component, Input } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

const DEFAULT_PLACEMENT = 'top';

@Component({
    selector: 'jhi-help-icon-without-translation',
    template: ` <fa-icon [icon]="faQuestionCircle" class="text-secondary" [placement]="placement" ngbTooltip="{{ text }}" /> `,
})
export class HelpIconComponentWithoutTranslationComponent {
    @Input() placement: string = DEFAULT_PLACEMENT;
    @Input() text: string;

    // Icons
    faQuestionCircle = faQuestionCircle;
}
