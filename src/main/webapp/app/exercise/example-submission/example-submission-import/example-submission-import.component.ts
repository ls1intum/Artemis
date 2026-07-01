import { Component, inject, input } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { getLatestResultOfStudentParticipation } from 'app/exercise/participation/participation.utils';
import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { ImportComponent } from 'app/shared-ui/import/import.component';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { SortByDirective } from 'app/foundation/sort/directive/sort-by.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { PaginatorModule } from 'primeng/paginator';
import { ResultComponent } from '../../result/result.component';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
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
        PaginatorModule,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
    ],
})
export class ExampleSubmissionImportComponent extends ImportComponent<Submission> {
    private exampleSubmissionService = inject(ExampleSubmissionService);

    readonly exercise = input.required<Exercise>();

    readonly faQuestionCircle = faQuestionCircle;
    readonly ExerciseType = ExerciseType;

    constructor() {
        const pagingService = inject(ExampleSubmissionImportPagingService);
        super(pagingService);
    }

    get searchTermEntered() {
        return !!(this.state?.searchTerm?.length && this.state.searchTerm.length > 0);
    }

    /**
     * Resolve the result to display for a submission's participation. ResultComponent is presentational and no longer
     * picks a result itself, so callers that only have a participation resolve it here via the shared helper.
     */
    getLatestResult(submission: Submission): Result | undefined {
        return getLatestResultOfStudentParticipation(submission.participation as StudentParticipation | undefined, false);
    }

    protected override onSearchResult() {
        const exercise = this.exercise();
        this.content()?.resultsOnPage?.forEach((submission) => {
            submission.submissionSize = this.exampleSubmissionService.getSubmissionSize(submission, exercise);
        });
    }

    protected override createOptions(): object | undefined {
        return { exerciseId: this.exercise().id! };
    }

    /**
     * Dismisses the modal dialog without any action.
     */
    dismiss(): void {
        this.dialogRef?.close();
    }
}
