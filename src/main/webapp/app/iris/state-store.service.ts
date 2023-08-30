import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, Subject, Subscription } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import {
    ActiveConversationMessageLoadedAction,
    ConversationErrorOccurredAction,
    HistoryMessageLoadedAction,
    MessageStoreAction,
    MessageStoreState,
    RateMessageSuccessAction,
    SessionReceivedAction,
    StudentMessageSentAction,
    isActiveConversationMessageLoadedAction,
    isConversationErrorOccurredAction,
    isHistoryMessageLoadedAction,
    isNumNewMessagesResetAction,
    isRateMessageSuccessAction,
    isSessionReceivedAction,
    isStudentMessageSentAction,
} from 'app/iris/state-store.model';
import { IrisServerMessage, isStudentSentMessage } from 'app/entities/iris/iris-message.model';
import { IrisErrorMessageKey, IrisErrorType, errorMessages } from 'app/entities/iris/iris-errors.model';

type ResolvableAction = { action: MessageStoreAction; resolve: () => void; reject: (error: IrisErrorType) => void };

/**
 * Provides a store to manage message-related state data and dispatch actions. Is valid only inside CourseExerciseDetailsComponent
 */
@Injectable()
export class IrisStateStore implements OnDestroy {
    private readonly initialState: MessageStoreState = {
        messages: [],
        sessionId: null,
        isLoading: false,
        numNewMessages: 0,
        error: null,
        serverResponseTimeout: null,
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

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    private static exhaustiveCheck(_: never): void {
        // typescript will detect any new unhandled action types using its inference system, this method should never be called
    }

    private static storeReducer(state: MessageStoreState, action: MessageStoreAction): MessageStoreState {
        const defaultError: IrisErrorType | null = state.error != null && state.error.fatal ? state.error : null;

        if (state.sessionId == null && !(isSessionReceivedAction(action) || isConversationErrorOccurredAction(action))) {
            return {
                ...state,
                isLoading: false,
                error: errorMessages[IrisErrorMessageKey.INVALID_SESSION_STATE],
                serverResponseTimeout: null,
            };
        }
        if (isNumNewMessagesResetAction(action)) {
            return {
                ...state,
                numNewMessages: 0,
            };
        }
        if (isHistoryMessageLoadedAction(action)) {
            const castedAction = action as HistoryMessageLoadedAction;
            return {
                ...state,
                messages: [...state.messages, castedAction.message],
                isLoading: false,
                error: defaultError,
                serverResponseTimeout: null,
            };
        }
        if (isActiveConversationMessageLoadedAction(action)) {
            const castedAction = action as ActiveConversationMessageLoadedAction;
            if (state.serverResponseTimeout !== null) {
                clearTimeout(state.serverResponseTimeout);
            }
            return {
                messages: [...state.messages, castedAction.message],
                sessionId: state.sessionId,
                isLoading: false,
                numNewMessages: state.numNewMessages + 1,
                error: defaultError,
                serverResponseTimeout: null,
            };
        }
        if (isConversationErrorOccurredAction(action)) {
            const castedAction = action as ConversationErrorOccurredAction;
            if (state.serverResponseTimeout !== null && (castedAction.errorObject?.fatal || castedAction.errorObject?.key === IrisErrorMessageKey.SEND_MESSAGE_FAILED)) {
                clearTimeout(state.serverResponseTimeout);
                state.serverResponseTimeout = null;
            }
            return {
                ...state,
                isLoading: false,
                error: castedAction.errorObject,
            };
        }
        if (isSessionReceivedAction(action)) {
            const castedAction = action as SessionReceivedAction;
            return {
                ...state,
                messages: castedAction.messages,
                sessionId: castedAction.sessionId,
                error: defaultError,
                serverResponseTimeout: null,
            };
        }
        if (isStudentMessageSentAction(action)) {
            const castedAction = action as StudentMessageSentAction;
            let newMessage = true;
            if (castedAction.message.messageDifferentiator !== undefined) {
                for (let i = state.messages.length - 1; i >= 0; i--) {
                    const message = state.messages[i];
                    if (!isStudentSentMessage(message)) continue;
                    if (message.messageDifferentiator === undefined) continue;
                    if (message.messageDifferentiator === castedAction.message.messageDifferentiator) {
                        newMessage = false;
                    }
                }
            }
            if (!newMessage) {
                if (castedAction.timeoutId !== null) {
                    clearTimeout(castedAction.timeoutId);
                }
                return {
                    ...state,
                    isLoading: true,
                    error: castedAction.message.id === undefined ? defaultError : null,
                };
            }
            return {
                ...state,
                messages: [...state.messages, castedAction.message],
                isLoading: true,
                error: defaultError,
                serverResponseTimeout: castedAction.timeoutId,
            };
        }
        if (isRateMessageSuccessAction(action)) {
            const castedAction = action as RateMessageSuccessAction;
            const newMessages = state.messages;
            if (castedAction.index < state.messages.length) {
                (newMessages[castedAction.index] as IrisServerMessage).helpful = castedAction.helpful;
                return {
                    ...state,
                    messages: newMessages,
                };
            }

            return state;
        }

        IrisStateStore.exhaustiveCheck(action);
        return state;
    }
}
