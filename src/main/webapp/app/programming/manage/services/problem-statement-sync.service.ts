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
    private readonly diffMatchPatch = new DiffMatchPatch();

    private exerciseId?: number;
    private incomingMessageSubscription?: Subscription;
    private outgoingDebounceSubscription?: Subscription;
    private localChangesQueue$ = new Subject<string>();
    private patchedContentUpdate = new Subject<string>();

    patchedContentObserable$: Observable<string> = this.patchedContentUpdate.asObservable();

    private lastSyncedContent = '';
    private lastProcessedTimestamp = 0;

    init(exerciseId: number, initialContent: string) {
        this.exerciseId = exerciseId;
        this.lastSyncedContent = initialContent;
        this.lastProcessedTimestamp = 0;
        this.incomingMessageSubscription = this.syncService.subscribeToUpdates(exerciseId).subscribe((message) => this.handleRemoteMessage(message));
        this.outgoingDebounceSubscription = this.localChangesQueue$.pipe(debounceTime(200)).subscribe((content) => this.handleLocalChange(content));
        this.requestInitialSync();
    }

    dispose() {
        this.incomingMessageSubscription?.unsubscribe();
        this.incomingMessageSubscription = undefined;
        this.outgoingDebounceSubscription?.unsubscribe();
        this.outgoingDebounceSubscription = undefined;
        this.exerciseId = undefined;
        this.lastSyncedContent = '';
        this.lastProcessedTimestamp = 0;
    }

    /**
     * put the edited content into the local queue for debounced synchronization
     * @param content The edited content to be synchronized
     */
    queueLocalChange(content: string) {
        this.localChangesQueue$.next(content);
    }

    /**
     * Construct and send a patch for the local content change, from the localChangesQueue.
     * This method is called debounced via the localChangesQueue$ subject.
     * @param content The new content to be sent as a patch
     * @returns void
     */
    handleLocalChange(content: string) {
        if (!this.exerciseId) {
            return;
        }
        const patchText = this.diffMatchPatch.patch_toText(this.diffMatchPatch.patch_make(this.lastSyncedContent, content));
        if (!patchText) {
            return;
        }
        this.lastSyncedContent = content;
        this.syncService.sendSynchronizationUpdate(this.exerciseId, {
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementPatch: patchText,
        });
    }

    /**
     * Request the newest problem statement content from other active editors (if exists).
     * This ensures that unsaved problem statement from other editors are also synchronized.
     *
     * @returns
     */
    requestInitialSync() {
        if (!this.exerciseId) {
            return;
        }
        this.syncService.sendSynchronizationUpdate(this.exerciseId, {
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementRequest: true,
        });
    }

    /**
     * Respond to a request for the full problem statement content.
     * @param content The current problem statement content to send
     * @returns void
     */
    private respondWithFullContent(content: string) {
        if (!this.exerciseId) {
            return;
        }
        this.syncService.sendSynchronizationUpdate(this.exerciseId, {
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            problemStatementFull: content,
        });
    }

    /**
     * Respond to a synchronization message from the websocket subscription for programming exercise problem statements.
     *
     * @param message The synchronization message to process
     * @returns void
     */
    private handleRemoteMessage(message: ProgrammingExerciseEditorSyncMessage) {
        if (message.target !== ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT) {
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
            this.patchedContentUpdate.next(message.problemStatementFull);
            return;
        }

        // Receives a patch, apply the patch to the last synced content and emit the update
        if (message.problemStatementPatch) {
            const patches = this.diffMatchPatch.patch_fromText(message.problemStatementPatch);
            if (!patches.length) {
                return;
            }
            try {
                const [patchedContent, results] = this.diffMatchPatch.patch_apply(patches, this.lastSyncedContent);
                // Check if any patch failed to apply (results array contains false for failed patches)
                const hasFailedPatches = results.some((success) => !success);
                if (hasFailedPatches) {
                    // TODO: we should alert this or handle it more gracefully
                    return;
                }
                this.lastSyncedContent = patchedContent;
                this.patchedContentUpdate.next(patchedContent);
            } catch (error) {
                // TODO: we should alert this or handle it more gracefully
            }
        }
    }
}
