import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, Subject, Subscription } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import {
    MessageStoreAction,
    MessageStoreState,
    isActiveConversationMessageLoadedAction,
    isConversationErrorOccurredAction,
    isHistoryMessageLoadedAction,
    isSessionReceivedAction,
    isStudentMessageSentAction,
} from 'app/iris/state-store.model';

type ResolvableAction = { action: MessageStoreAction; resolve: () => void; reject: (error: string) => void };

/**
 * Provides a store to manage message-related state data and dispatch actions. Is valid only inside CourseExerciseDetailsComponent
 */
@Injectable()
export class IrisStateStore implements OnDestroy {
    private readonly initialState: MessageStoreState = {
        messages: [],
        sessionId: null,
        isLoading: false,
        error: '',
    };

    private readonly action = new Subject<ResolvableAction>();
    private readonly state = new BehaviorSubject<MessageStoreState>(this.initialState);
    private readonly subscription: Subscription;

    constructor() {
        this.subscription = this.action
            .pipe(
                tap((resolvableAction: ResolvableAction) => {
                    const newState = IrisStateStore.storeReducer(this.state.getValue(), resolvableAction.action);
                    this.state.next(newState);
                    if (newState.error) {
                        resolvableAction.reject(newState.error);
                    } else {
                        resolvableAction.resolve();
                    }
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
     * @return promise Promise to handle consecutive cases
     */
    dispatchAndThen(action: MessageStoreAction): Promise<void> {
        return new Promise<void>((resolve, reject) => {
            this.action.next({
                action: action,
                resolve: resolve,
                reject: reject,
            });
        });
    }

    /**
     * Dispatches an action to update the message store state.
     * @param action The action to dispatch.
     * @return void
     */
    dispatch(action: MessageStoreAction): void {
        this.action.next({
            action: action,
            resolve: () => {},
            reject: () => {},
        });
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
        return this.action.asObservable().pipe(map((resolvableAction: ResolvableAction) => resolvableAction.action));
    }

    private static exhaustiveCheck(action: never): void {
        console.debug('You forgot to handle a case of MessageStoreAction: ' + action);
    }

    private static storeReducer(state: MessageStoreState, action: MessageStoreAction): MessageStoreState {
        if (state.sessionId == null && !(isSessionReceivedAction(action) || isConversationErrorOccurredAction(action))) {
            return {
                messages: [...state.messages],
                sessionId: state.sessionId,
                isLoading: false,
                error: 'Iris ChatBot state is invalid. It is impossible to send messages in such a session.', // TODO translate to German
            };
        }

        if (isHistoryMessageLoadedAction(action) || isActiveConversationMessageLoadedAction(action)) {
            return {
                messages: [...state.messages, action.message],
                sessionId: state.sessionId,
                isLoading: false,
                error: '',
            };
        }
        if (isConversationErrorOccurredAction(action)) {
            return {
                messages: state.messages,
                sessionId: state.sessionId,
                isLoading: false,
                error: action.errorMessage,
            };
        }
        if (isSessionReceivedAction(action)) {
            return {
                messages: action.messages,
                sessionId: action.sessionId,
                isLoading: state.isLoading,
                error: '',
            };
        }
        if (isStudentMessageSentAction(action)) {
            return {
                messages: [...state.messages, action.message],
                sessionId: state.sessionId,
                isLoading: true,
                error: '',
            };
        }

        IrisStateStore.exhaustiveCheck(action);
        return state;
    }
}
