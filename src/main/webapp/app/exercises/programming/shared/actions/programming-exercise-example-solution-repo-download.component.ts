import { Component, Input } from '@angular/core';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { ProgrammingExerciseInstructorRepoDownloadComponent } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-repo-download.component';

@Component({
    selector: 'jhi-programming-exercise-example-solution-repo-download',
    templateUrl: './programming-exercise-instructor-repo-download.component.html',
})
export class ProgrammingExerciseExampleSolutionRepoDownloadComponent extends ProgrammingExerciseInstructorRepoDownloadComponent {
    @Input() includeTests?: boolean;

    exportRepository() {
        if (this.exerciseId) {
            this.programmingExerciseService.exportStudentRequestedRepository(this.exerciseId, this.includeTests ?? false).subscribe((response) => {
                downloadZipFileFromResponse(response);
                this.alertService.success('artemisApp.programmingExercise.export.successMessageRepos');
            });
        }
    }
}
