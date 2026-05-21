import { Component, inject } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { ImportComponent } from 'app/shared/import/import.component';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbActiveModal, NgbPagination, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ResultComponent } from '../../result/result.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExampleSubmissionImportPagingService } from 'app/exercise/example-submission/example-submission-import/example-submission-import-paging.service';

@Component({
    selector: 'jhi-example-submission-import',
    templateUrl: './example-submission-import.component.html',
    imports: [
        FormsModule,
        TranslateDirective,
        SortDirective,
        SortByDirective,
        FaIconComponent,
        NgbTooltip,
        ResultComponent,
        ButtonComponent,
        NgbPagination,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
    ],
})
export class ExampleSubmissionImportComponent extends ImportComponent<Submission> {
    private exampleSubmissionService = inject(ExampleSubmissionService);
    private activeModal = inject(NgbActiveModal);

    exercise: Exercise;

    readonly faQuestionCircle = faQuestionCircle;
    readonly ExerciseType = ExerciseType;

    constructor() {
        const pagingService = inject(ExampleSubmissionImportPagingService);
        super(pagingService);
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

    /**
     * Dismisses the modal dialog without any action.
     */
    dismiss(): void {
        this.activeModal.dismiss();
    }
}
