import { Component, Input } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { ResultSummaryExerciseInfo } from 'app/exam/participate/summary/exam-result-summary.component';
import { SubmissionType } from 'app/entities/submission.model';

@Component({
    selector: 'jhi-result-summary-exercise-card-header',
    templateUrl: './exam-result-summary-exercise-card-header.component.html',
    styleUrls: ['./exam-result-summary-exercise-card-header.component.scss'],
})
export class ExamResultSummaryExerciseCardHeaderComponent {
    @Input() index: number;
    @Input() exercise: Exercise;
    @Input() exerciseInfo?: ResultSummaryExerciseInfo;
    @Input() resultsPublished: boolean;

    faAngleRight = faAngleRight;

    toggleCollapseExercise() {
        this.exerciseInfo!.isCollapsed = !this.exerciseInfo!.isCollapsed;
    }

    readonly SUBMISSION_TYPE_ILLEGAL = SubmissionType.ILLEGAL;
}
