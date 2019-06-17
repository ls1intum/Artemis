import { BehaviorSubject, Observable, of } from 'rxjs';
import { IProgrammingExerciseTestCaseService } from 'app/entities/programming-exercise';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise/programming-exercise-test-case.model';

export class MockProgrammingExerciseTestCaseService implements IProgrammingExerciseTestCaseService {
    private subject: BehaviorSubject<ProgrammingExerciseTestCase[]>;

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
}
