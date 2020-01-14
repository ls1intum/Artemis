import { Component } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseTriggerBuildButtonComponent } from './programming-exercise-trigger-build-button.component';
import { ProgrammingSubmissionService } from 'app/programming-submission/programming-submission.service';
import { SubmissionType } from 'app/entities/submission';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmAutofocusModalComponent } from 'app/shared/components';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    selector: 'jhi-programming-exercise-instructor-trigger-build-button',
    templateUrl: './programming-exercise-trigger-build-button.component.html',
})
export class ProgrammingExerciseInstructorTriggerBuildButtonComponent extends ProgrammingExerciseTriggerBuildButtonComponent {
    constructor(
        submissionService: ProgrammingSubmissionService,
        alertService: JhiAlertService,
        participationWebsocketService: ParticipationWebsocketService,
        private translateService: TranslateService,
        private modalService: NgbModal,
    ) {
        super(submissionService, participationWebsocketService, alertService);
        this.showForSuccessfulSubmissions = true;
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
        modalRef.componentInstance.text = 'artemisApp.programmingExercise.resubmitConfirmManualResultOverride';
        modalRef.result.then(() => {
            super.triggerWithType(SubmissionType.INSTRUCTOR);
        });
    };
}
