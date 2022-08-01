import { Component, Input } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-live-statistics-exercise-detail',
    templateUrl: './live-statistics-exercise-detail.component.html',
})
export class LiveStatisticsExerciseDetailComponent {
    @Input() exercise: Exercise;
    @Input() exam: Exam;

    constructor() {}
}
