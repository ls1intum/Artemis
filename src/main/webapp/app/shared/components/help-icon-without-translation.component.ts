import { Component, Input } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-help-icon-without-translation',
    template: ` <fa-icon [icon]="faQuestionCircle" class="text-secondary" [placement]="placement" ngbTooltip="{{ text }}"></fa-icon> `,
})
export class HelpIconComponentWithoutTranslationComponent {
    @Input() placement: string;
    @Input() text: string;

    // Icons
    faQuestionCircle = faQuestionCircle;
}
