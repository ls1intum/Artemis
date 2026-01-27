import { Injectable, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import * as Y from 'yjs';
import {
    ProgrammingExerciseEditorSyncMessage,
    ProgrammingExerciseEditorSyncService,
    ProgrammingExerciseEditorSyncTarget,
} from 'app/programming/manage/services/programming-exercise-editor-sync.service';
import { decodeBase64ToUint8Array, encodeUint8ArrayToBase64, normalizeYjsOrigin } from 'app/programming/manage/services/yjs-utils';

export type ProblemStatementSyncState = {
    doc: Y.Doc;
    text: Y.Text;
};

@Injectable({ providedIn: 'root' })
export class ProblemStatementSyncService {
    private syncService = inject(ProgrammingExerciseEditorSyncService);

    private exerciseId?: number;
    private incomingMessageSubscription?: Subscription;
    private yDoc?: Y.Doc;
    private yText?: Y.Text;
    private awaitingInitialSync = false;
    private replaceOnNextSync = false;

    init(exerciseId: number, initialContent: string): ProblemStatementSyncState {
        this.reset();
        this.exerciseId = exerciseId;
        this.replaceOnNextSync = initialContent.length > 0;
        this.awaitingInitialSync = true;
        this.initializeYjsDocument(initialContent);
        this.incomingMessageSubscription = this.syncService.subscribeToUpdates(exerciseId).subscribe((message) => this.handleRemoteMessage(message));
        this.requestInitialSync();
        return { doc: this.yDoc!, text: this.yText! };
    }

    reset() {
        this.incomingMessageSubscription?.unsubscribe();
        this.incomingMessageSubscription = undefined;
        this.yDoc?.destroy();
        this.yDoc = undefined;
        this.yText = undefined;
        this.exerciseId = undefined;
        this.awaitingInitialSync = false;
        this.replaceOnNextSync = false;
    }

    /**
     * Request the newest problem statement content from other active editors (if exists).
     * This ensures that unsaved problem statement from other editors are also synchronized.
     */
    requestInitialSync() {
        if (!this.exerciseId) {
            return;
        }
        const stateVector = this.yDoc && !this.replaceOnNextSync ? encodeUint8ArrayToBase64(Y.encodeStateVector(this.yDoc)) : undefined;
        this.syncService.sendSynchronizationUpdate(this.exerciseId, {
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            yjsRequest: true,
            yjsStateVector: stateVector,
        });
    }

    private respondWithFullContent(stateVector?: string) {
        if (!this.exerciseId) {
            return;
        }
        if (!this.yDoc) {
            return;
        }
        const decodedStateVector = stateVector ? decodeBase64ToUint8Array(stateVector) : undefined;
        const update = decodedStateVector ? Y.encodeStateAsUpdate(this.yDoc, decodedStateVector) : Y.encodeStateAsUpdate(this.yDoc);
        this.syncService.sendSynchronizationUpdate(this.exerciseId, {
            target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
            yjsUpdate: encodeUint8ArrayToBase64(update),
        });
    }

    /**
     * Respond to a synchronization message from the websocket subscription for programming exercise problem statements.
     *
     * @param message The synchronization message to process
     */
    private handleRemoteMessage(message: ProgrammingExerciseEditorSyncMessage) {
        if (message.target !== ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT) {
            return;
        }

        if (message.yjsRequest) {
            this.respondWithFullContent(message.yjsStateVector);
            return;
        }

        if (message.yjsUpdate && this.yDoc) {
            const update = decodeBase64ToUint8Array(message.yjsUpdate);
            if (this.awaitingInitialSync && this.replaceOnNextSync) {
                this.replaceContentFromUpdate(update);
                this.awaitingInitialSync = false;
                return;
            }
            Y.applyUpdate(this.yDoc, update, 'remote');
            this.awaitingInitialSync = false;
        }
    }

    private initializeYjsDocument(initialContent: string) {
        const doc = new Y.Doc();
        const text = doc.getText('problem-statement');
        doc.transact(() => {
            if (initialContent) {
                text.insert(0, initialContent);
            }
        }, 'init');
        doc.on('update', (update, origin: unknown) => {
            if (!this.exerciseId) {
                return;
            }
            const originTag = normalizeYjsOrigin(origin);
            if (originTag === 'remote' || originTag === 'init') {
                return;
            }
            this.syncService.sendSynchronizationUpdate(this.exerciseId, {
                target: ProgrammingExerciseEditorSyncTarget.PROBLEM_STATEMENT,
                yjsUpdate: encodeUint8ArrayToBase64(update),
            });
        });
        this.yDoc = doc;
        this.yText = text;
    }

    private replaceContentFromUpdate(update: Uint8Array) {
        if (!this.yDoc || !this.yText) {
            return;
        }
        const snapshotDoc = new Y.Doc();
        Y.applyUpdate(snapshotDoc, update);
        const snapshotText = snapshotDoc.getText('problem-statement').toString();
        this.yDoc.transact(() => {
            this.yText?.delete(0, this.yText.length);
            if (snapshotText) {
                this.yText?.insert(0, snapshotText);
            }
        }, 'remote');
        snapshotDoc.destroy();
    }
}
