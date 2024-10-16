import { Injectable, OnDestroy, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ProgrammingExerciseTestCase, Visibility } from 'app/entities/programming/programming-exercise-test-case.model';
import { StaticCodeAnalysisCategory } from 'app/entities/programming/static-code-analysis-category.model';
import { ProgrammingExerciseGradingStatistics } from 'app/entities/programming/programming-exercise-test-case-statistics.model';

export class ProgrammingExerciseTestCaseUpdate {
    constructor(
        public id?: number,
        public weight?: number,
        public bonusPoints?: number,
        public bonusMultiplier?: number,
        public visibility?: Visibility,
    ) {}

    static from(testCase: ProgrammingExerciseTestCase) {
        return new ProgrammingExerciseTestCaseUpdate(testCase.id, testCase.weight, testCase.bonusPoints, testCase.bonusMultiplier, testCase.visibility);
    }
}
export class StaticCodeAnalysisCategoryUpdate {
    constructor(
        public id?: number,
        public penalty?: number,
        public maxPenalty?: number,
        public state?: string,
    ) {}

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
    importCategoriesFromExercise(targetExerciseId: number, sourceExerciseId: number): Observable<StaticCodeAnalysisCategory[]>;
}

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseGradingService implements IProgrammingExerciseGradingService, OnDestroy {
    private jhiWebsocketService = inject(JhiWebsocketService);
    private http = inject(HttpClient);

    public resourceUrl = 'api/programming-exercises';

    private connections: { [exerciseId: string]: string } = {};
    private subjects: { [exerciseId: string]: BehaviorSubject<ProgrammingExerciseTestCase[] | undefined> } = {};
    private testCases: Map<number, ProgrammingExerciseTestCase[]> = new Map();

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
        }
        return this.getTestCases(exerciseId).pipe(
            map((testCases) => (testCases.length ? testCases : undefined)),
            catchError(() => of(undefined)),
            switchMap((testCases: ProgrammingExerciseTestCase[] | undefined) => {
                if (testCases) {
                    this.testCases.set(exerciseId, testCases);
                }
                return this.initTestCaseSubscription(exerciseId, testCases);
            }),
        );
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
    public getTestCases(exerciseId: number): Observable<ProgrammingExerciseTestCase[]> {
        if (this.testCases.has(exerciseId)) {
            return of(this.testCases.get(exerciseId)!);
        }
        return this.http.get<ProgrammingExerciseTestCase[]>(`${this.resourceUrl}/${exerciseId}/test-cases`).pipe(
            tap((testCases) => {
                this.testCases.set(exerciseId, testCases);
            }),
        );
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
     * Use with care: Resets all test cases of an exercise to their initial configuration
     * Set all test case weights to 1, all bonus multipliers to 1, all bonus points to 0 and visibility to always.
     *
     * @param exerciseId the id of the exercise to reset the test case weights of.
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
                tap((testCases) => {
                    if (testCases) {
                        this.testCases.set(exerciseId, testCases);
                    }
                    this.notifySubscribers(exerciseId, testCases);
                }),
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

    /**
     * Imports an existing SCA configuration (defined in the sourceExercise) into the targetExercise
     * by comping all categories.
     * @param targetExerciseId The exercise to copy the categories in
     * @param sourceExerciseId The exercise from where to copy the categories
     * @return The new categories
     */
    public importCategoriesFromExercise(targetExerciseId: number, sourceExerciseId: number): Observable<StaticCodeAnalysisCategory[]> {
        const params = new HttpParams().set('sourceExerciseId', sourceExerciseId);
        return this.http.patch<StaticCodeAnalysisCategory[]>(`${this.resourceUrl}/${targetExerciseId}/static-code-analysis-categories/import`, {}, { params });
    }
}
