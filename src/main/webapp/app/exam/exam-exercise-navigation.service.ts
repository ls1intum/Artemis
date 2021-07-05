import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ExamExerciseNavigationService {
    private exerciseIdToNavigateToSource = new BehaviorSubject(-1);
    currentExerciseIdToNavigateTo = this.exerciseIdToNavigateToSource.asObservable();

    constructor() {}

    navigateToExamExercise(exerciseId: number) {
        this.exerciseIdToNavigateToSource.next(exerciseId);
    }
}
