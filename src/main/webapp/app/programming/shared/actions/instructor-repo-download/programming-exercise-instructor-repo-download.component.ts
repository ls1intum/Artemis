import { Component, inject, input } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { AlertService } from 'app/shared/service/alert.service';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import { catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

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

    readonly exerciseId = input<number>(undefined!);
    readonly repositoryType = input<RepositoryType>(undefined!);
    readonly auxiliaryRepositoryId = input<number>(undefined!);
    readonly buttonSize = input(ButtonSize.SMALL);
    readonly title = input('artemisApp.programmingExercise.export.downloadRepo');

    // Icons
    faDownload = faDownload;

    exportRepository() {
        const exerciseId = this.exerciseId();
        const repositoryType = this.repositoryType();
        if (exerciseId && repositoryType) {
            this.programmingExerciseService
                .exportInstructorRepository(exerciseId, repositoryType, this.auxiliaryRepositoryId())
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
