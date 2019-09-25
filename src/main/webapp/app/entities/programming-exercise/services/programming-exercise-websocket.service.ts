import { Injectable, OnDestroy } from '@angular/core';
import * as moment from 'moment';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { omit as _omit } from 'lodash';
import { SERVER_API_URL } from 'app/app.constants';

import { ProgrammingExercise } from '../programming-exercise.model';
import { createRequestOption } from 'app/shared';
import { ExerciseService } from 'app/entities/exercise';
import { SolutionProgrammingExerciseParticipation, TemplateProgrammingExerciseParticipation } from 'app/entities/participation';
import { JhiWebsocketService } from 'app/core';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise/programming-exercise-test-case.model';
import { BehaviorSubject } from 'rxjs';
import { tap, filter } from 'rxjs/operators';

export type EntityResponseType = HttpResponse<ProgrammingExercise>;
export type EntityArrayResponseType = HttpResponse<ProgrammingExercise[]>;

export type ReleaseStateDTO = { released: boolean; hasStudentResult: boolean; testCasesChanged: boolean };

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseWebsocketService implements OnDestroy {
    private connections: string[] = [];
    // Uses undefined for initial value.
    private subjects: { [programmingExerciseId: number]: BehaviorSubject<boolean | undefined> } = {};

    constructor(private websocketService: JhiWebsocketService) {}

    /**
     * On destroy unsubscribe all connections.
     */
    ngOnDestroy(): void {
        Object.values(this.connections).forEach(connection => this.websocketService.unsubscribe(connection));
    }

    private notifySubscribers(programmingExerciseId: number, testCasesChanged: boolean) {
        this.subjects[programmingExerciseId].next(testCasesChanged);
    }

    private initTestCaseStateSubscription(programmingExerciseId: number) {
        const testCaseTopic = `/topic/programming-exercise/${programmingExerciseId}/test-cases-changed`;
        this.websocketService.subscribe(testCaseTopic);
        this.connections[programmingExerciseId] = testCaseTopic;
        this.subjects[programmingExerciseId] = new BehaviorSubject(undefined);
        this.websocketService
            .receive(testCaseTopic)
            .pipe(tap(testCasesChanged => this.notifySubscribers(programmingExerciseId, testCasesChanged)))
            .subscribe();
        return this.subjects[programmingExerciseId];
    }

    getTestCaseState(programmingExerciseId: number) {
        const existingSubject = this.subjects[programmingExerciseId];
        return (existingSubject || this.initTestCaseStateSubscription(programmingExerciseId)).asObservable().pipe(filter(val => val !== undefined));
    }
}
