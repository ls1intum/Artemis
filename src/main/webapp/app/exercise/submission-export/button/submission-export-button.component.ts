import { Component, inject, input } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { SubmissionExportDialogComponent } from '../dialog/submission-export-dialog.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-exercise-submission-export',
    template: `
        <jhi-button
            [featureToggle]="FeatureToggle.Exports"
            [disabled]="!exerciseId()"
            [btnType]="ButtonType.INFO"
            [btnSize]="ButtonSize.SMALL"
            [shouldSubmit]="false"
            [icon]="faDownload"
            [title]="'artemisApp.instructorDashboard.exportSubmissions.title'"
            (onClick)="openSubmissionExportDialog($event)"
        />
    `,
    imports: [ButtonComponent],
})
export class SubmissionExportButtonComponent {
    private readonly dialogService = inject(DialogService);
    private readonly translateService = inject(TranslateService);

    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;
    readonly FeatureToggle = FeatureToggle;

    readonly exerciseId = input<number>();
    readonly exerciseType = input<ExerciseType>();

    // Icons
    readonly faDownload = faDownload;

    /**
     * Stops the propagation of the mouse event and updates the component instance
     * of the dialogRef with this instance's values
     * @param {MouseEvent} event - Mouse event
     */
    openSubmissionExportDialog(event: MouseEvent) {
        event.stopPropagation();
        const exerciseId = this.exerciseId();
        const exerciseType = this.exerciseType();
        if (exerciseId === undefined || exerciseType === undefined) {
            return;
        }

        this.dialogService.open(SubmissionExportDialogComponent, {
            header: this.translateService.instant('artemisApp.instructorDashboard.exportSubmissions.title'),
            width: '50rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            dismissableMask: true,
            inputValues: {
                exerciseId,
                exerciseType,
            },
        });
    }
}
