import { Component } from '@angular/core';
import { ProgrammingExerciseTriggerBuildButtonComponent } from './programming-exercise-trigger-build-button.component';
import { ProgrammingSubmissionService } from 'app/programming-submission/programming-submission.service';

@Component({
    selector: 'jhi-programming-exercise-student-trigger-build-button',
    templateUrl: './programming-exercise-trigger-build-button.component.html',
})
export class ProgrammingExerciseStudentTriggerBuildButtonComponent extends ProgrammingExerciseTriggerBuildButtonComponent {
    constructor(submissionService: ProgrammingSubmissionService) {
        super(submissionService);
    }
    triggerBuild = (event: any) => {
        // The button might be placed in other elements that have a click listener, so catch the click here.
        event.stopPropagation();
        this.submissionService.triggerBuild(this.participation.id).subscribe();
    };
}
