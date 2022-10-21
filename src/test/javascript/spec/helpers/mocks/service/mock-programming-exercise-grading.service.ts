import { BehaviorSubject, Observable, of } from 'rxjs';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import {
    IProgrammingExerciseGradingService,
    ProgrammingExerciseTestCaseUpdate,
    StaticCodeAnalysisCategoryUpdate,
} from 'app/exercises/programming/manage/services/programming-exercise-grading.service';
import { StaticCodeAnalysisCategory } from 'app/entities/static-code-analysis-category.model';
import { ProgrammingExerciseGradingStatistics } from 'app/entities/programming-exercise-test-case-statistics.model';

export class MockProgrammingExerciseGradingService implements IProgrammingExerciseGradingService {
    private testCaseSubject = new BehaviorSubject<ProgrammingExerciseTestCase[] | undefined>(undefined);
    private categorySubject = new BehaviorSubject<StaticCodeAnalysisCategory[] | undefined>(undefined);

    subscribeForTestCases(exerciseId: number): Observable<ProgrammingExerciseTestCase[]> {
        return this.testCaseSubject as Observable<ProgrammingExerciseTestCase[]>;
    }

    initSubject(initialValue: ProgrammingExerciseTestCase[]) {
        if (this.testCaseSubject) {
            this.testCaseSubject.complete();
        }
        this.testCaseSubject = new BehaviorSubject(initialValue);
    }

    nextTestCases(value: ProgrammingExerciseTestCase[] | undefined) {
        this.testCaseSubject.next(value);
    }

    notifyTestCases(exerciseId: number, testCases: ProgrammingExerciseTestCase[]): void {
        this.testCaseSubject.next(testCases);
    }

    resetTestCases(exerciseId: number): Observable<ProgrammingExerciseTestCase[]> {
        return of();
    }

    resetCategories(exerciseId: number): Observable<StaticCodeAnalysisCategory[]> {
        return of();
    }

    updateTestCase(exerciseId: number, updates: ProgrammingExerciseTestCaseUpdate[]): Observable<ProgrammingExerciseTestCase[]> {
        return of();
    }

    getCodeAnalysisCategories(exerciseId: number): Observable<StaticCodeAnalysisCategory[]> {
        return this.categorySubject as Observable<StaticCodeAnalysisCategory[]>;
    }

    updateCodeAnalysisCategories(exerciseId: number, updates: StaticCodeAnalysisCategoryUpdate[]): Observable<StaticCodeAnalysisCategoryUpdate[]> {
        return of();
    }

    nextCategories(value: StaticCodeAnalysisCategory[]) {
        this.categorySubject.next(value);
    }

    getGradingStatistics(exerciseId: number): Observable<ProgrammingExerciseGradingStatistics> {
        return of();
    }
}
