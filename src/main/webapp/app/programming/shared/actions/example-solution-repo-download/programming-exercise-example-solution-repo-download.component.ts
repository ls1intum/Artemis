import { Component, input } from '@angular/core';
import { downloadZipFileFromResponse } from 'app/foundation/util/download.util';
import { ProgrammingExerciseInstructorRepoDownloadComponent } from 'app/programming/shared/actions/instructor-repo-download/programming-exercise-instructor-repo-download.component';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';

@Component({
    selector: 'jhi-programming-exercise-example-solution-repo-download',
    template: `<jhi-button
        [disabled]="!exerciseId()"
        [btnType]="displayedOnExamSummary() ? ButtonType.PRIMARY_OUTLINE : ButtonType.INFO"
        [btnSize]="displayedOnExamSummary() ? ButtonSize.SMALL : ButtonSize.MEDIUM"
        [shouldSubmit]="false"
        [featureToggle]="[FeatureToggle.ProgrammingExercises, FeatureToggle.Exports]"
        [icon]="faDownload"
        [title]="title()"
        (onClick)="exportRepository()"
    />`,
    imports: [ButtonComponent],
})
export class ProgrammingExerciseExampleSolutionRepoDownloadComponent extends ProgrammingExerciseInstructorRepoDownloadComponent {
    readonly includeTests = input<boolean>();
    readonly forStaff = input(false);
    readonly displayedOnExamSummary = input(false);

    override exportRepository() {
        const exerciseId = this.exerciseId();
        if (exerciseId) {
            const download = this.forStaff()
                ? this.programmingExerciseService.exportSolutionSnapshotForStaff(exerciseId, this.includeTests() ?? false)
                : this.programmingExerciseService.exportStudentRequestedRepository(exerciseId, this.includeTests() ?? false);
            download.subscribe((response) => {
                downloadZipFileFromResponse(response);
                this.alertService.success('artemisApp.programmingExercise.export.successMessageRepos');
            });
        }
    }
}
