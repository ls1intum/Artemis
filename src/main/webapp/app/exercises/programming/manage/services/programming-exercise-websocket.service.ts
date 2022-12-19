import { Injectable, OnDestroy } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { filter, tap } from 'rxjs/operators';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

export type EntityResponseType = HttpResponse<ProgrammingExercise>;
export type EntityArrayResponseType = HttpResponse<ProgrammingExercise[]>;

export interface IProgrammingExerciseWebsocketService {
    /**
     * Gets the current state of test cases of a particular programming exercise
     * True => The test cases were changed and the student submissions should be built again.
     * False => There are no outstanding test case changes, the student results are up-to-date.
     * @param programmingExerciseId of the particular programming exercise
     */
    getTestCaseState(programmingExerciseId: number): Observable<boolean>;
}

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseWebsocketService implements OnDestroy, IProgrammingExerciseWebsocketService {
    private connections: string[] = [];
    // Uses undefined for initial value.
    private subjects: { [programmingExerciseId: number]: BehaviorSubject<boolean | undefined> } = {};

    constructor(private websocketService: JhiWebsocketService) {}

    /**
     * On destroy unsubscribe all connections.
     */
    ngOnDestroy(): void {
        Object.values(this.connections).forEach((connection) => this.websocketService.unsubscribe(connection));
    }

    /**
     * Notifies the subscribers of the programming exercise if the testCases have changed
     * @param programmingExerciseId of the particular programmingExercise
     * @param testCasesChanged indicates if the testCases have changed
     */
    private notifySubscribers(programmingExerciseId: number, testCasesChanged: boolean) {
        this.subjects[programmingExerciseId].next(testCasesChanged);
    }

    /**
     * Subscribes to the testCaseChanged flag of the programming exercise.
     * @param programmingExerciseId of the particular programming exercise
     */
    private initTestCaseStateSubscription(programmingExerciseId: number) {
        const testCaseTopic = `/topic/programming-exercises/${programmingExerciseId}/test-cases-changed`;
        this.websocketService.subscribe(testCaseTopic);
        this.connections[programmingExerciseId] = testCaseTopic;
        this.subjects[programmingExerciseId] = new BehaviorSubject(undefined);
        this.websocketService
            .receive(testCaseTopic)
            .pipe(tap((testCasesChanged) => this.notifySubscribers(programmingExerciseId, testCasesChanged)))
            .subscribe();
        return this.subjects[programmingExerciseId];
    }

    /**
     * Gets the current state of test cases of a particular programming exercise
     * True => The test cases were changed and the student submissions should be built again.
     * False => There are no outstanding test case changes, the student results are up-to-date.
     * @param programmingExerciseId of the particular programming exercise
     */
    getTestCaseState(programmingExerciseId: number) {
        const existingSubject = this.subjects[programmingExerciseId];
        return (existingSubject || this.initTestCaseStateSubscription(programmingExerciseId)).asObservable().pipe(filter((val) => val !== undefined)) as Observable<boolean>;
    }
}
