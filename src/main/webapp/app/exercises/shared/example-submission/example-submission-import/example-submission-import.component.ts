import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Submission } from 'app/entities/submission.model';
import { ExampleSubmissionImportPagingService } from 'app/exercises/shared/example-submission/example-submission-import/example-submission-import-paging.service';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { ImportComponent } from 'app/shared/import/import.component';
import { SortService } from 'app/shared/service/sort.service';

@Component({
    selector: 'jhi-example-submission-import',
    templateUrl: './example-submission-import.component.html',
})
export class ExampleSubmissionImportComponent extends ImportComponent<Submission> {
    exercise: Exercise;

    readonly faQuestionCircle = faQuestionCircle;
    readonly ExerciseType = ExerciseType;

    constructor(
        router: Router,
        pagingService: ExampleSubmissionImportPagingService,
        sortService: SortService,
        activeModal: NgbActiveModal,
        private exampleSubmissionService: ExampleSubmissionService,
    ) {
        super(router, pagingService, sortService, activeModal);
    }

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
