import { Component } from '@angular/core';
import { ProgrammingExerciseTriggerBuildButtonComponent } from './programming-exercise-trigger-build-button.component';
import { ProgrammingSubmissionWebsocketService } from 'app/programming-submission/programming-submission-websocket.service';

@Component({
    selector: 'jhi-programming-exercise-instructor-trigger-build-button',
    templateUrl: './programming-exercise-trigger-build-button.component.html',
})
export class ProgrammingExerciseInstructorTriggerBuildButtonComponent extends ProgrammingExerciseTriggerBuildButtonComponent {
    constructor(submissionService: ProgrammingSubmissionWebsocketService) {
        super(submissionService);
        this.alwaysShowTriggerButton = true;
    }
    triggerBuild = (event: any) => {
        // The button might be placed in other elements that have a click listener, so catch the click here.
        event.stopPropagation();
        this.submissionService.triggerInstructorBuild(this.participation.id).subscribe();
    };
}
