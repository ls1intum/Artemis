import { Component, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseTriggerBuildButtonComponent } from './programming-exercise-trigger-build-button.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { SubmissionType } from 'app/entities/submission.model';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal.component';
import { faRedo } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-programming-exercise-instructor-trigger-build-button',
    templateUrl: './programming-exercise-trigger-build-button.component.html',
})
export class ProgrammingExerciseInstructorTriggerBuildButtonComponent extends ProgrammingExerciseTriggerBuildButtonComponent {
    private translateService = inject(TranslateService);
    private modalService = inject(NgbModal);

    // Icons
    faRedo = faRedo;

    constructor() {
        super();
        this.showForSuccessfulSubmissions = true;
        this.personalParticipation = false;
    }

    triggerBuild = (event: any) => {
        // The button might be placed in other elements that have a click listener, so catch the click here.
        event.stopPropagation();
        if (this.participationHasLatestSubmissionWithoutResult) {
            super.triggerFailed().subscribe();
        } else {
            super.triggerWithType(SubmissionType.INSTRUCTOR).subscribe();
        }
        if (!this.lastResultIsManual) {
            super.triggerWithType(SubmissionType.INSTRUCTOR);
            return;
        }
        // The instructor needs to confirm overriding a manual result.
        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.title = 'artemisApp.programmingExercise.resubmitSingle';
        modalRef.componentInstance.text = this.translateService.instant('artemisApp.programmingExercise.resubmitConfirmManualResultOverride');
        modalRef.result.then(() => {
            super.triggerWithType(SubmissionType.INSTRUCTOR);
        });
    };
}
