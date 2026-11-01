import { Component, inject, input } from '@angular/core';
import { SubmissionExportDialogComponent } from '../dialog/submission-export-dialog.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { TumAetUiDialogService } from '@tumaet/ui-angular';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-exercise-submission-export',
    template: `
        <button
            tumAetUiButton
            type="button"
            [jhiFeatureToggle]="FeatureToggle.Exports"
            [disabled]="!exerciseId()"
            severity="info"
            size="small"
            (click)="openSubmissionExportDialog($event)"
        >
            <fa-icon [icon]="faDownload" /><span jhiTranslate="artemisApp.instructorDashboard.exportSubmissions.title"></span>
        </button>
    `,
    imports: [TumAetUiButtonDirective, TranslateDirective, FaIconComponent, FeatureToggleDirective],
})
export class SubmissionExportButtonComponent {
    private readonly dialogService = inject(TumAetUiDialogService);
    private readonly translateService = inject(TranslateService);

    readonly FeatureToggle = FeatureToggle;

    readonly exerciseId = input<number>();
    readonly exerciseType = input<ExerciseType>();
    readonly examExercise = input(false);

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
            size: 'large',
            closable: true,
            closeOnEscape: true,
            dismissableMask: true,
            inputValues: {
                exerciseId,
                exerciseType,
                examExercise: this.examExercise(),
            },
        });
    }
}
