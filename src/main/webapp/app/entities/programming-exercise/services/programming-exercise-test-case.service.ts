import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, filter, switchMap, tap } from 'rxjs/operators';
import { SERVER_API_URL } from 'app/app.constants';
import { ProgrammingExerciseTestCase } from '../programming-exercise-test-case.model';
import { JhiWebsocketService } from 'app/core';

export interface IProgrammingExerciseTestCaseService {
    subscribeForTestCases(exerciseId: number): Observable<ProgrammingExerciseTestCase[]>;
}

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseTestCaseService implements IProgrammingExerciseTestCaseService, OnDestroy {
    public testCaseUrl = `${SERVER_API_URL}api/programming-exercises/test-cases/`;

    private connections: { [exerciseId: string]: string } = {};
    private subjects: { [exerciseId: string]: BehaviorSubject<ProgrammingExerciseTestCase[]> } = {};

    constructor(private jhiWebsocketService: JhiWebsocketService, private http: HttpClient) {}

    ngOnDestroy(): void {
        Object.values(this.connections).forEach(connection => this.jhiWebsocketService.unsubscribe(connection));
    }

    subscribeForTestCases(exerciseId: number) {
        if (this.subjects[exerciseId]) {
            return this.subjects[exerciseId] as Observable<ProgrammingExerciseTestCase[]>;
        } else {
            return this.getTestCases(exerciseId).pipe(
                catchError(() => of(null)),
                switchMap(testCases => this.initTestCaseSubscription(exerciseId, testCases)),
            );
        }
    }

    private getTestCases(exerciseId: number): Observable<string[]> {
        return this.http.get<string[]>(`${this.testCaseUrl}/${exerciseId}`);
    }

    private initTestCaseSubscription(exerciseId: number, initialValue: ProgrammingExerciseTestCase[] | null) {
        const testCaseTopic = `/topic/programming-exercise/${exerciseId}/test-cases`;
        this.jhiWebsocketService.subscribe(testCaseTopic);
        this.connections[exerciseId] = testCaseTopic;
        this.subjects[exerciseId] = new BehaviorSubject(initialValue);
        this.jhiWebsocketService
            .receive(testCaseTopic)
            .pipe(
                filter(testCases => !!testCases),
                tap(testCases => this.notifySubscribers(exerciseId, testCases)),
            )
            .subscribe();
        return this.subjects[exerciseId];
    }

    private notifySubscribers(exerciseId: number, testCases: ProgrammingExerciseTestCase[]) {
        this.subjects[exerciseId].next(testCases);
    }
}
