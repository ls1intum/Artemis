import { Component, OnDestroy, OnInit } from '@angular/core';
import { Exercise, ExerciseCategory } from 'app/entities/exercise';
import { Result } from 'app/entities/result';

@Component({
    selector: 'jhi-ide-programming-exercise-details',
    templateUrl: './ide-programming-exercise-details.component.html',
    styles: [],
})
export class IdeProgrammingExerciseDetailsComponent implements OnInit, OnDestroy {
    courseId: number;
    exercise: Exercise | null;
    showMoreResults = false;
    sortedResults: Result[] = [];
    sortedHistoryResults: Result[];
    exerciseCategories: ExerciseCategory;

    constructor() {}

    ngOnInit() {}

    ngOnDestroy(): void {}
}
