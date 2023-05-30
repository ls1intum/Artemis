import { Injectable } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ConversationErrorOccurredAction, SessionReceivedAction } from 'app/iris/message-store.model';
import { IrisHttpSessionService } from 'app/iris/http-session.service';
import { IrisSession } from 'app/entities/iris/iris-session.model';
import { IrisHttpMessageService } from 'app/iris/http-message.service';
import { IrisMessage } from 'app/entities/iris/iris-message.model';

@Injectable()
export class IrisSessionService {
    constructor(private readonly stateStore: IrisStateStore, private httpSessionService: IrisHttpSessionService, private httpMessageService: IrisHttpMessageService) {}

    getCurrentSessionOrCreate(exerciseId: number): void {
        let sessionId: number;
        this.httpSessionService
            .getCurrentSession(exerciseId)
            .toPromise()
            .then((irisSessionResponse: HttpResponse<IrisSession>) => {
                sessionId = irisSessionResponse.body!.id;
                return this.httpMessageService.getMessages(sessionId).toPromise();
            })
            .then((messages: HttpResponse<IrisMessage[]>) => {
                this.stateStore.dispatch(new SessionReceivedAction(sessionId, messages.body!));
            })
            .catch((error: HttpErrorResponse) => {
                if (error.status == 404) {
                    return this.createNewSession(exerciseId);
                } else {
                    this.dispatchError('Could not fetch session details');
                }
            });
    }

    createNewSession(exerciseId: number): void {
        this.httpSessionService
            .createSessionForProgrammingExercise(exerciseId)
            .toPromise()
            .then((irisSessionResponse: HttpResponse<IrisSession>) => {
                this.stateStore.dispatch(new SessionReceivedAction(irisSessionResponse.body!.id, []));
            })
            .catch(() => {
                this.dispatchError('Could not create a new session');
            });
    }

    private dispatchError(error: string): void {
        this.stateStore.dispatch(new ConversationErrorOccurredAction(error)); // TODO in messages.json
    }
}
