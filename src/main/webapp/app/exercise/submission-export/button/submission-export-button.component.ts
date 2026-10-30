import { Component, inject, input, output } from '@angular/core';
import { SubmissionExportDialogComponent } from '../dialog/submission-export-dialog.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-exercise-submission-export',
    template: `
        <button
            type="button"
            tumAetUiButton
            size="small"
            [variant]="inDropdown() ? 'text' : 'solid'"
            [severity]="inDropdown() ? 'secondary' : 'primary'"
            [class]="inDropdown() ? 'w-full justify-start!' : ''"
            [jhiFeatureToggle]="FeatureToggle.Exports"
            [forceDisabled]="!exerciseId() || false"
            [attr.aria-label]="'artemisApp.instructorDashboard.exportSubmissions.title' | artemisTranslate"
            (click)="openSubmissionExportDialog($event)"
        >
            <fa-icon [icon]="faDownload" />
            <span [jhiTranslate]="'artemisApp.instructorDashboard.exportSubmissions.title'"></span>
        </button>
    `,
    imports: [TumAetUiButtonDirective, FaIconComponent, ArtemisTranslatePipe, FeatureToggleDirective, TranslateDirective],
})
export class SubmissionExportButtonComponent {
    private readonly dialogService = inject(DialogService);
    private readonly translateService = inject(TranslateService);

    readonly inDropdown = input(false);

    readonly FeatureToggle = FeatureToggle;

    readonly exerciseId = input<number>();
    readonly exerciseType = input<ExerciseType>();

    readonly buttonPressed = output<void>();

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

        this.buttonPressed.emit();
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
