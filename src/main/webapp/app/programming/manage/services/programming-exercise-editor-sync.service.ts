import { Injectable, inject } from '@angular/core';
import { Observable, Subject, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';
import { AlertService } from 'app/shared/service/alert.service';

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

export enum ProgrammingExerciseEditorSyncEventType {
    PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST = 'PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST',
    PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE = 'PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE',
    PROBLEM_STATEMENT_SYNC_UPDATE = 'PROBLEM_STATEMENT_SYNC_UPDATE',
    PROBLEM_STATEMENT_AWARENESS_UPDATE = 'PROBLEM_STATEMENT_AWARENESS_UPDATE',
    NEW_COMMIT_ALERT = 'NEW_COMMIT_ALERT',
}

export interface ProgrammingExerciseEditorSyncEventBase {
    eventType: ProgrammingExerciseEditorSyncEventType;
    target: ProgrammingExerciseEditorSyncTarget;
    clientInstanceId?: string;
    timestamp?: number;
}

export interface ProblemStatementSyncFullContentRequestEvent extends ProgrammingExerciseEditorSyncEventBase {
    eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST;
    requestId: string;
}

export interface ProblemStatementSyncFullContentResponseEvent extends ProgrammingExerciseEditorSyncEventBase {
    eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE;
    responseTo: string;
    yjsUpdate: string;
    leaderTimestamp: number;
}

export interface ProblemStatementSyncUpdateEvent extends ProgrammingExerciseEditorSyncEventBase {
    eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_SYNC_UPDATE;
    yjsUpdate: string;
}

export interface ProblemStatementAwarenessUpdateEvent extends ProgrammingExerciseEditorSyncEventBase {
    eventType: ProgrammingExerciseEditorSyncEventType.PROBLEM_STATEMENT_AWARENESS_UPDATE;
    awarenessUpdate: string;
}

export interface ProgrammingExerciseNewCommitAlertEvent {
    eventType: ProgrammingExerciseEditorSyncEventType.NEW_COMMIT_ALERT;
    target: ProgrammingExerciseEditorSyncTarget;
    auxiliaryRepositoryId?: number;
    clientInstanceId?: string;
    timestamp?: number;
}

export type ProgrammingExerciseEditorSyncEvent =
    | ProblemStatementSyncFullContentRequestEvent
    | ProblemStatementSyncFullContentResponseEvent
    | ProblemStatementSyncUpdateEvent
    | ProblemStatementAwarenessUpdateEvent
    | ProgrammingExerciseNewCommitAlertEvent;

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseEditorSyncService {
    private websocketService = inject(WebsocketService);
    private browserFingerprintService = inject(BrowserFingerprintService);
    private alertService = inject(AlertService);

    private exerciseId?: number;
    private subject: Subject<ProgrammingExerciseEditorSyncEvent> | undefined;
    private subscription: Subscription | undefined;
    private sessionClientId?: string;

    private readonly sessionClientIdKey = 'artemis.editor.sessionClientId';

    private getTopic(exerciseId: number): string {
        return `/topic/programming-exercises/${exerciseId}/synchronization`;
    }

    get clientInstanceId(): string | undefined {
        return this.ensureSessionClientId() ?? this.browserFingerprintService.browserInstanceId.value;
    }

    private ensureSessionClientId(): string | undefined {
        if (this.sessionClientId) {
            return this.sessionClientId;
        }
        if (typeof window === 'undefined' || !window.sessionStorage) {
            return undefined;
        }
        try {
            let stored = window.sessionStorage.getItem(this.sessionClientIdKey);
            if (!stored) {
                stored = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
                window.sessionStorage.setItem(this.sessionClientIdKey, stored);
            }
            this.sessionClientId = stored;
            return stored;
        } catch {
            return undefined;
        }
    }

    sendSynchronizationUpdate(exerciseId: number, message: ProgrammingExerciseEditorSyncEvent): void {
        if (!this.subscription) {
            throw new Error('Cannot send synchronization message: not subscribed to websocket topic');
        }
        const topic = this.getTopic(exerciseId);
        this.websocketService.send(topic, { ...message, timestamp: message.timestamp ?? Date.now(), clientInstanceId: this.clientInstanceId });
    }

    subscribeToUpdates(exerciseId: number): Observable<ProgrammingExerciseEditorSyncEvent> {
        if (!this.subject) {
            const topic = this.getTopic(exerciseId);
            this.exerciseId = exerciseId;
            this.subject = new Subject<ProgrammingExerciseEditorSyncEvent>();
            this.subscription = this.websocketService
                .subscribe(topic)
                .pipe(filter((message: ProgrammingExerciseEditorSyncEvent) => message.clientInstanceId !== this.clientInstanceId))
                .subscribe((message: ProgrammingExerciseEditorSyncEvent) => this.handleIncomingMessage(message));
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

    private handleIncomingMessage(message: ProgrammingExerciseEditorSyncEvent) {
        if (message.eventType === ProgrammingExerciseEditorSyncEventType.NEW_COMMIT_ALERT) {
            this.alertService.info('artemisApp.editor.synchronization.newCommitAlert');
            return;
        }
        this.subject?.next(message);
    }
}
