import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-help-icon-without-translation',
    template: ` <fa-icon [icon]="'question-circle'" class="text-secondary" [placement]="placement" ngbTooltip="{{ text }}"></fa-icon> `,
})
export class HelpIconComponentWithoutTranslationComponent {
    @Input() placement: string;
    @Input() text: string;
}
