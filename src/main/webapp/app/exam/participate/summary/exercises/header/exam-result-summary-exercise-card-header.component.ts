import { Component, Input } from '@angular/core';
import { ExerciseType } from 'app/entities/exercise.model';
import { ResultSummaryExerciseInfo } from 'app/exam/participate/summary/exam-result-summary.component';
import { SubmissionType } from 'app/entities/submission.model';

@Component({
    selector: 'jhi-result-summary-exercise-card-header',
    templateUrl: './exam-result-summary-exercise-card-header.component.html',
})
export class ExamResultSummaryExerciseCardHeaderComponent {
    @Input() index: number;
    @Input() id?: number;
    @Input() title?: string;
    @Input() exerciseType?: ExerciseType;
    @Input() maxPoints?: number;
    @Input() submissionType?: SubmissionType;
    @Input() exerciseInfo?: ResultSummaryExerciseInfo;
    @Input() resultsPublished: boolean;

    readonly SUBMISSION_TYPE_ILLEGAL = SubmissionType.ILLEGAL;
}
