import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-help-icon',
    template: ` <fa-icon [icon]="'question-circle'" class="text-secondary" [placement]="placement" ngbTooltip="{{ text | translate }}"></fa-icon> `,
})
export class HelpIconComponent {
    @Input() placement: string;
    @Input() text: string;
}
