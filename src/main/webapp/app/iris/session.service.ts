import { Injectable } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ConversationErrorOccurredAction, SessionReceivedAction } from 'app/iris/message-store.model';
import { IrisHttpSessionService } from 'app/iris/http-session.service';
import { IrisSession } from 'app/entities/iris/iris-session.model';

@Injectable()
export class IrisSessionService {
    constructor(private readonly stateStore: IrisStateStore, private httpSessionService: IrisHttpSessionService) {}

    getCurrentSessionOrCreate(exerciseId: number): void {
        this.httpSessionService.getCurrentSession(exerciseId).subscribe({
            next: (irisSessionResponse: HttpResponse<IrisSession>) => {
                this.stateStore.dispatch(new SessionReceivedAction(irisSessionResponse.body!.id, []));
            },
            error: (error: HttpErrorResponse) => {
                if (error.status == 404) {
                    this.createNewSession(exerciseId);
                } else {
                    this.dispatchError('Could not fetch session details');
                }
            },
        });
    }

    createNewSession(exerciseId: number): void {
        this.httpSessionService.createSessionForProgrammingExercise(exerciseId).subscribe({
            next: (irisSessionResponse: HttpResponse<IrisSession>) => {
                this.stateStore.dispatch(new SessionReceivedAction(irisSessionResponse.body!.id, []));
            },
            error: () => {
                this.dispatchError('Could not create a new session');
            },
        });
        this.stateStore.dispatch(new SessionReceivedAction(5, []));
    }

    private dispatchError(error: string): void {
        this.stateStore.dispatch(new ConversationErrorOccurredAction(error)); // TODO in messages.json
    }
}
