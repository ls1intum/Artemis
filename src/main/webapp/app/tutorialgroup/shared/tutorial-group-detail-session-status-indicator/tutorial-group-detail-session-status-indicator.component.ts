import { Component, input } from '@angular/core';
import { TutorialGroupDetailSessionStatusChipComponent } from 'app/tutorialgroup/shared/tutorial-group-detail-session-status-chip/tutorial-group-detail-session-status-chip.component';

@Component({
    selector: 'jhi-course-tutorial-group-detail-session-status-indicator',
    imports: [TutorialGroupDetailSessionStatusChipComponent],
    templateUrl: './tutorial-group-detail-session-status-indicator.component.html',
    styleUrl: './tutorial-group-detail-session-status-indicator.component.scss',
})
export class TutorialGroupDetailSessionStatusIndicatorComponent {
    isCancelled = input.required<boolean>();
    locationChanged = input.required<boolean>();
    timeChanged = input.required<boolean>();
    dateChanged = input.required<boolean>();
}
