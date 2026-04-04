import { Component, input } from '@angular/core';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';

export type CourseTutorialGroupDetailSessionStatusChipVariant = 'REGULAR' | 'CANCELLED' | 'RESCHEDULED' | 'RELOCATED';

@Component({
    selector: 'jhi-tutorial-group-detail-session-status-chip',
    imports: [TranslateDirective, NgClass],
    templateUrl: './tutorial-group-detail-session-status-chip.component.html',
    styleUrl: './tutorial-group-detail-session-status-chip.component.scss',
})
export class TutorialGroupDetailSessionStatusChipComponent {
    variant = input.required<CourseTutorialGroupDetailSessionStatusChipVariant>();
}
