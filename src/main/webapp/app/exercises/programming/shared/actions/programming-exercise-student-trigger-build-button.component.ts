import { Component, Input } from '@angular/core';
import { ProgrammingExerciseTriggerBuildButtonComponent } from './programming-exercise-trigger-build-button.component';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { AlertService } from 'app/core/util/alert.service';
import { catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { SubmissionType } from 'app/entities/submission.model';
import { faRedo } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-programming-exercise-student-trigger-build-button',
    templateUrl: './programming-exercise-trigger-build-button.component.html',
})
export class ProgrammingExerciseStudentTriggerBuildButtonComponent extends ProgrammingExerciseTriggerBuildButtonComponent {
    @Input() triggerLastGraded = false;

    // Icons
    faRedo = faRedo;

    constructor(submissionService: ProgrammingSubmissionService, alertService: AlertService, participationWebsocketService: ParticipationWebsocketService) {
        super(submissionService, participationWebsocketService, alertService);
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
