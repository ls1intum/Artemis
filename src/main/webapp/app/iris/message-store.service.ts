import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { ActionType, MessageStoreAction, MessageStoreState } from 'app/iris/message-store.model';

@Injectable({
    providedIn: 'root',
})
export class IrisMessageStore implements OnDestroy {
    private readonly initialState: MessageStoreState = {
        messages: [],
    };

    private readonly state = new BehaviorSubject<MessageStoreState>(this.initialState);

    ngOnDestroy() {
        this.state.complete();
    }

    dispatch(action: MessageStoreAction) {
        this.state.next(IrisMessageStore.storeReducer(this.state.getValue(), action));
    }

    getState(): Observable<MessageStoreState> {
        return this.state.asObservable();
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
