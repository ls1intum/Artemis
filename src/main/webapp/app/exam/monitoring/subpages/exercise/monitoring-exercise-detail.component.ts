import { Component, Input } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-monitoring-exercise-detail',
    templateUrl: './monitoring-exercise-detail.component.html',
})
export class MonitoringExerciseDetailComponent {
    @Input() exercise: Exercise;
    @Input() exam: Exam;

    constructor() {}
}
