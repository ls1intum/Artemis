import { Component } from '@angular/core';
import { ProgrammingExerciseTriggerBuildButtonComponent } from './programming-exercise-trigger-build-button.component';
import { ProgrammingSubmissionService } from 'app/programming-submission/programming-submission.service';
import { SubmissionType } from 'app/entities/submission';

@Component({
    selector: 'jhi-programming-exercise-instructor-trigger-build-button',
    templateUrl: './programming-exercise-trigger-build-button.component.html',
})
export class ProgrammingExerciseInstructorTriggerBuildButtonComponent extends ProgrammingExerciseTriggerBuildButtonComponent {
    constructor(submissionService: ProgrammingSubmissionService) {
        super(submissionService);
        this.showForSuccessfulSubmissions = true;
    }

    // TODO: we should warn the instructor in case manual results are enabled and the build and test deadline has passed

    triggerBuild = (event: any) => {
        // The button might be placed in other elements that have a click listener, so catch the click here.
        event.stopPropagation();
        super.triggerBuild(SubmissionType.INSTRUCTOR);
    };
}
