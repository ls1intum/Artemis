import { Component, Input, inject } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { AlertService } from 'app/shared/service/alert.service';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';

@Component({
    selector: 'jhi-programming-exercise-instructor-exercise-download',
    template: `
        <jhi-button
            [disabled]="!exerciseId"
            [btnType]="ButtonType.INFO"
            [btnSize]="ButtonSize.SMALL"
            [shouldSubmit]="false"
            [featureToggle]="[FeatureToggle.ProgrammingExercises, FeatureToggle.Exports]"
            [icon]="faDownload"
            [title]="'artemisApp.programmingExercise.export.downloadExercise'"
            (onClick)="exportExercise()"
        />
    `,
    imports: [ButtonComponent],
})
export class ProgrammingExerciseInstructorExerciseDownloadComponent {
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private alertService = inject(AlertService);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;
    readonly FeatureToggle = FeatureToggle;

    @Input()
    exerciseId: number;

    // Icons
    faDownload = faDownload;

    exportExercise() {
        if (this.exerciseId) {
            this.programmingExerciseService.exportInstructorExercise(this.exerciseId).subscribe(
                (response) => {
                    downloadZipFileFromResponse(response);
                    this.alertService.success('artemisApp.programmingExercise.export.successMessageExercise');
                },
                () => this.alertService.error('error.exportFailed'),
            );
        }
    }
}
