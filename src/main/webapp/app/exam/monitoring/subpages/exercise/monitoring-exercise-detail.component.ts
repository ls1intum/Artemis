import { Component, Input } from '@angular/core';

import { Exam } from 'app/entities/exam.model';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-monitoring-exercise-detail',
    templateUrl: './monitoring-exercise-detail.component.html',
})
export class MonitoringExerciseDetailComponent {
    @Input() exercise: Exercise;
    @Input() exam: Exam;

    constructor() {}
}
