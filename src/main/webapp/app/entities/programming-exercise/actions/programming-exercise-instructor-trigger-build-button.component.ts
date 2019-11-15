import { Component, Input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseTriggerBuildButtonComponent } from './programming-exercise-trigger-build-button.component';
import { ProgrammingSubmissionService } from 'app/programming-submission/programming-submission.service';
import { SubmissionType } from 'app/entities/submission';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/programming-assessment/repo-export';
import { ProgrammingExerciseInstructorTriggerAllDialogComponent } from 'app/entities/programming-exercise/actions/programming-exercise-trigger-all-button.component';
import { ConfirmAutofocusModalComponent } from 'app/shared/components';
import { ParticipationWebsocketService } from 'app/entities/participation';

@Component({
    selector: 'jhi-programming-exercise-instructor-trigger-build-button',
    templateUrl: './programming-exercise-trigger-build-button.component.html',
})
export class ProgrammingExerciseInstructorTriggerBuildButtonComponent extends ProgrammingExerciseTriggerBuildButtonComponent {
    constructor(
        submissionService: ProgrammingSubmissionService,
        participationWebsocketService: ParticipationWebsocketService,
        private translateService: TranslateService,
        private modalService: NgbModal,
    ) {
        super(submissionService, participationWebsocketService);
        this.showForSuccessfulSubmissions = true;
    }

    triggerBuild = (event: any) => {
        // The button might be placed in other elements that have a click listener, so catch the click here.
        event.stopPropagation();
        if (!this.lastResultIsManual) {
            super.triggerBuild(SubmissionType.INSTRUCTOR);
            return;
        }
        // The instructor needs to confirm overriding a manual result.
        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.title = 'artemisApp.programmingExercise.resubmitSingle';
        modalRef.componentInstance.text = 'artemisApp.programmingExercise.resubmitConfirmManualResultOverride';
        modalRef.result.then(() => {
            super.triggerBuild(SubmissionType.INSTRUCTOR);
        });
    };
}
