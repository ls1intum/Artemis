import { Injectable, inject } from '@angular/core';
import { Observable, Subject, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';

export enum ExerciseEditorSyncTarget {
    PROBLEM_STATEMENT = 'PROBLEM_STATEMENT',
}

export enum ExerciseEditorSyncEventType {
    PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST = 'PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST',
    PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE = 'PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE',
    PROBLEM_STATEMENT_SYNC_UPDATE = 'PROBLEM_STATEMENT_SYNC_UPDATE',
    PROBLEM_STATEMENT_AWARENESS_UPDATE = 'PROBLEM_STATEMENT_AWARENESS_UPDATE',
}

export interface ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType;
    target: ExerciseEditorSyncTarget;
    sessionId?: string;
    timestamp?: number;
}

export interface ProblemStatementSyncFullContentRequestEvent extends ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST;
    requestId: string;
}

export interface ProblemStatementSyncFullContentResponseEvent extends ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE;
    responseTo: string;
    yjsUpdate: string;
    leaderTimestamp: number;
}

export interface ProblemStatementSyncUpdateEvent extends ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE;
    yjsUpdate: string;
}

export interface ProblemStatementAwarenessUpdateEvent extends ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType.PROBLEM_STATEMENT_AWARENESS_UPDATE;
    awarenessUpdate: string;
}

export type ExerciseEditorSyncEvent =
    | ProblemStatementSyncFullContentRequestEvent
    | ProblemStatementSyncFullContentResponseEvent
    | ProblemStatementSyncUpdateEvent
    | ProblemStatementAwarenessUpdateEvent;

@Injectable({ providedIn: 'root' })
export class ExerciseEditorSyncService {
    private websocketService = inject(WebsocketService);
    private browserFingerprintService = inject(BrowserFingerprintService);

    private exerciseId?: number;
    private subject: Subject<ExerciseEditorSyncEvent> | undefined;
    private subscription: Subscription | undefined;

    private getTopic(exerciseId: number): string {
        return `/topic/exercises/${exerciseId}/synchronization`;
    }

    get sessionId(): string | undefined {
        return this.browserFingerprintService.browserSessionId.value;
    }

    sendSynchronizationUpdate(exerciseId: number, message: ExerciseEditorSyncEvent): void {
        if (!this.subscription) {
            throw new Error('Cannot send synchronization message: not subscribed to websocket topic');
        }
        const topic = this.getTopic(exerciseId);
        this.websocketService.send(topic, { ...message, timestamp: message.timestamp ?? Date.now(), sessionId: this.sessionId });
    }

    subscribeToUpdates(exerciseId: number): Observable<ExerciseEditorSyncEvent> {
        if (!this.subject) {
            const topic = this.getTopic(exerciseId);
            this.exerciseId = exerciseId;
            this.subject = new Subject<ExerciseEditorSyncEvent>();
            this.subscription = this.websocketService
                .subscribe(topic)
                .pipe(filter((message: ExerciseEditorSyncEvent) => message.sessionId !== this.sessionId))
                .subscribe((message: ExerciseEditorSyncEvent) => this.handleIncomingMessage(message));
        }
        return this.subject;
    }

    /**
     * Unsubscribe from synchronization updates for a specific exercise.
     * Should be called when leaving the code editor for an exercise.
     */
    unsubscribe(): void {
        // Complete the Subject to notify all observers that the stream has ended
        if (this.subject) {
            this.subject.complete();
            delete this.subject;
        }

        // Unsubscribe from internal websocket subscription
        if (this.subscription) {
            this.subscription.unsubscribe();
            delete this.subscription;
        }
        delete this.exerciseId;
    }

    private handleIncomingMessage(message: ExerciseEditorSyncEvent) {
        this.subject?.next(message);
    }
}
