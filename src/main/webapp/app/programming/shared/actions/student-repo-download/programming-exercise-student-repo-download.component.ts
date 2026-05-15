import { Component, inject, input } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { AlertService } from 'app/shared/service/alert.service';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { take } from 'rxjs';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';

@Component({
    selector: 'jhi-programming-exercise-student-repo-download',
    templateUrl: './programming-exercise-student-repo-download.component.html',
    imports: [ButtonComponent],
})
export class ProgrammingExerciseStudentRepoDownloadComponent {
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private alertService = inject(AlertService);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;
    readonly FeatureToggle = FeatureToggle;

    readonly exerciseId = input<number>();

    readonly participationId = input<number>();

    readonly buttonSize = input<ButtonSize>(ButtonSize.SMALL);

    readonly title = input('artemisApp.programmingExercise.export.downloadRepo');

    // Icons
    faDownload = faDownload;

    exportRepository() {
        const exerciseId = this.exerciseId();
        const participationId = this.participationId();
        if (exerciseId && participationId) {
            this.programmingExerciseService
                .exportStudentRepository(exerciseId, participationId)
                .pipe(take(1))
                .subscribe((response) => {
                    downloadZipFileFromResponse(response);
                    this.alertService.success('artemisApp.programmingExercise.export.successMessageRepo');
                });
        }
    }
}
