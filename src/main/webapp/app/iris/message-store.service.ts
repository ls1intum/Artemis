import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, Subject, Subscription } from 'rxjs';
import { tap } from 'rxjs/operators';
import { ActionType, MessageStoreAction, MessageStoreState } from 'app/iris/message-store.model';

/**
 * Provides a store to manage message-related state data and dispatch actions.
 */
@Injectable({
    providedIn: 'root',
})
export class IrisMessageStore implements OnDestroy {
    private readonly initialState: MessageStoreState = {
        messages: [],
    };

    private readonly action = new Subject<MessageStoreAction>();
    private readonly state = new BehaviorSubject<MessageStoreState>(this.initialState);
    private readonly subscription: Subscription;

    constructor() {
        this.subscription = this.action
            .pipe(
                tap((action: MessageStoreAction) => {
                    this.state.next(IrisMessageStore.storeReducer(this.state.getValue(), action));
                }),
            )
            .subscribe();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.state.complete();
        this.action.complete();
    }

    /**
     * Dispatches an action to update the message store state.
     * @param action The action to dispatch.
     */
    dispatch(action: MessageStoreAction): void {
        this.action.next(action);
    }

    /**
     * Returns an observable of the current message store state.
     * @returns An observable of the current message store state.
     */
    getState(): Observable<MessageStoreState> {
        return this.state.asObservable();
    }

    /**
     * Returns an observable of the actions dispatched to the message store.
     * @returns An observable of the actions dispatched to the message store.
     */
    getActionObservable(): Observable<MessageStoreAction> {
        return this.action.asObservable();
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
