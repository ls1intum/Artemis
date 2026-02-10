import { Injectable, inject } from '@angular/core';
import { Observable, Subject, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

/**
 * Synchronization targets used to scope editor sync events.
 */
export enum ExerciseEditorSyncTarget {
    PROBLEM_STATEMENT = 'PROBLEM_STATEMENT',
    TEMPLATE_REPOSITORY = 'TEMPLATE_REPOSITORY',
    SOLUTION_REPOSITORY = 'SOLUTION_REPOSITORY',
    TESTS_REPOSITORY = 'TESTS_REPOSITORY',
    AUXILIARY_REPOSITORY = 'AUXILIARY_REPOSITORY',
    EXERCISE_METADATA = 'EXERCISE_METADATA',
}

/**
 * Discriminator for synchronization event payloads.
 */
export enum ExerciseEditorSyncEventType {
    PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST = 'PROBLEM_STATEMENT_SYNC_FULL_CONTENT_REQUEST',
    PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE = 'PROBLEM_STATEMENT_SYNC_FULL_CONTENT_RESPONSE',
    PROBLEM_STATEMENT_SYNC_UPDATE = 'PROBLEM_STATEMENT_SYNC_UPDATE',
    PROBLEM_STATEMENT_AWARENESS_UPDATE = 'PROBLEM_STATEMENT_AWARENESS_UPDATE',
    FILE_SYNC_FULL_CONTENT_REQUEST = 'FILE_SYNC_FULL_CONTENT_REQUEST',
    FILE_SYNC_FULL_CONTENT_RESPONSE = 'FILE_SYNC_FULL_CONTENT_RESPONSE',
    FILE_SYNC_UPDATE = 'FILE_SYNC_UPDATE',
    FILE_AWARENESS_UPDATE = 'FILE_AWARENESS_UPDATE',
    FILE_CREATED = 'FILE_CREATED',
    FILE_DELETED = 'FILE_DELETED',
    FILE_RENAMED = 'FILE_RENAMED',
    NEW_COMMIT_ALERT = 'NEW_COMMIT_ALERT',
    NEW_EXERCISE_VERSION_ALERT = 'NEW_EXERCISE_VERSION_ALERT',
}

/**
 * Shared fields for synchronization events received over websocket.
 */
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

export interface FileSyncFullContentRequestEvent extends ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_REQUEST;
    filePath: string;
    requestId: string;
    auxiliaryRepositoryId?: number;
}

export interface FileSyncFullContentResponseEvent extends ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType.FILE_SYNC_FULL_CONTENT_RESPONSE;
    filePath: string;
    responseTo: string;
    yjsUpdate: string;
    leaderTimestamp: number;
    auxiliaryRepositoryId?: number;
}

export interface FileSyncUpdateEvent extends ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType.FILE_SYNC_UPDATE;
    filePath: string;
    yjsUpdate: string;
    auxiliaryRepositoryId?: number;
}

export interface FileAwarenessUpdateEvent extends ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType.FILE_AWARENESS_UPDATE;
    filePath: string;
    awarenessUpdate: string;
    auxiliaryRepositoryId?: number;
}

export interface FileCreatedEvent extends ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType.FILE_CREATED;
    filePath: string;
    fileType: 'FILE' | 'FOLDER';
    auxiliaryRepositoryId?: number;
}

export interface FileDeletedEvent extends ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType.FILE_DELETED;
    filePath: string;
    fileType: 'FILE' | 'FOLDER';
    auxiliaryRepositoryId?: number;
}

export interface FileRenamedEvent extends ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType.FILE_RENAMED;
    oldPath: string;
    newPath: string;
    fileType: 'FILE' | 'FOLDER';
    auxiliaryRepositoryId?: number;
}

/**
 * Event payload indicating a repository commit was pushed.
 */
export interface ExerciseNewCommitAlertEvent {
    eventType: ExerciseEditorSyncEventType.NEW_COMMIT_ALERT;
    target: ExerciseEditorSyncTarget;
    auxiliaryRepositoryId?: number;
    sessionId?: string;
    timestamp?: number;
}

/**
 * Event payload indicating a new exercise version (metadata) was saved.
 */
export interface ExerciseNewVersionAlertEvent {
    eventType: ExerciseEditorSyncEventType.NEW_EXERCISE_VERSION_ALERT;
    target: ExerciseEditorSyncTarget;
    exerciseVersionId: number;
    author: UserPublicInfoDTO;
    changedFields?: string[];
    sessionId?: string;
    timestamp?: number;
}

/**
 * Union of all synchronization events received by the editor.
 */
export type ExerciseEditorSyncEvent =
    | ProblemStatementSyncFullContentRequestEvent
    | ProblemStatementSyncFullContentResponseEvent
    | ProblemStatementSyncUpdateEvent
    | ProblemStatementAwarenessUpdateEvent
    | FileSyncFullContentRequestEvent
    | FileSyncFullContentResponseEvent
    | FileSyncUpdateEvent
    | FileAwarenessUpdateEvent
    | FileCreatedEvent
    | FileDeletedEvent
    | FileRenamedEvent
    | ExerciseNewVersionAlertEvent
    | ExerciseNewCommitAlertEvent;

/**
 * Maps a RepositoryType to the corresponding ExerciseEditorSyncTarget.
 */
export function repositoryTypeToSyncTarget(repoType: RepositoryType): ExerciseEditorSyncTarget {
    switch (repoType) {
        case RepositoryType.TEMPLATE:
            return ExerciseEditorSyncTarget.TEMPLATE_REPOSITORY;
        case RepositoryType.SOLUTION:
            return ExerciseEditorSyncTarget.SOLUTION_REPOSITORY;
        case RepositoryType.TESTS:
            return ExerciseEditorSyncTarget.TESTS_REPOSITORY;
        case RepositoryType.AUXILIARY:
            return ExerciseEditorSyncTarget.AUXILIARY_REPOSITORY;
        default:
            throw new Error(`No sync target mapping for repository type: ${repoType}`);
    }
}

/**
 * Subscribes to exercise editor synchronization events and filters loopback updates.
 */
@Injectable({ providedIn: 'root' })
export class ExerciseEditorSyncService {
    private websocketService = inject(WebsocketService);
    private browserFingerprintService = inject(BrowserFingerprintService);

    private exerciseId?: number;
    private subject: Subject<ExerciseEditorSyncEvent> | undefined;
    private subscription: Subscription | undefined;

    /**
     * Builds the websocket topic for a given exercise id.
     *
     * @param exerciseId the exercise id
     * @returns the websocket topic string
     */
    private getTopic(exerciseId: number): string {
        return `/topic/exercises/${exerciseId}/synchronization`;
    }

    /**
     * Returns the current browser tab session id used to filter out loopback events.
     */
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

    /**
     * Subscribes to websocket updates for an exercise and returns a stream of sync events.
     *
     * @param exerciseId the exercise id to subscribe to
     * @returns observable of incoming synchronization events
     */
    subscribeToUpdates(exerciseId: number): Observable<ExerciseEditorSyncEvent> {
        if (this.subject && this.exerciseId !== undefined && this.exerciseId !== exerciseId) {
            this.unsubscribe();
        }
        if (!this.subject) {
            const topic = this.getTopic(exerciseId);
            this.exerciseId = exerciseId;
            this.subject = new Subject<ExerciseEditorSyncEvent>();
            this.subscription = this.websocketService
                .subscribe(topic)
                .pipe(
                    filter((message: ExerciseEditorSyncEvent) => {
                        if (!message.sessionId || !this.sessionId) {
                            return true;
                        }
                        return message.sessionId !== this.sessionId;
                    }),
                )
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

        // Unsubscribing the RxJS subscription also tears down the underlying STOMP topic subscription.
        if (this.subscription) {
            this.subscription.unsubscribe();
            delete this.subscription;
        }
        delete this.exerciseId;
    }

    /**
     * Emits a received websocket message to observers.
     *
     * @param message the incoming synchronization event
     */
    private handleIncomingMessage(message: ExerciseEditorSyncEvent) {
        this.subject?.next(message);
    }
}
