import { Component, input } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-help-icon',
    template: ` <fa-icon [icon]="faQuestionCircle" class="text-secondary" [placement]="placement()" [ngbTooltip]="text() | artemisTranslate" container="body" /> `,
    imports: [FaIconComponent, NgbTooltip, ArtemisTranslatePipe],
})
export class HelpIconComponent {
    protected readonly faQuestionCircle = faQuestionCircle;

    placement = input<string>('auto');
    text = input.required<string>();
}
