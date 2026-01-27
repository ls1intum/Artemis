import { Component, inject } from '@angular/core';
import { getNumericPathVariableSignal } from 'app/shared/route/getPathVariableSignal';
import { ActivatedRoute } from '@angular/router';
import { TutorialRegistrationsComponent } from 'app/tutorialgroup/manage/tutorial-registrations/tutorial-registrations.component';

@Component({
    selector: 'jhi-tutorial-registrations-container',
    imports: [TutorialRegistrationsComponent],
    templateUrl: './tutorial-registrations-container.component.html',
    styleUrl: './tutorial-registrations-container.component.scss',
})
export class TutorialRegistrationsContainerComponent {
    private activatedRoute = inject(ActivatedRoute);

    courseId = getNumericPathVariableSignal(this.activatedRoute, 'courseId', 3);
    tutorialGroupId = getNumericPathVariableSignal(this.activatedRoute, 'tutorialGroupId', 1);
}
