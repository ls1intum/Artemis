import { Component, Input } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-help-icon',
    template: ` <fa-icon [icon]="faQuestionCircle" class="text-secondary" [placement]="placement" container="body" ngbTooltip="{{ text | artemisTranslate }}"></fa-icon> `,
})
export class HelpIconComponent {
    @Input() placement = 'auto';
    @Input() text: string;

    // Icons
    faQuestionCircle = faQuestionCircle;
}
