import { Component } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseTriggerBuildButtonComponent } from './programming-exercise-trigger-build-button.component';
import { ProgrammingSubmissionService } from 'app/programming-submission/programming-submission.service';
import { SubmissionType } from 'app/entities/submission';

@Component({
    selector: 'jhi-programming-exercise-instructor-trigger-build-button',
    templateUrl: './programming-exercise-trigger-build-button.component.html',
})
export class ProgrammingExerciseInstructorTriggerBuildButtonComponent extends ProgrammingExerciseTriggerBuildButtonComponent {
    constructor(submissionService: ProgrammingSubmissionService, translateService: TranslateService) {
        super(submissionService, translateService);
        this.showForSuccessfulSubmissions = true;
    }

    triggerBuild = (event: any) => {
        // The button might be placed in other elements that have a click listener, so catch the click here.
        event.stopPropagation();
        // The instructor needs to confirm overriding a manual result.
        if (this.lastResultIsManual && !confirm(this.translateService.instant('artemisApp.programmingExercise.resubmitConfirmManualResultOverride'))) {
            return;
        }
        super.triggerBuild(SubmissionType.INSTRUCTOR);
    };
}
