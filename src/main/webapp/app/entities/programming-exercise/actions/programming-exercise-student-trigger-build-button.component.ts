import { Component, Input } from '@angular/core';
import { ProgrammingExerciseTriggerBuildButtonComponent } from './programming-exercise-trigger-build-button.component';
import { ProgrammingSubmissionService } from 'app/programming-submission/programming-submission.service';
import { SubmissionType } from 'app/entities/submission';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { JhiAlertService } from 'ng-jhipster';
import { catchError } from 'rxjs/operators';
import { of } from 'rxjs';

@Component({
    selector: 'jhi-programming-exercise-student-trigger-build-button',
    templateUrl: './programming-exercise-trigger-build-button.component.html',
})
export class ProgrammingExerciseStudentTriggerBuildButtonComponent extends ProgrammingExerciseTriggerBuildButtonComponent {
    @Input() triggerLastGraded = false;

    constructor(submissionService: ProgrammingSubmissionService, alertService: JhiAlertService, participationWebsocketService: ParticipationWebsocketService) {
        super(submissionService, participationWebsocketService, alertService);
    }

    // TODO: this should not be allowed after the build and test deadline if manual grading is enabled for the exercise otherwise students could override the manual results
    triggerBuild = (event: any) => {
        // The button might be placed in other elements that have a click listener, so catch the click here.
        event.stopPropagation();
        const triggerAction = this.participationHasLatestSubmissionWithoutResult ? super.triggerFailed(this.triggerLastGraded) : super.triggerWithType(SubmissionType.MANUAL);
        triggerAction
            .pipe(
                catchError(() => {
                    this.alertService.error('artemisApp.programmingExercise.resubmitUnsuccessful');
                    return of(null);
                }),
            )
            .subscribe();
    };
}
