import { BehaviorSubject, Observable, of } from 'rxjs';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import {
    IProgrammingExerciseGradingService,
    ProgrammingExerciseTestCaseUpdate,
    StaticCodeAnalysisCategoryUpdate,
} from 'app/exercises/programming/manage/services/programming-exercise-grading.service';
import { StaticCodeAnalysisCategory } from 'app/entities/static-code-analysis-category.model';

export class MockProgrammingExerciseGradingService implements IProgrammingExerciseGradingService {
    private subject = new BehaviorSubject<ProgrammingExerciseTestCase[] | undefined>(undefined);

    subscribeForTestCases(exerciseId: number): Observable<ProgrammingExerciseTestCase[]> {
        return this.subject as Observable<ProgrammingExerciseTestCase[]>;
    }

    initSubject(initialValue: ProgrammingExerciseTestCase[]) {
        if (this.subject) {
            this.subject.complete();
        }
        this.subject = new BehaviorSubject(initialValue);
    }

    next(value: ProgrammingExerciseTestCase[] | undefined) {
        this.subject.next(value);
    }

    notifyTestCases(exerciseId: number, testCases: ProgrammingExerciseTestCase[]): void {
        this.subject.next(testCases);
    }

    reset(exerciseId: number): Observable<ProgrammingExerciseTestCase[]> {
        return of();
    }

    updateTestCase(exerciseId: number, updates: ProgrammingExerciseTestCaseUpdate[]): Observable<ProgrammingExerciseTestCase[]> {
        return of();
    }

    getCodeAnalysisCategories(exerciseId: number): Observable<StaticCodeAnalysisCategory[]> {
        return of();
    }

    updateCodeAnalysisCategories(exerciseId: number, updates: StaticCodeAnalysisCategoryUpdate[]): Observable<StaticCodeAnalysisCategoryUpdate[]> {
        return of();
    }
}
