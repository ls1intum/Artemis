import { Component, effect, inject } from '@angular/core';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariableSignal';
import { ActivatedRoute } from '@angular/router';
import { TutorialRegistrationsComponent } from 'app/tutorialgroup/manage/tutorial-registrations/tutorial-registrations.component';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { TutorialGroupRegisteredStudentsService } from 'app/tutorialgroup/shared/service/tutorial-group-registered-students.service';

@Component({
    selector: 'jhi-tutorial-registrations-container',
    imports: [TutorialRegistrationsComponent, LoadingIndicatorOverlayComponent],
    templateUrl: './tutorial-registrations-container.component.html',
})
export class TutorialRegistrationsContainerComponent {
    private activatedRoute = inject(ActivatedRoute);
    private tutorialGroupRegisteredStudentsService = inject(TutorialGroupRegisteredStudentsService);

    courseId = getNumericPathVariableSignal(this.activatedRoute, 'courseId');
    tutorialGroupId = getNumericPathVariableSignal(this.activatedRoute, 'tutorialGroupId');
    isLoading = this.tutorialGroupRegisteredStudentsService.isLoading;
    registeredStudents = this.tutorialGroupRegisteredStudentsService.registeredStudents;

    constructor() {
        effect(() => {
            const courseId = this.courseId();
            const tutorialGroupsId = this.tutorialGroupId();
            if (courseId && tutorialGroupsId) {
                this.tutorialGroupRegisteredStudentsService.fetchRegisteredStudents(courseId, tutorialGroupsId);
            }
        });
    }
}
