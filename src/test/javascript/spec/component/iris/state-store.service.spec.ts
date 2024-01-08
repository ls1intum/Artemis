import { IrisStateStore } from 'app/iris/state-store.service';
import {
    ActionType,
    ActiveConversationMessageLoadedAction,
    ConversationErrorOccurredAction,
    HistoryMessageLoadedAction,
    MessageStoreState,
    NumNewMessagesResetAction,
    RateLimitUpdatedAction,
    RateMessageSuccessAction,
    SessionReceivedAction,
    StudentMessageSentAction,
} from 'app/iris/state-store.model';
import { firstValueFrom, skip, take } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { mockClientMessage, mockServerMessage, mockState } from '../../helpers/sample/iris-sample-data';
import { IrisErrorMessageKey, errorMessages } from 'app/entities/iris/iris-errors.model';
import { IrisRateLimitInformation } from 'app/iris/websocket.service';

describe('IrisStateStore', () => {
    let stateStore: IrisStateStore;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [IrisStateStore],
        });
        stateStore = TestBed.inject(IrisStateStore);
        stateStore.dispatch(new SessionReceivedAction(0, []));
    });

    describe('RateMessageSuccessAction', () => {
        it('should keep the state when index is out of range', async () => {
            const action: RateMessageSuccessAction = new RateMessageSuccessAction(0, true);
            const obs = stateStore.getState();
            const promise = firstValueFrom(obs.pipe(skip(1), take(1)));

            stateStore.dispatch(action);

            const state = (await promise) as MessageStoreState;
            expect(state).toEqual(mockState);
        });

        it('should change helpful attribute of message position when index is in range', async () => {
            const obs = stateStore.getState();
            stateStore.dispatch(new SessionReceivedAction(123, [mockServerMessage, mockServerMessage]));

            const action: RateMessageSuccessAction = new RateMessageSuccessAction(0, true);
            const promise = firstValueFrom(obs.pipe(skip(1), take(1)));

            stateStore.dispatch(action);

            const state = (await promise) as MessageStoreState;
            expect(state).toEqual({
                ...mockState,
                sessionId: 123,
                messages: [
                    {
                        ...mockServerMessage,
                        helpful: true,
                    },
                    {
                        ...mockServerMessage,
                    },
                ],
            });
        });
    });

    it('should dispatch and handle NumNewMessagesResetAction', async () => {
        const action: NumNewMessagesResetAction = new NumNewMessagesResetAction();

        const obs = stateStore.getState();

        const promise = firstValueFrom(obs.pipe(skip(1), take(1)));

        stateStore.dispatch(action);

        const state = (await promise) as MessageStoreState;

        expect(state).toEqual({
            ...mockState,
            numNewMessages: 0,
        });
    });

    it('should dispatch and handle HistoryMessageLoadedAction', async () => {
        const action: HistoryMessageLoadedAction = new HistoryMessageLoadedAction(mockServerMessage);

        const obs = stateStore.getState();

        const promise = firstValueFrom(obs.pipe(skip(1), take(1)));

        stateStore.dispatch(action);

        const state = (await promise) as MessageStoreState;

        expect(state).toEqual({
            ...mockState,
            messages: [action.message],
        });
    });

    it('should dispatch and handle ActiveConversationMessageLoadedAction', async () => {
        const action: ActiveConversationMessageLoadedAction = new ActiveConversationMessageLoadedAction(mockServerMessage);

        const obs = stateStore.getState();

        const promise = firstValueFrom(obs.pipe(skip(1), take(1)));

        stateStore.dispatch(action);

        const state = (await promise) as MessageStoreState;

        expect(state).toEqual({
            ...mockState,
            numNewMessages: 1,
            messages: [action.message],
        });
    });

    it('should dispatch and handle StudentMessageSentAction', async () => {
        const action: StudentMessageSentAction = {
            type: ActionType.STUDENT_MESSAGE_SENT,
            message: mockClientMessage,
            timeoutId: null,
        };

        const obs = stateStore.getState();

        const promise = firstValueFrom(obs.pipe(skip(1), take(1)));

        stateStore.dispatch(action);

        const state = (await promise) as MessageStoreState;

        expect(state).toEqual({
            ...mockState,
            isLoading: true,
            messages: [action.message],
        });
    });

    it('should dispatch and handle 4 messages', async () => {
        const action1: StudentMessageSentAction = {
            type: ActionType.STUDENT_MESSAGE_SENT,
            message: mockClientMessage,
            timeoutId: null,
        };

        const action2: ActiveConversationMessageLoadedAction = {
            type: ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED,
            message: mockServerMessage,
        };

        const action3: ActiveConversationMessageLoadedAction = {
            type: ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED,
            message: mockServerMessage,
        };

        const action4: NumNewMessagesResetAction = new NumNewMessagesResetAction();

        const obs = stateStore.getState();

        const promise1 = firstValueFrom(obs.pipe(skip(1), take(1)));

        stateStore.dispatch(action1);

        const state1 = (await promise1) as MessageStoreState;

        expect(state1).toEqual({
            ...mockState,
            isLoading: true,
            messages: [action1.message],
        });

        const promise2 = firstValueFrom(obs.pipe(skip(1), take(1)));

        stateStore.dispatch(action2);

        const state2 = (await promise2) as MessageStoreState;

        expect(state2).toEqual({
            ...mockState,
            numNewMessages: 1,
            messages: [action1.message, action2.message],
        });

        // the observable should only be aware of the previously emitted value
        const promise3 = firstValueFrom(obs.pipe(skip(1), take(1)));

        stateStore.dispatch(action3);

        const state3 = (await promise3) as MessageStoreState;

        expect(state3).toEqual({
            ...mockState,
            numNewMessages: 2,
            messages: [action1.message, action2.message, action3.message],
        });

        const promise4 = firstValueFrom(obs.pipe(skip(1), take(1)));

        stateStore.dispatch(action4);

        const state4 = (await promise4) as MessageStoreState;

        expect(state4).toEqual({
            ...mockState,
            numNewMessages: 0,
            messages: [action1.message, action2.message, action3.message],
        });
    });

    it('should dispatch error occurrences', async () => {
        const obs = stateStore.getState();

        const promise = firstValueFrom(obs.pipe(skip(1), take(1)));

        stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.HISTORY_LOAD_FAILED));

        const state = (await promise) as MessageStoreState;

        expect(state).toStrictEqual({
            ...mockState,
            error: errorMessages[IrisErrorMessageKey.HISTORY_LOAD_FAILED],
            serverResponseTimeout: null,
        });
    });

    it('should proceed with then clause after dispatchAndThen is executed', async () => {
        const action: StudentMessageSentAction = {
            type: ActionType.STUDENT_MESSAGE_SENT,
            message: mockClientMessage,
            timeoutId: null,
        };

        await stateStore.dispatchAndThen(action).then(async () => {
            const promise = firstValueFrom(stateStore.getState().pipe(take(1)));
            const state = (await promise) as MessageStoreState;
            expect(state).toEqual({
                ...mockState,
                isLoading: true,
                messages: [action.message],
            });
        });
    });

    it('should stay in fatal states', async () => {
        const obs1 = stateStore.getState();

        const promise1 = firstValueFrom(obs1.pipe(skip(1), take(1)));

        stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.SESSION_LOAD_FAILED));

        const state1 = (await promise1) as MessageStoreState;

        expect(state1.error?.fatal).toBeTruthy();

        const obs2 = stateStore.getState();

        const promise2 = firstValueFrom(obs2.pipe(skip(1), take(1)));

        stateStore.dispatch(new StudentMessageSentAction(mockClientMessage, null));

        const state2 = (await promise2) as MessageStoreState;

        expect(state2.error?.fatal).toBeTruthy();
    });

    it('should not dispatch 2 StudentMessageSentActions with unique messageDifferentiator', async () => {
        const action1: StudentMessageSentAction = {
            type: ActionType.STUDENT_MESSAGE_SENT,
            message: {
                ...mockClientMessage,
                messageDifferentiator: 5,
            },
            timeoutId: null,
        };

        const obs1 = stateStore.getState();

        const promise1 = firstValueFrom(obs1.pipe(skip(1), take(1)));

        stateStore.dispatch(action1);

        const state1 = (await promise1) as MessageStoreState;

        expect(state1).toEqual({
            ...mockState,
            isLoading: true,
            messages: [
                {
                    ...action1.message,
                    messageDifferentiator: 5,
                },
            ],
        });

        const action2: StudentMessageSentAction = {
            type: ActionType.STUDENT_MESSAGE_SENT,
            message: {
                ...mockClientMessage,
                messageDifferentiator: 5,
            },
            timeoutId: null,
        };

        const obs2 = stateStore.getState();

        const promise2 = firstValueFrom(obs2.pipe(skip(1), take(1)));

        stateStore.dispatch(action2);

        const state2 = (await promise2) as MessageStoreState;

        expect(state2.messages).toHaveLength(1);
    });

    it('should  dispatch 2 StudentMessageSentActions with different messageDifferentiators', async () => {
        const action1: StudentMessageSentAction = {
            type: ActionType.STUDENT_MESSAGE_SENT,
            message: {
                ...mockClientMessage,
                messageDifferentiator: undefined,
            },
            timeoutId: null,
        };

        const obs1 = stateStore.getState();

        const promise1 = firstValueFrom(obs1.pipe(skip(1), take(1)));

        stateStore.dispatch(action1);

        const state1 = (await promise1) as MessageStoreState;

        expect(state1).toEqual({
            ...mockState,
            isLoading: true,
            messages: [
                {
                    ...action1.message,
                    messageDifferentiator: undefined,
                },
            ],
        });

        const action2: StudentMessageSentAction = {
            type: ActionType.STUDENT_MESSAGE_SENT,
            message: {
                ...mockClientMessage,
                messageDifferentiator: 7,
            },
            timeoutId: null,
        };

        const obs2 = stateStore.getState();

        const promise2 = firstValueFrom(obs2.pipe(skip(1), take(1)));

        stateStore.dispatch(action2);

        const state2 = (await promise2) as MessageStoreState;

        expect(state2.messages).toHaveLength(2);
    });

    it('should update below rate limit state', async () => {
        const obs = stateStore.getState();

        const promise = firstValueFrom(obs.pipe(skip(1), take(1)));

        stateStore.dispatch(new RateLimitUpdatedAction(new IrisRateLimitInformation(1, 2, 3)));

        const state = (await promise) as MessageStoreState;

        expect(state).toStrictEqual({
            ...mockState,
            error: null,
            currentMessageCount: 1,
            rateLimit: 2,
            rateLimitTimeframeHours: 3,
        });
    });

    it('should update above rate limit state', async () => {
        const obs = stateStore.getState();

        const promise = firstValueFrom(obs.pipe(skip(1), take(1)));

        stateStore.dispatch(new RateLimitUpdatedAction(new IrisRateLimitInformation(2, 2, 3)));

        const state = (await promise) as MessageStoreState;

        expect(state).toStrictEqual({
            ...mockState,
            error: errorMessages[IrisErrorMessageKey.RATE_LIMIT_EXCEEDED],
            currentMessageCount: 2,
            rateLimit: 2,
            rateLimitTimeframeHours: 3,
        });
    });
});

describe('IrisStateStore with an empty session state', () => {
    let stateStore: IrisStateStore;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [IrisStateStore],
        });
        stateStore = TestBed.inject(IrisStateStore);
    });

    it('should not dispatch new message actions with an empty session id', async () => {
        const action: ActiveConversationMessageLoadedAction = {
            type: ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED,
            message: mockServerMessage,
        };

        const obs = stateStore.getState();

        const promise = firstValueFrom(obs.pipe(skip(1), take(1)));

        stateStore.dispatch(action);

        const state = (await promise) as MessageStoreState;

        expect(state).toStrictEqual({
            ...mockState,
            error: errorMessages[IrisErrorMessageKey.INVALID_SESSION_STATE],
            sessionId: null,
        });
    });
});
