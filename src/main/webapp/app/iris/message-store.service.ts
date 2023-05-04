import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { scan } from 'rxjs/operators';
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
        function buildNewMessageArray() {
            const messages = state.messages;
            messages.push(action.message);
            return {
                messages: messages,
            };
        }

        switch (action.type) {
            case ActionType.HISTORY_MESSAGE_LOADED:
            case ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED:
                return buildNewMessageArray();
            case ActionType.STUDENT_MESSAGE_SENT:
                return buildNewMessageArray();
            default:
                this.exhaustiveCheck(action);
                return state;
        }
    }
}
