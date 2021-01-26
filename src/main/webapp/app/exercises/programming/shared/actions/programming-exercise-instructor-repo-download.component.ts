import { Component, Input } from '@angular/core';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingExerciseInstructorRepositoryType, ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    selector: 'jhi-programming-exercise-instructor-repo-download',
    template: `
        <jhi-button
            [disabled]="!exerciseId"
            [btnType]="ButtonType.INFO"
            [btnSize]="ButtonSize.SMALL"
            [shouldSubmit]="false"
            [featureToggle]="FeatureToggle.PROGRAMMING_EXERCISES"
            [icon]="'download'"
            [title]="'artemisApp.programmingExercise.export.downloadRepo'"
            (onClick)="exportRepository()"
        ></jhi-button>
    `,
})
export class ProgrammingExerciseInstructorRepoDownloadComponent {
    ButtonType = ButtonType;
    ButtonSize = ButtonSize;
    readonly FeatureToggle = FeatureToggle;

    @Input()
    exerciseId: number;

    @Input()
    repositoryType: ProgrammingExerciseInstructorRepositoryType;

    constructor(private programmingExerciseService: ProgrammingExerciseService, private alertService: JhiAlertService) {}

    exportRepository() {
        if (this.exerciseId && this.repositoryType) {
            this.programmingExerciseService.exportInstructorRepository(this.exerciseId, this.repositoryType).subscribe((response) => {
                downloadZipFileFromResponse(response);
                this.alertService.success('artemisApp.programmingExercise.export.successMessage');
            });
        }
    }
}
