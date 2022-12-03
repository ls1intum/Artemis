import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ProgrammingExerciseTestCase, Visibility } from 'app/entities/programming-exercise-test-case.model';
import { StaticCodeAnalysisCategory } from 'app/entities/static-code-analysis-category.model';
import { ProgrammingExerciseGradingStatistics } from 'app/entities/programming-exercise-test-case-statistics.model';

export class ProgrammingExerciseTestCaseUpdate {
    constructor(public id?: number, public weight?: number, public bonusPoints?: number, public bonusMultiplier?: number, public visibility?: Visibility) {}

    static from(testCase: ProgrammingExerciseTestCase) {
        return new ProgrammingExerciseTestCaseUpdate(testCase.id, testCase.weight, testCase.bonusPoints, testCase.bonusMultiplier, testCase.visibility);
    }
}
export class StaticCodeAnalysisCategoryUpdate {
    constructor(public id?: number, public penalty?: number, public maxPenalty?: number, public state?: string) {}

    static from(category: StaticCodeAnalysisCategory) {
        return new StaticCodeAnalysisCategoryUpdate(category.id, category.penalty, category.maxPenalty, category.state);
    }
}

export interface IProgrammingExerciseGradingService {
    subscribeForTestCases(exerciseId: number): Observable<ProgrammingExerciseTestCase[] | undefined>;
    notifyTestCases(exerciseId: number, testCases: ProgrammingExerciseTestCase[]): void;
    updateTestCase(exerciseId: number, testCaseUpdates: ProgrammingExerciseTestCaseUpdate[]): Observable<ProgrammingExerciseTestCase[]>;
    resetTestCases(exerciseId: number): Observable<ProgrammingExerciseTestCase[]>;
    getCodeAnalysisCategories(exerciseId: number): Observable<StaticCodeAnalysisCategory[]>;
    updateCodeAnalysisCategories(exerciseId: number, updates: StaticCodeAnalysisCategoryUpdate[]): Observable<StaticCodeAnalysisCategoryUpdate[]>;
    resetCategories(exerciseId: number): Observable<StaticCodeAnalysisCategory[]>;
    getGradingStatistics(exerciseId: number): Observable<ProgrammingExerciseGradingStatistics>;
}

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseGradingService implements IProgrammingExerciseGradingService, OnDestroy {
    public resourceUrl = `${SERVER_API_URL}api/programming-exercises`;

    private connections: { [exerciseId: string]: string } = {};
    private subjects: { [exerciseId: string]: BehaviorSubject<ProgrammingExerciseTestCase[] | undefined> } = {};

    constructor(private jhiWebsocketService: JhiWebsocketService, private http: HttpClient) {}

    /**
     * On destroy unsubscribe all connections.
     */
    ngOnDestroy(): void {
        Object.values(this.connections).forEach((connection) => this.jhiWebsocketService.unsubscribe(connection));
    }

    /**
     * Subscribe to test case changes on the server.
     * Executes a REST request initially to get the current value, so that ideally no null value is emitted to the subscriber.
     *
     * If the result is an empty array, this will be translated into a null value.
     * This is done on purpose most likely this is an error as most programming exercises have at least one test case.
     *
     * @param exerciseId
     */
    subscribeForTestCases(exerciseId: number): Observable<ProgrammingExerciseTestCase[] | undefined> {
        if (this.subjects[exerciseId]) {
            return this.subjects[exerciseId] as Observable<ProgrammingExerciseTestCase[] | undefined>;
        } else {
            return this.getTestCases(exerciseId).pipe(
                map((testCases) => (testCases.length ? testCases : undefined)),
                catchError(() => of(undefined)),
                switchMap((testCases: ProgrammingExerciseTestCase[] | undefined) => this.initTestCaseSubscription(exerciseId, testCases)),
            );
        }
    }

    /**
     * Send new values for the test cases of an exercise to all subscribers.
     * @param exerciseId
     * @param testCases
     */
    public notifyTestCases(exerciseId: number, testCases: ProgrammingExerciseTestCase[]): void {
        if (this.subjects[exerciseId]) {
            this.subjects[exerciseId].next(testCases);
        }
    }

    /**
     * Executes a REST request to the test case endpoint.
     * @param exerciseId
     */
    private getTestCases(exerciseId: number): Observable<ProgrammingExerciseTestCase[]> {
        return this.http.get<ProgrammingExerciseTestCase[]>(`${this.resourceUrl}/${exerciseId}/test-cases`);
    }

    /**
     * Update the weights with the provided values of the test cases.
     * Needs the exercise to verify permissions on the server.
     *
     * @param exerciseId
     * @param updates dto for updating test cases to avoid setting automatic parameters (e.g. active or testName)
     */
    public updateTestCase(exerciseId: number, updates: ProgrammingExerciseTestCaseUpdate[]): Observable<ProgrammingExerciseTestCase[]> {
        return this.http.patch<ProgrammingExerciseTestCase[]>(`${this.resourceUrl}/${exerciseId}/update-test-cases`, updates);
    }

    public getGradingStatistics(exerciseId: number): Observable<ProgrammingExerciseGradingStatistics> {
        return this.http.get<ProgrammingExerciseGradingStatistics>(`${this.resourceUrl}/${exerciseId}/grading/statistics`);
    }

    /**
     * Use with care: Set all test case weights to 1, all bonus multipliers to 1 and all bonus points to 0.
     *
     * @param exerciseId
     */
    public resetTestCases(exerciseId: number): Observable<ProgrammingExerciseTestCase[]> {
        return this.http.patch<ProgrammingExerciseTestCase[]>(`${this.resourceUrl}/${exerciseId}/test-cases/reset`, {});
    }

    /**
     * Use with care: Re-evaluate the latest automatic results of all student participations.
     *
     * @param exerciseId
     */
    public reEvaluate(exerciseId: number): Observable<number> {
        return this.http.put<number>(`${this.resourceUrl}/${exerciseId}/grading/re-evaluate`, {});
    }

    /**
     * Set up the infrastructure for handling and reusing a new test case subscription.
     * @param exerciseId
     * @param initialValue
     */
    private initTestCaseSubscription(exerciseId: number, initialValue: ProgrammingExerciseTestCase[] | undefined) {
        const testCaseTopic = `/topic/programming-exercises/${exerciseId}/test-cases`;
        this.jhiWebsocketService.subscribe(testCaseTopic);
        this.connections[exerciseId] = testCaseTopic;
        this.subjects[exerciseId] = new BehaviorSubject(initialValue);
        this.jhiWebsocketService
            .receive(testCaseTopic)
            .pipe(
                map((testCases) => (testCases.length ? testCases : undefined)),
                tap((testCases) => this.notifySubscribers(exerciseId, testCases)),
            )
            .subscribe();
        return this.subjects[exerciseId];
    }

    /**
     * Notify the subscribers of the exercise specific test cases.
     * @param exerciseId
     * @param testCases
     */
    private notifySubscribers(exerciseId: number, testCases: ProgrammingExerciseTestCase[] | undefined) {
        this.subjects[exerciseId].next(testCases);
    }

    /**
     * Executes a REST request to the static code analysis endpoint.
     * @param exerciseId
     */
    public getCodeAnalysisCategories(exerciseId: number): Observable<StaticCodeAnalysisCategory[]> {
        return this.http.get<StaticCodeAnalysisCategory[]>(`${this.resourceUrl}/${exerciseId}/static-code-analysis-categories`);
    }

    /**
     * Update the fields with the provided values of the sca categories.
     * Needs the exercise to verify permissions on the server.
     *
     * @param exerciseId
     * @param updates dto for updating sca categories to avoid setting automatic parameters
     */
    public updateCodeAnalysisCategories(exerciseId: number, updates: StaticCodeAnalysisCategoryUpdate[]): Observable<StaticCodeAnalysisCategory[]> {
        return this.http.patch<StaticCodeAnalysisCategory[]>(`${this.resourceUrl}/${exerciseId}/static-code-analysis-categories`, updates);
    }

    /**
     * Restores the default static code analysis configuration for the given exercise.
     *
     * @param exerciseId
     */
    public resetCategories(exerciseId: number): Observable<StaticCodeAnalysisCategory[]> {
        return this.http.patch<StaticCodeAnalysisCategory[]>(`${this.resourceUrl}/${exerciseId}/static-code-analysis-categories/reset`, {});
    }
}
