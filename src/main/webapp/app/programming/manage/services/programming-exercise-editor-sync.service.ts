import { Injectable, OnDestroy, inject } from '@angular/core';
import { Observable, Subject, Subscription } from 'rxjs';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { FileType } from 'app/programming/shared/code-editor/model/code-editor.model';

export enum ProgrammingExerciseEditorSyncTarget {
    PROBLEM_STATEMENT = 'PROBLEM_STATEMENT',
    TEMPLATE_REPOSITORY = 'TEMPLATE_REPOSITORY',
    SOLUTION_REPOSITORY = 'SOLUTION_REPOSITORY',
    TESTS_REPOSITORY = 'TESTS_REPOSITORY',
    AUXILIARY_REPOSITORY = 'AUXILIARY_REPOSITORY',
}

export interface ProgrammingExerciseEditorSyncChange {
    target: ProgrammingExerciseEditorSyncTarget;
    auxiliaryRepositoryId?: number;
}

export interface ProgrammingExerciseEditorSyncMessage {
    target?: ProgrammingExerciseEditorSyncTarget;
    auxiliaryRepositoryId?: number;
    clientInstanceId?: string;
    problemStatementPatch?: string;
    problemStatementFull?: string;
    problemStatementRequest?: boolean;
    filePatches?: ProgrammingExerciseEditorFileSync[];
    fileRequests?: string[];
    fileFulls?: ProgrammingExerciseEditorFileFull[];
    timestamp?: number;
}

export enum ProgrammingExerciseEditorFileChangeType {
    CONTENT = 'CONTENT',
    CREATE = 'CREATE',
    DELETE = 'DELETE',
    RENAME = 'RENAME',
}

export interface ProgrammingExerciseEditorFileSync {
    fileName: string;
    patch?: string;
    changeType?: ProgrammingExerciseEditorFileChangeType;
    newFileName?: string;
    fileType?: FileType;
}

export interface ProgrammingExerciseEditorFileFull {
    fileName: string;
    content: string;
}

export const EDITOR_SESSION_HEADER = 'X-Artemis-Client-Instance-ID';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseEditorSyncService implements OnDestroy {
    private websocketService = inject(WebsocketService);

    private connections: Record<number, string> = {};
    private subjects: Record<number, Subject<ProgrammingExerciseEditorSyncMessage>> = {};
    private subscriptions: Record<number, Subscription> = {};

    ngOnDestroy(): void {
        Object.values(this.connections).forEach((connection) => this.websocketService.unsubscribe(connection));
    }

    sendSynchronization(exerciseId: number, message: ProgrammingExerciseEditorSyncMessage): void {
        const topic = this.getOrCreateTopic(exerciseId);
        this.websocketService.send(topic, { ...message, timestamp: message.timestamp ?? Date.now() });
    }

    getSynchronizationUpdates(exerciseId: number): Observable<ProgrammingExerciseEditorSyncMessage> {
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
        const topic = this.getOrCreateTopic(exerciseId);

        const subject = new Subject<ProgrammingExerciseEditorSyncMessage>();
        this.subjects[exerciseId] = subject;

        const subscription = this.websocketService.receive(topic).subscribe((message: ProgrammingExerciseEditorSyncMessage) => subject.next(message));
        this.subscriptions[exerciseId] = subscription;

        return subject;
    }

    private getOrCreateTopic(exerciseId: number): string {
        const topic = `/topic/programming-exercises/${exerciseId}/synchronization`;
        if (!this.connections[exerciseId]) {
            this.websocketService.subscribe(topic);
            this.connections[exerciseId] = topic;
        }
        return topic;
    }
}
