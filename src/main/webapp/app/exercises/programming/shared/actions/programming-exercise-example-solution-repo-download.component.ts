import { Component } from '@angular/core';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { ProgrammingExerciseInstructorRepoDownloadComponent, template } from 'app/exercises/programming/shared/actions/programming-exercise-instructor-repo-download.component';

@Component({
    selector: 'jhi-programming-exercise-example-solution-repo-download',
    template,
})
export class ProgrammingExerciseExampleSolutionRepoDownloadComponent extends ProgrammingExerciseInstructorRepoDownloadComponent {
    exportRepository() {
        if (this.exerciseId) {
            this.programmingExerciseService.exportSolutionRepository(this.exerciseId).subscribe((response) => {
                downloadZipFileFromResponse(response);
                this.alertService.success('artemisApp.programmingExercise.export.successMessageRepos');
            });
        }
    }
}
