import { Component, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseTriggerBuildButtonComponent } from '../programming-exercise-trigger-build-button.component';
import { SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { ConfirmAutofocusModalResult, openConfirmAutofocusDialog } from 'app/shared-ui/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { faRedo } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { DialogService } from 'primeng/dynamicdialog';

@Component({
    selector: 'jhi-programming-exercise-instructor-trigger-build-button',
    templateUrl: '../programming-exercise-trigger-build-button.component.html',
    imports: [ButtonComponent],
})
export class ProgrammingExerciseInstructorTriggerBuildButtonComponent extends ProgrammingExerciseTriggerBuildButtonComponent {
    private translateService = inject(TranslateService);
    private dialogService = inject(DialogService);

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
        // The instructor needs to confirm overriding a manual result.
        const dialogRef = openConfirmAutofocusDialog(this.dialogService, {
            title: 'artemisApp.programmingExercise.resubmitSingle',
            text: this.translateService.instant('artemisApp.programmingExercise.resubmitConfirmManualResultOverride'),
        });
        dialogRef?.onClose.subscribe((result: ConfirmAutofocusModalResult | undefined) => {
            if (result?.confirmed) {
                super.triggerWithType(SubmissionType.INSTRUCTOR).subscribe();
            }
        });
    };
}
