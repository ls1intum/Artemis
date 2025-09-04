import { Component, input } from '@angular/core';
import { CourseTutorialGroupDetailSessionStatusChipComponent } from 'app/tutorialgroup/overview/tutorial-group-detail-session-status-chip/course-tutorial-group-detail-session-status-chip.component';

@Component({
    selector: 'jhi-tutorial-group-detail-session-status-indicator',
    imports: [CourseTutorialGroupDetailSessionStatusChipComponent],
    templateUrl: './tutorial-group-detail-session-status-indicator.component.html',
    styleUrl: './tutorial-group-detail-session-status-indicator.component.scss',
})
export class TutorialGroupDetailSessionStatusIndicatorComponent {
    isCancelled = input.required<boolean>();
    locationChanged = input.required<boolean>();
    timeChanged = input.required<boolean>();
    dateChanged = input.required<boolean>();
}
