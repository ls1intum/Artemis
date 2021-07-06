import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

interface LiveExamExerciseUpdate {
    exerciseId: number;
    problemStatement: string;
}

@Injectable({ providedIn: 'root' })
export class ExamExerciseUpdateService {
    private examExerciseIdAndProblemStatementSource = new BehaviorSubject<LiveExamExerciseUpdate>({ exerciseId: -1, problemStatement: '' });
    currentExerciseIdAndProblemStatement = this.examExerciseIdAndProblemStatementSource.asObservable();

    constructor() {}

    navigateToExamExercise(exerciseId: number) {
        this.examExerciseIdAndProblemStatementSource.next({ exerciseId, problemStatement: '' });
    }

    updateLiveExamExercise(exerciseId: number, problemStatement: string) {
        this.examExerciseIdAndProblemStatementSource.next({ exerciseId, problemStatement });
    }
}
