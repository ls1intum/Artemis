import { Injectable, OnDestroy, inject } from '@angular/core';
import { Observable, Subject, Subscription } from 'rxjs';
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
    private subscriptions: Record<number, Subscription> = {};

    ngOnDestroy(): void {
        Object.values(this.connections).forEach((connection) => this.websocketService.unsubscribe(connection));
    }

    getSynchronizationUpdates(exerciseId: number): Observable<ProgrammingExerciseSynchronizationMessage> {
        return (this.subjects[exerciseId] ?? this.initializeSynchronizationSubscription(exerciseId)).asObservable();
    }

    /**
     * Unsubscribe from synchronization updates for a specific exercise.
     * Should be called when leaving the code editor for an exercise.
     *
     * @param exerciseId The ID of the exercise to unsubscribe from
     */
    unsubscribeFromExercise(exerciseId: number): void {
        // Unsubscribe from websocket connection
        if (this.connections[exerciseId]) {
            this.websocketService.unsubscribe(this.connections[exerciseId]);
            delete this.connections[exerciseId];
        }

        // Complete the Subject to notify all observers that the stream has ended
        if (this.subjects[exerciseId]) {
            this.subjects[exerciseId].complete();
            delete this.subjects[exerciseId];
        }

        // Unsubscribe from internal websocket subscription
        if (this.subscriptions[exerciseId]) {
            this.subscriptions[exerciseId].unsubscribe();
            delete this.subscriptions[exerciseId];
        }
    }

    private initializeSynchronizationSubscription(exerciseId: number) {
        const topic = `/topic/programming-exercises/${exerciseId}/synchronization`;
        this.websocketService.subscribe(topic);
        this.connections[exerciseId] = topic;

        const subject = new Subject<ProgrammingExerciseSynchronizationMessage>();
        this.subjects[exerciseId] = subject;

        const subscription = this.websocketService.receive(topic).subscribe((message: ProgrammingExerciseSynchronizationMessage) => subject.next(message));
        this.subscriptions[exerciseId] = subscription;

        return subject;
    }
}
