import { Injectable, inject } from '@angular/core';
import { Observable, Subject, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { FileType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';

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
    newCommitAlert?: boolean;
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
export class ProgrammingExerciseEditorSyncService {
    private websocketService = inject(WebsocketService);
    private browserFingerprintService = inject(BrowserFingerprintService);

    private exerciseId?: number;
    private subject: Subject<ProgrammingExerciseEditorSyncMessage> | undefined;
    private subscription: Subscription | undefined;

    private getTopic(exerciseId: number): string {
        return `/topic/programming-exercises/${exerciseId}/synchronization`;
    }

    get clientInstanceId(): string | undefined {
        return this.browserFingerprintService.instanceIdentifier.value;
    }

    sendSynchronizationUpdate(exerciseId: number, message: ProgrammingExerciseEditorSyncMessage): void {
        if (!this.subscription) {
            throw new Error('Cannot send synchronization message: not subscribed to websocket topic');
        }
        const topic = this.getTopic(exerciseId);
        this.websocketService.send(topic, { ...message, timestamp: message.timestamp ?? Date.now(), clientInstanceId: this.clientInstanceId });
    }

    subscribeToUpdates(exerciseId: number): Observable<ProgrammingExerciseEditorSyncMessage> {
        if (!this.subject) {
            const topic = this.getTopic(exerciseId);
            this.websocketService.subscribe(topic);
            this.exerciseId = exerciseId;
            this.subject = new Subject<ProgrammingExerciseEditorSyncMessage>();
            this.subscription = this.websocketService
                .receive(topic)
                .pipe(filter((message: ProgrammingExerciseEditorSyncMessage) => message.clientInstanceId !== this.clientInstanceId))
                .subscribe((message: ProgrammingExerciseEditorSyncMessage) => this.subject!.next(message));
        }
        return this.subject;
    }

    /**
     * Unsubscribe from synchronization updates for a specific exercise.
     * Should be called when leaving the code editor for an exercise.
     */
    unsubscribe(): void {
        // Unsubscribe from websocket connection
        if (this.exerciseId) {
            this.websocketService.unsubscribe(this.getTopic(this.exerciseId));
        }

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
}
