import { Injectable, inject } from '@angular/core';
import { DiffMatchPatch } from 'diff-match-patch-typescript';
import { Observable, Subject, Subscription, debounceTime } from 'rxjs';
import {
    ProgrammingExerciseEditorSyncMessage,
    ProgrammingExerciseEditorSyncService,
    ProgrammingExerciseEditorSyncTarget,
} from 'app/programming/manage/services/programming-exercise-editor-sync.service';

@Injectable({ providedIn: 'root' })
export class ProblemStatementSyncService {
    private syncService = inject(ProgrammingExerciseEditorSyncService);
    private readonly dmp = new DiffMatchPatch();
    private exerciseId?: number;
    private clientInstanceId?: string;
    private syncSubscription?: Subscription;
    private outgoingSyncSubscription?: Subscription;
    private localChanges$ = new Subject<string>();
    private updatesSubject = new Subject<string>();
    updates$: Observable<string> = this.updatesSubject.asObservable();
    private lastSyncedContent = '';
    private lastProcessedTimestamp = 0;
    private hasRequestedInitialSync = false;

    init(exerciseId: number, initialContent: string, clientInstanceId: string | undefined) {
        this.dispose();
        this.exerciseId = exerciseId;
        this.clientInstanceId = clientInstanceId;
        this.lastSyncedContent = initialContent;
        this.lastProcessedTimestamp = 0;
        this.hasRequestedInitialSync = false;

        this.syncSubscription = this.syncService.getSynchronizationUpdates(exerciseId).subscribe((message) => this.handleRemoteMessage(message));
        this.outgoingSyncSubscription = this.localChanges$.pipe(debounceTime(500)).subscribe((content) => this.handleLocalChange(content));
    }

    dispose() {
        this.syncSubscription?.unsubscribe();
        this.syncSubscription = undefined;
        this.outgoingSyncSubscription?.unsubscribe();
        this.outgoingSyncSubscription = undefined;
        this.exerciseId = undefined;
        this.clientInstanceId = undefined;
        this.lastSyncedContent = '';
        this.lastProcessedTimestamp = 0;
        this.hasRequestedInitialSync = false;
    }

    queueLocalChange(content: string) {
        this.localChanges$.next(content);
    }

    handleLocalChange(content: string) {
        if (!this.exerciseId) {
            return;
        }

        const patchText = this.dmp.patch_toText(this.dmp.patch_make(this.lastSyncedContent, content));
        if (!patchText) {
            return;
        }

        this.lastSyncedContent = content;
        this.syncService.sendSynchronization(this.exerciseId, {
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementPatch: patchText,
            clientInstanceId: this.clientInstanceId,
        });
    }

    requestInitialSync() {
        if (this.hasRequestedInitialSync || !this.exerciseId) {
            return;
        }
        this.hasRequestedInitialSync = true;
        this.syncService.sendSynchronization(this.exerciseId, {
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementRequest: true,
            clientInstanceId: this.clientInstanceId,
        });
    }

    private respondWithFullContent(content: string) {
        if (!this.exerciseId) {
            return;
        }
        this.syncService.sendSynchronization(this.exerciseId, {
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementFull: content,
            clientInstanceId: this.clientInstanceId,
        });
    }

    private handleRemoteMessage(message: ProgrammingExerciseEditorSyncMessage) {
        if (message.target !== ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT) {
            return;
        }
        if (message.clientInstanceId && this.clientInstanceId && message.clientInstanceId === this.clientInstanceId) {
            return;
        }
        if (message.timestamp && message.timestamp <= this.lastProcessedTimestamp) {
            return;
        }
        this.lastProcessedTimestamp = message.timestamp ?? Date.now();

        // Receives a request for the full content, send the last synced content
        if (message.problemStatementRequest) {
            this.respondWithFullContent(this.lastSyncedContent);
            return;
        }
        // Receives the full content, update the last synced content and emit the update
        if (message.problemStatementFull !== undefined) {
            this.lastSyncedContent = message.problemStatementFull;
            this.updatesSubject.next(message.problemStatementFull);
            return;
        }
        // Receives a patch, apply the patch to the last synced content and emit the update
        if (message.problemStatementPatch) {
            const patches = this.dmp.patch_fromText(message.problemStatementPatch);
            if (!patches.length) {
                return;
            }

            try {
                const [patchedContent, results] = this.dmp.patch_apply(patches, this.lastSyncedContent);
                // Check if any patch failed to apply (results array contains false for failed patches)
                const hasFailedPatches = results.some((success) => !success);
                if (hasFailedPatches) {
                    // Patch application failed - request full content sync as fallback
                    this.requestInitialSync();
                    return;
                }
                this.lastSyncedContent = patchedContent;
                this.updatesSubject.next(patchedContent);
            } catch (error) {
                // If patch parsing or application throws an error, request full content sync as fallback
                this.requestInitialSync();
            }
        }
    }
}
