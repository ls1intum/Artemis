import { Injectable } from '@angular/core';
import { BehaviorSubject, Subject } from 'rxjs';

export interface ExamExerciseUpdate {
    exerciseId: number;
    problemStatement: string;
}

@Injectable({ providedIn: 'root' })
export class ExamExerciseUpdateService {
    private examExerciseIdAndProblemStatementSource = new Subject<ExamExerciseUpdate>();
    currentExerciseIdAndProblemStatement = this.examExerciseIdAndProblemStatementSource.asObservable();

    private examExerciseIdForNavigationSource = new BehaviorSubject<number>(-1);
    currentExerciseIdForNavigation = this.examExerciseIdForNavigationSource.asObservable();

    navigateToExamExercise(exerciseId: number) {
        this.examExerciseIdForNavigationSource.next(exerciseId);
    }

    updateLiveExamExercise(exerciseId: number, problemStatement: string) {
        this.examExerciseIdAndProblemStatementSource.next({ exerciseId, problemStatement });
    }
}
