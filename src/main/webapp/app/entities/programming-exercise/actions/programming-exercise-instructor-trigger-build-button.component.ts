import { Component, Input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseTriggerBuildButtonComponent } from './programming-exercise-trigger-build-button.component';
import { ProgrammingSubmissionService } from 'app/programming-submission/programming-submission.service';
import { SubmissionType } from 'app/entities/submission';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/programming-assessment/repo-export';
import { ProgrammingExerciseInstructorTriggerAllDialogComponent } from 'app/entities/programming-exercise/actions/programming-exercise-trigger-all-button.component';

@Component({
    selector: 'jhi-programming-exercise-instructor-trigger-build-button',
    templateUrl: './programming-exercise-trigger-build-button.component.html',
})
export class ProgrammingExerciseInstructorTriggerBuildButtonComponent extends ProgrammingExerciseTriggerBuildButtonComponent {
    constructor(submissionService: ProgrammingSubmissionService, private translateService: TranslateService, private modalService: NgbModal) {
        super(submissionService);
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
        const modalRef = this.modalService.open(ProgrammingExerciseInstructorTriggerBuildDialogComponent, { keyboard: true, size: 'lg' });
        modalRef.result.then(() => {
            super.triggerBuild(SubmissionType.INSTRUCTOR);
        });
    };
}

/**
 * The warning modal of the trigger button that informs the user about existing manual results.
 */
@Component({
    template: `
        <form (ngSubmit)="onConfirm()">
            <div class="modal-header">
                <h4 class="modal-title" jhiTranslate="artemisApp.programmingExercise.resubmitSingle">Trigger</h4>
                <button type="button" class="close" data-dismiss="modal" aria-hidden="true" (click)="cancel()">&times;</button>
            </div>
            <div class="modal-body">
                <p class="text-danger" jhiTranslate="artemisApp.programmingExercise.resubmitConfirmManualResultOverride">
                    The last result of this participation is manual, which means the submission was assessed by a teaching assisstant. If you trigger the build for this submission
                    again, the manual result will no longer be the latest result that is shown primarily to the student.
                </p>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-dismiss="modal" (click)="cancel()">
                    <fa-icon [icon]="'ban'"></fa-icon>&nbsp;<span jhiTranslate="entity.action.cancel">Cancel</span>
                </button>
                <button type="submit" class="btn btn-danger">
                    <fa-icon [icon]="'times'"></fa-icon>&nbsp;
                    <span jhiTranslate="entity.action.confirm">Confirm</span>
                </button>
            </div>
        </form>
    `,
})
export class ProgrammingExerciseInstructorTriggerBuildDialogComponent {
    constructor(private activeModal: NgbActiveModal) {}

    cancel() {
        this.activeModal.dismiss('cancel');
    }

    onConfirm() {
        this.activeModal.close();
    }
}
