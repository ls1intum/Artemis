import { Component, input } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ResultSummaryExerciseInfo } from 'app/exam/overview/summary/exam-result-summary.component';
import { SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-result-summary-exercise-card-header',
    templateUrl: './exam-result-summary-exercise-card-header.component.html',
    imports: [FaIconComponent, NgClass, TranslateDirective, ArtemisTranslatePipe],
})
export class ExamResultSummaryExerciseCardHeaderComponent {
    index = input.required<number>();
    exercise = input.required<Exercise>();
    exerciseInfo = input.required<ResultSummaryExerciseInfo>();
    resultsPublished = input.required<boolean>();

    readonly SUBMISSION_TYPE_ILLEGAL = SubmissionType.ILLEGAL;
}
