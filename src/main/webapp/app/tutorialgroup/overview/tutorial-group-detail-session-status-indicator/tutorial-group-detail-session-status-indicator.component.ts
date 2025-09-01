import { Component, input } from '@angular/core';
import { TutorialGroupDetailSessionDTOStatus } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { CourseTutorialGroupDetailSessionStatusChipComponent } from 'app/tutorialgroup/overview/tutorial-group-detail-session-status-chip/course-tutorial-group-detail-session-status-chip.component';

@Component({
    selector: 'jhi-tutorial-group-detail-session-status-indicator',
    imports: [CourseTutorialGroupDetailSessionStatusChipComponent],
    templateUrl: './tutorial-group-detail-session-status-indicator.component.html',
    styleUrl: './tutorial-group-detail-session-status-indicator.component.scss',
})
export class TutorialGroupDetailSessionStatusIndicatorComponent {
    readonly TutorialGroupDetailSessionDTOStatus = TutorialGroupDetailSessionDTOStatus;
    status = input.required<TutorialGroupDetailSessionDTOStatus>();
}
