import { Component, input } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ResultSummaryExerciseInfo } from 'app/exam/overview/summary/exam-result-summary.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-result-summary-exercise-card-header',
    templateUrl: './exam-result-summary-exercise-card-header.component.html',
    imports: [FaIconComponent, NgClass, ArtemisTranslatePipe],
})
export class ExamResultSummaryExerciseCardHeaderComponent {
    readonly index = input<number>(undefined!);
    readonly exercise = input<Exercise>(undefined!);
    readonly exerciseInfo = input<ResultSummaryExerciseInfo>();
    readonly resultsPublished = input<boolean>(undefined!);
}
