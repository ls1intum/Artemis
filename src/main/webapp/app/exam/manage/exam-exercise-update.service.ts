import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface ExamExerciseUpdate {
    exerciseId: number;
    problemStatement: string;
}

@Injectable({ providedIn: 'root' })
export class ExamExerciseUpdateService {
    private examExerciseIdAndProblemStatementSource = new BehaviorSubject<ExamExerciseUpdate>({ exerciseId: -1, problemStatement: 'initialProblemStatementValue' });
    currentExerciseIdAndProblemStatement = this.examExerciseIdAndProblemStatementSource.asObservable();

    private examExerciseIdForNavigationSource = new BehaviorSubject<number>(-1);
    currentExerciseIdForNavigation = this.examExerciseIdForNavigationSource.asObservable();

    constructor() {}

    navigateToExamExercise(exerciseId: number) {
        this.examExerciseIdForNavigationSource.next(exerciseId);
    }

    updateLiveExamExercise(exerciseId: number, problemStatement: string) {
        this.examExerciseIdAndProblemStatementSource.next({ exerciseId, problemStatement });
    }
}
