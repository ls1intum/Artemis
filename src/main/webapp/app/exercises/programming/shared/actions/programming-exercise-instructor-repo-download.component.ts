import { Component, Input, inject } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { AlertService } from 'app/core/util/alert.service';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { ButtonComponent } from 'app/shared/components/button.component';
import { RepositoryType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-programming-exercise-instructor-repo-download',
    templateUrl: './programming-exercise-instructor-repo-download.component.html',
    imports: [ButtonComponent],
})
export class ProgrammingExerciseInstructorRepoDownloadComponent {
    protected programmingExerciseService = inject(ProgrammingExerciseService);
    protected alertService = inject(AlertService);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;
    readonly FeatureToggle = FeatureToggle;

    @Input() exerciseId: number;
    @Input() repositoryType: RepositoryType;
    @Input() auxiliaryRepositoryId: number;
    @Input() buttonSize = ButtonSize.SMALL;
    @Input() title = 'artemisApp.programmingExercise.export.downloadRepo';

    // Icons
    faDownload = faDownload;

    exportRepository() {
        if (this.exerciseId && this.repositoryType) {
            this.programmingExerciseService
                .exportInstructorRepository(this.exerciseId, this.repositoryType, this.auxiliaryRepositoryId)
                .pipe(
                    catchError((error) => {
                        if (error.status === 500 || error.status === 404) {
                            this.alertService.error('artemisApp.programmingExercise.export.errorMessageRepo');
                        }
                        // Return an observable of undefined so the subscribe method can continue
                        return of(undefined);
                    }),
                )
                .subscribe((response) => {
                    if (response !== undefined) {
                        downloadZipFileFromResponse(response);
                        this.alertService.success('artemisApp.programmingExercise.export.successMessageRepo');
                    }
                });
        }
    }
}
