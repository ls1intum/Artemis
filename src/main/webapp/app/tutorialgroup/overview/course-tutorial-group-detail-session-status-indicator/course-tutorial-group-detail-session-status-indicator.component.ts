import { Component, input } from '@angular/core';
import { CourseTutorialGroupDetailSessionStatusChipComponent } from 'app/tutorialgroup/overview/course-tutorial-group-detail-session-status-chip/course-tutorial-group-detail-session-status-chip.component';

@Component({
    selector: 'jhi-course-tutorial-group-detail-session-status-indicator',
    imports: [CourseTutorialGroupDetailSessionStatusChipComponent],
    templateUrl: './course-tutorial-group-detail-session-status-indicator.component.html',
    styleUrl: './course-tutorial-group-detail-session-status-indicator.component.scss',
})
export class CourseTutorialGroupDetailSessionStatusIndicatorComponent {
    isCancelled = input.required<boolean>();
    locationChanged = input.required<boolean>();
    timeChanged = input.required<boolean>();
    dateChanged = input.required<boolean>();
}
