import { Component } from '@angular/core';
import { ImportComponent } from 'app/shared/import/import.component';
import { SortService } from 'app/shared/service/sort.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Submission } from 'app/entities/submission.model';
import { debounceTime, switchMap, tap } from 'rxjs/operators';
import { Subject } from 'rxjs';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExampleSubmissionImportPagingService } from 'app/exercises/shared/example-submission/example-submission-import/example-submission-import-paging.service';
import { Router } from '@angular/router';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

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

    override performSearch(searchSubject: Subject<void>, debounce: number) {
        searchSubject
            .pipe(
                debounceTime(debounce),
                tap(() => (this.loading = true)),
                switchMap(() => this.pagingService.search(this.state, { exerciseId: this.exercise.id! })),
            )
            .subscribe((resp) => {
                this.content = resp;
                this.loading = false;
                this.total = resp.numberOfPages * this.state.pageSize;
                this.content?.resultsOnPage?.forEach((submission) => {
                    submission.submissionSize = this.exampleSubmissionService.getSubmissionSize(submission, this.exercise);
                });
            });
    }
}
