import { Injectable, inject } from '@angular/core';
import { Observable, Subject, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';
import { UserPublicInfoDTO } from 'app/core/user/user.model';

export enum ExerciseEditorSyncTarget {
    PROBLEM_STATEMENT = 'PROBLEM_STATEMENT',
    TEMPLATE_REPOSITORY = 'TEMPLATE_REPOSITORY',
    SOLUTION_REPOSITORY = 'SOLUTION_REPOSITORY',
    TESTS_REPOSITORY = 'TESTS_REPOSITORY',
    AUXILIARY_REPOSITORY = 'AUXILIARY_REPOSITORY',
    EXERCISE_METADATA = 'EXERCISE_METADATA',
}

export enum ExerciseEditorSyncEventType {
    NEW_COMMIT_ALERT = 'NEW_COMMIT_ALERT',
    NEW_EXERCISE_VERSION_ALERT = 'NEW_EXERCISE_VERSION_ALERT',
}

export interface ExerciseEditorSyncEventBase {
    eventType: ExerciseEditorSyncEventType;
    target: ExerciseEditorSyncTarget;
    sessionId?: string;
    timestamp?: number;
}

export interface ExerciseNewCommitAlertEvent {
    eventType: ExerciseEditorSyncEventType.NEW_COMMIT_ALERT;
    target: ExerciseEditorSyncTarget;
    auxiliaryRepositoryId?: number;
    sessionId?: string;
    timestamp?: number;
}
export interface ExerciseNewVersionAlertEvent {
    eventType: ExerciseEditorSyncEventType.NEW_EXERCISE_VERSION_ALERT;
    target: ExerciseEditorSyncTarget;
    exerciseVersionId: number;
    author: UserPublicInfoDTO;
    changedFields?: string[];
    sessionId?: string;
}

export type ExerciseEditorSyncEvent = ExerciseNewVersionAlertEvent | ExerciseNewCommitAlertEvent;

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
