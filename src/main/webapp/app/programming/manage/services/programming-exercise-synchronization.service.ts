import { Injectable, OnDestroy, inject } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { WebsocketService } from 'app/shared/service/websocket.service';
export enum ProgrammingExerciseSynchronizationTarget {
    PROBLEM_STATEMENT = 'PROBLEM_STATEMENT',
    TEMPLATE_REPOSITORY = 'TEMPLATE_REPOSITORY',
    SOLUTION_REPOSITORY = 'SOLUTION_REPOSITORY',
    TESTS_REPOSITORY = 'TESTS_REPOSITORY',
    AUXILIARY_REPOSITORY = 'AUXILIARY_REPOSITORY',
}

export interface ProgrammingExerciseSynchronizationChange {
    target: ProgrammingExerciseSynchronizationTarget;
    auxiliaryRepositoryId?: number;
}

export interface ProgrammingExerciseSynchronizationMessage {
    target?: ProgrammingExerciseSynchronizationTarget;
    auxiliaryRepositoryId?: number;
    clientInstanceId?: string;
}

export const EDITOR_SESSION_HEADER = 'X-Artemis-Client-Instance-ID';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseSynchronizationService implements OnDestroy {
    private websocketService = inject(WebsocketService);

    private connections: Record<number, string> = {};
    private subjects: Record<number, Subject<ProgrammingExerciseSynchronizationMessage>> = {};

    ngOnDestroy(): void {
        Object.values(this.connections).forEach((connection) => this.websocketService.unsubscribe(connection));
    }

    getSynchronizationUpdates(exerciseId: number): Observable<ProgrammingExerciseSynchronizationMessage> {
        return (this.subjects[exerciseId] ?? this.initializeSynchronizationSubscription(exerciseId)).asObservable();
    }

    private initializeSynchronizationSubscription(exerciseId: number) {
        const topic = `/topic/programming-exercises/${exerciseId}/synchronization`;
        this.websocketService.subscribe(topic);
        this.connections[exerciseId] = topic;

        const subject = new Subject<ProgrammingExerciseSynchronizationMessage>();
        this.subjects[exerciseId] = subject;

        this.websocketService.receive(topic).subscribe((message: ProgrammingExerciseSynchronizationMessage) => subject.next(message));

        return subject;
    }
}
