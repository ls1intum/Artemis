import { Component, Input } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { ResultSummaryExerciseInfo } from 'app/exam/participate/summary/exam-result-summary.component';

@Component({
    selector: 'jhi-collapsable-card',
    templateUrl: './collapsable-card.component.html',
    styleUrls: ['../../../course/manage/course-exercise-card.component.scss', '../../../exercises/quiz/shared/quiz.scss', 'exam-result-summary.component.scss'],
})
export class CollapsableCardComponent {
    @Input() index: number;
    @Input() exercise: Exercise;
    @Input() exerciseInfo?: ResultSummaryExerciseInfo;
    @Input() resultsPublished: boolean;
    @Input() exerciseInfos: Record<number, ResultSummaryExerciseInfo>;
}
