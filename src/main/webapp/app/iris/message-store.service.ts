import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { scan, shareReplay } from 'rxjs/operators';
import { ActionType, MessageStoreAction, MessageStoreState } from 'app/iris/message-store.model';

@Injectable({
    providedIn: 'root',
})
export class IrisMessageStore {
    private initialState: MessageStoreState = {
        messages: [],
    };
    private action = new Subject<MessageStoreAction>();
    private state: Observable<MessageStoreState> = this.action.pipe(
        scan<MessageStoreAction, MessageStoreState>((state, action) => IrisMessageStore.storeReducer(state, action), this.initialState),
        shareReplay(1),
    );

    dispatch(action: MessageStoreAction) {
        this.action.next(action);
    }

    getState(): Observable<MessageStoreState> {
        return this.state;
    }

    private static exhaustiveCheck(action: never): void {
        console.debug('You forgot to implement a case of MessageStoreAction: ' + action);
    }

    private static storeReducer(state: MessageStoreState, action: MessageStoreAction): MessageStoreState {
        switch (action.type) {
            case ActionType.HISTORY_MESSAGE_LOADED:
            case ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED:
            case ActionType.STUDENT_MESSAGE_SENT:
                return {
                    messages: [action.message, ...state.messages],
                };
            default:
                this.exhaustiveCheck(action);
                return state;
        }
    }
}
