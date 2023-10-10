import { Injectable } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ConversationErrorOccurredAction, SessionReceivedAction } from 'app/iris/state-store.model';
import { IrisSession } from 'app/entities/iris/iris-session.model';
import { IrisMessage } from 'app/entities/iris/iris-message.model';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import { IrisHttpCodeEditorSessionService } from 'app/iris/http-code-editor-session.service';
import { IrisHttpCodeEditorMessageService } from 'app/iris/http-code-editor-message.service';

/**
 * The IrisSessionService is responsible for managing Iris sessions and retrieving their associated messages.
 */
@Injectable()
export class IrisCodeEditorSessionService {
    /**
     * Creates an instance of IrisCodeEditorSessionService.
     * @param stateStore The IrisStateStore for managing the state of the application.
     * @param httpCodeEditorSessionService The IrisHttpCodeEditorSessionService for HTTP operations related to sessions.
     * @param httpCodeEditorMessageService The IrisHttpCodeEditorMessageService for HTTP operations related to messages.
     */
    constructor(
        private readonly stateStore: IrisStateStore,
        private httpCodeEditorSessionService: IrisHttpCodeEditorSessionService,
        private httpCodeEditorMessageService: IrisHttpCodeEditorMessageService,
    ) {}

    /**
     * Retrieves the current session or creates a new one if it doesn't exist.
     * @param exerciseId The exercise ID to which the session will be attached.
     */
    getCurrentSessionOrCreate(exerciseId: number): void {
        let sessionId: number;

        this.httpCodeEditorSessionService
            .getCurrentSession(exerciseId)
            .toPromise()
            .then((irisSessionResponse: HttpResponse<IrisSession>) => {
                sessionId = irisSessionResponse.body!.id;
                return this.httpCodeEditorMessageService
                    .getMessages(sessionId)
                    .toPromise()
                    .then((messagesResponse: HttpResponse<IrisMessage[]>) => {
                        const messages = messagesResponse.body!;
                        messages.sort((a, b) => {
                            if (a.sentAt && b.sentAt) {
                                if (a.sentAt === b.sentAt) return 0;
                                return a.sentAt.isBefore(b.sentAt) ? -1 : 1;
                            }
                            return 0;
                        });
                        this.stateStore.dispatch(new SessionReceivedAction(sessionId, messages));
                    })
                    .catch(() => {
                        this.dispatchError(IrisErrorMessageKey.HISTORY_LOAD_FAILED);
                    });
            })
            .catch((error: HttpErrorResponse) => {
                if (error.status == 404) {
                    return this.createNewSession(exerciseId);
                } else {
                    this.dispatchError(IrisErrorMessageKey.SESSION_LOAD_FAILED);
                }
            });
    }

    /**
     * Creates a new session for the given exercise ID.
     * @param exerciseId The exercise ID for which to create a new session.
     */
    createNewSession(exerciseId: number): void {
        this.httpCodeEditorSessionService.createSessionForProgrammingExercise(exerciseId).subscribe(
            (irisSessionResponse: any) => {
                this.stateStore.dispatch(new SessionReceivedAction(irisSessionResponse.id, []));
            },
            () => this.dispatchError(IrisErrorMessageKey.SESSION_CREATION_FAILED),
        );
    }

    /**
     * Dispatches an error action with the specified error message.
     * @param error The error message.
     */
    private dispatchError(error: IrisErrorMessageKey): void {
        this.stateStore.dispatch(new ConversationErrorOccurredAction(error));
    }
}
