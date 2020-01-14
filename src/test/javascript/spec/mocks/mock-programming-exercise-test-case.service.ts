import { BehaviorSubject, Observable, of } from 'rxjs';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise/programming-exercise-test-case.model';
import { IProgrammingExerciseTestCaseService, ProgrammingExerciseTestCaseUpdate } from 'app/entities/programming-exercise/services';

export class MockProgrammingExerciseTestCaseService implements IProgrammingExerciseTestCaseService {
    private subject = new BehaviorSubject<ProgrammingExerciseTestCase[] | null>(null);

    subscribeForTestCases(exerciseId: number): Observable<ProgrammingExerciseTestCase[]> {
        return this.subject as Observable<ProgrammingExerciseTestCase[]>;
    }

    initSubject(initialValue: ProgrammingExerciseTestCase[]) {
        if (this.subject) {
            this.subject.complete();
        }
        this.subject = new BehaviorSubject(initialValue);
    }

    next(value: ProgrammingExerciseTestCase[]) {
        this.subject.next(value);
    }

    notifyTestCases(exerciseId: number, testCases: ProgrammingExerciseTestCase[]): void {
        this.subject.next(testCases);
    }

    resetWeights(exerciseId: number): Observable<ProgrammingExerciseTestCase[]> {
        return of();
    }

    updateTestCase(exerciseId: number, updates: ProgrammingExerciseTestCaseUpdate[]): Observable<ProgrammingExerciseTestCase[]> {
        return of();
    }
}
