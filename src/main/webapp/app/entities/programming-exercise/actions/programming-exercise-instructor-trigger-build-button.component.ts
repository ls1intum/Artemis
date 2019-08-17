import { Component } from '@angular/core';
import { ProgrammingExerciseTriggerBuildButtonComponent } from './programming-exercise-trigger-build-button.component';
import { ParticipationWebsocketService } from 'app/entities/participation';
import { ProgrammingSubmissionWebsocketService } from 'app/submission/programming-submission-websocket.service';
import { ProgrammingExerciseParticipationService } from 'app/entities/programming-exercise';

@Component({
    selector: 'jhi-programming-exercise-instructor-trigger-build-button',
    templateUrl: './programming-exercise-trigger-build-button.component.html',
})
export class ProgrammingExerciseInstructorTriggerBuildButtonComponent extends ProgrammingExerciseTriggerBuildButtonComponent {
    constructor(
        participationWebsocketService: ParticipationWebsocketService,
        submissionService: ProgrammingSubmissionWebsocketService,
        programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
    ) {
        super(participationWebsocketService, submissionService, programmingExerciseParticipationService);
        this.alwaysShowTriggerButton = true;
    }
    triggerBuild = (event: any) => {
        // The button might be placed in other elements that have a click listener, so catch the click here.
        event.stopPropagation();
        this.submissionService.triggerInstructorBuild(this.participation.id).subscribe();
    };
    getTooltip = () => {
        return 'artemisApp.programmingExercise.triggerInstructorBuild';
    };
}
