import { Component, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseTriggerBuildButtonComponent } from '../programming-exercise-trigger-build-button.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { ConfirmAutofocusModalComponent } from 'app/shared-ui/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { faRedo } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';

@Component({
    selector: 'jhi-programming-exercise-instructor-trigger-build-button',
    templateUrl: '../programming-exercise-trigger-build-button.component.html',
    imports: [ButtonComponent],
})
export class ProgrammingExerciseInstructorTriggerBuildButtonComponent extends ProgrammingExerciseTriggerBuildButtonComponent {
    private translateService = inject(TranslateService);
    // ConfirmAutofocusModalComponent still uses NgbActiveModal; migration is out of scope.
    private modalService = inject(NgbModal);

    faRedo = faRedo;

    constructor() {
        super();
        this.showForSuccessfulSubmissions.set(true);
        this.personalParticipation = false;
    }

    triggerBuild = (event: MouseEvent) => {
        // The button is often nested inside other click handlers; stop propagation so the parent action does not also fire.
        event.stopPropagation();
        if (this.participationHasLatestSubmissionWithoutResult()) {
            super.triggerFailed().subscribe();
            return;
        }
        if (!this.lastResultIsManual()) {
            super.triggerWithType(SubmissionType.INSTRUCTOR).subscribe();
            return;
        }
        // Overriding a manual result requires explicit instructor confirmation.
        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.title = 'artemisApp.programmingExercise.resubmitSingle';
        modalRef.componentInstance.text = this.translateService.instant('artemisApp.programmingExercise.resubmitConfirmManualResultOverride');
        modalRef.result
            .then(() => {
                super.triggerWithType(SubmissionType.INSTRUCTOR).subscribe();
            })
            .catch(() => {});
    };
}
