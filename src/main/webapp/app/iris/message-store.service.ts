import { Injectable, OnDestroy } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { scan, shareReplay, takeUntil } from 'rxjs/operators';
import { ActionType, MessageStoreAction, MessageStoreState } from 'app/iris/message-store.model';

@Injectable({
    providedIn: 'root',
})
export class IrisMessageStore implements OnDestroy {
    private readonly initialState: MessageStoreState = {
        messages: [],
    };

    private readonly keepAliveState = new Subject<number>();
    private readonly action = new Subject<MessageStoreAction>();
    private readonly state: Observable<MessageStoreState> = this.action.pipe(
        scan<MessageStoreAction, MessageStoreState>((state, action) => IrisMessageStore.storeReducer(state, action), this.initialState),
        takeUntil(this.keepAliveState),
        shareReplay(1),
    );

    ngOnDestroy(): void {
        // we need to complete the stream after component destruction to prevent memory leaks
        this.keepAliveState.next(1);
    }

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
