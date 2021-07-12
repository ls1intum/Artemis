import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface ExamExerciseUpdate {
    exerciseId: number;
    problemStatement: string;
}

@Injectable({ providedIn: 'root' })
export class ExamExerciseUpdateService {
    private examExerciseIdAndProblemStatementSource = new BehaviorSubject<ExamExerciseUpdate>({ exerciseId: -1, problemStatement: '' });
    currentExerciseIdAndProblemStatement = this.examExerciseIdAndProblemStatementSource.asObservable();

    constructor() {}

    navigateToExamExercise(exerciseId: number) {
        this.examExerciseIdAndProblemStatementSource.next({ exerciseId, problemStatement: '' });
    }

    updateLiveExamExercise(exerciseId: number, problemStatement: string) {
        debugger;
        this.examExerciseIdAndProblemStatementSource.next({ exerciseId, problemStatement });
    }
}
