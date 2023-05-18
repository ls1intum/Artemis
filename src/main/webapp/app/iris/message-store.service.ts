import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, Subject, Subscription } from 'rxjs';
import { tap } from 'rxjs/operators';
import {
    MessageStoreAction,
    MessageStoreState,
    isActiveConversationMessageLoadedAction,
    isHistoryMessageLoadedAction,
    isSessionIdReceivedAction,
    isStudentMessageSentAction,
} from 'app/iris/message-store.model';
import { IrisHttpMessageService } from 'app/iris/http-message.service';

/**
 * Provides a component level store to manage message-related state data and dispatch actions.
 */
@Injectable()
export class IrisMessageStore implements OnDestroy {
    private readonly initialState: MessageStoreState = {
        messages: [],
        sessionId: null,
    };

    private readonly action = new Subject<MessageStoreAction>();
    private readonly state = new BehaviorSubject<MessageStoreState>(this.initialState);
    private readonly subscription: Subscription;

    constructor(private httpMessageService: IrisHttpMessageService) {
        this.subscription = this.action
            .pipe(
                tap((action: MessageStoreAction) => {
                    this.state.next(this.storeReducer(this.state.getValue(), action));
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
        console.debug('You forgot to handle a case of MessageStoreAction: ' + action);
    }

    private storeReducer(state: MessageStoreState, action: MessageStoreAction): MessageStoreState {
        if (state.sessionId == null && !isSessionIdReceivedAction(action)) {
            throw new Error('You are trying to append messages to a conversation with an empty session id!');
        }

        if (isHistoryMessageLoadedAction(action) || isActiveConversationMessageLoadedAction(action)) {
            return {
                messages: [action.message, ...state.messages],
                sessionId: state.sessionId,
            };
        }
        if (isSessionIdReceivedAction(action)) {
            return {
                messages: [],
                sessionId: action.sessionId,
            };
        }
        if (isStudentMessageSentAction(action)) {
            // if sessionId is null then we have either an error or another action type
            this.httpMessageService.createMessage(<number>state.sessionId, action.message);
            return {
                messages: [action.message, ...state.messages],
                sessionId: state.sessionId,
            };
        }

        IrisMessageStore.exhaustiveCheck(action);
        return state;
    }
}
