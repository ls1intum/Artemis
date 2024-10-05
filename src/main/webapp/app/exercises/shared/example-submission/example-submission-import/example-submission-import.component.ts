import { Component, inject } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Submission } from 'app/entities/submission.model';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { ImportComponent } from 'app/shared/import/import.component';

@Component({
    selector: 'jhi-example-submission-import',
    templateUrl: './example-submission-import.component.html',
})
export class ExampleSubmissionImportComponent extends ImportComponent<Submission> {
    private exampleSubmissionService = inject(ExampleSubmissionService);

    exercise: Exercise;

    readonly faQuestionCircle = faQuestionCircle;
    readonly ExerciseType = ExerciseType;

    get searchTermEntered() {
        return !!(this.state?.searchTerm?.length && this.state.searchTerm.length > 0);
    }

    protected override onSearchResult() {
        this.content?.resultsOnPage?.forEach((submission) => {
            submission.submissionSize = this.exampleSubmissionService.getSubmissionSize(submission, this.exercise);
        });
    }

    protected override createOptions(): object | undefined {
        return { exerciseId: this.exercise.id! };
    }
}
