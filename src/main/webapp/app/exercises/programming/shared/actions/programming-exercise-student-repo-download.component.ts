import { Component, Input, inject } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { AlertService } from 'app/core/util/alert.service';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { take } from 'rxjs';

@Component({
    selector: 'jhi-programming-exercise-student-repo-download',
    templateUrl: './programming-exercise-student-repo-download.component.html',
})
export class ProgrammingExerciseStudentRepoDownloadComponent {
    protected programmingExerciseService = inject(ProgrammingExerciseService);
    protected alertService = inject(AlertService);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;
    readonly FeatureToggle = FeatureToggle;

    @Input()
    exerciseId: number;

    @Input()
    participationId: number;

    @Input()
    buttonSize: ButtonSize = ButtonSize.SMALL;

    @Input()
    title = 'artemisApp.programmingExercise.export.downloadRepo';

    // Icons
    faDownload = faDownload;

    exportRepository() {
        if (this.exerciseId && this.participationId) {
            this.programmingExerciseService
                .exportStudentRepository(this.exerciseId, this.participationId)
                .pipe(take(1))
                .subscribe((response) => {
                    downloadZipFileFromResponse(response);
                    this.alertService.success('artemisApp.programmingExercise.export.successMessageRepo');
                });
        }
    }
}
