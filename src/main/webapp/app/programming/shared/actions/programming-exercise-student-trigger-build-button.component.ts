import { Component, Input, inject } from '@angular/core';
import { ProgrammingExerciseTriggerBuildButtonComponent } from './programming-exercise-trigger-build-button.component';
import { AlertService } from 'app/shared/service/alert.service';
import { catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { SubmissionType } from 'app/entities/submission.model';
import { faRedo } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared/components/button.component';

@Component({
    selector: 'jhi-programming-exercise-student-trigger-build-button',
    templateUrl: './programming-exercise-trigger-build-button.component.html',
    imports: [ButtonComponent],
})
export class ProgrammingExerciseStudentTriggerBuildButtonComponent extends ProgrammingExerciseTriggerBuildButtonComponent {
    private alertService = inject(AlertService);
    @Input() triggerLastGraded = false;

    // Icons
    faRedo = faRedo;

    constructor() {
        super();
        this.personalParticipation = true;
    }

    triggerBuild = (event: any) => {
        // The button might be placed in other elements that have a click listener, so catch the click here.
        event.stopPropagation();
        const triggerAction = this.participationHasLatestSubmissionWithoutResult ? super.triggerFailed(this.triggerLastGraded) : super.triggerWithType(SubmissionType.MANUAL);
        triggerAction
            .pipe(
                catchError(() => {
                    this.alertService.error('artemisApp.programmingExercise.resubmitUnsuccessful');
                    return of(undefined);
                }),
            )
            .subscribe();
    };
}
