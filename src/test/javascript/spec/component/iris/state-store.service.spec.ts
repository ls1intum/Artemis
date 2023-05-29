import { IrisStateStore } from 'app/iris/state-store.service';
import {
    ActionType,
    ActiveConversationMessageLoadedAction,
    HistoryMessageLoadedAction,
    MessageStoreState,
    SessionReceivedAction,
    StudentMessageSentAction,
} from 'app/iris/message-store.model';
import { skip, take } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { mockClientMessage, mockServerMessage } from '../../helpers/sample/iris-sample-data';

describe('IrisMessageStore', () => {
    let messageStore: IrisStateStore;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [IrisStateStore],
        });
        messageStore = TestBed.inject(IrisStateStore);
        messageStore.dispatch(new SessionReceivedAction(0, []));
    });

    it('should dispatch and handle HistoryMessageLoadedAction', async () => {
        const action: HistoryMessageLoadedAction = {
            type: ActionType.HISTORY_MESSAGE_LOADED,
            message: mockServerMessage,
        };

        const obs = messageStore.getState();

        const promise = obs.pipe(skip(1), take(1)).toPromise();

        messageStore.dispatch(action);

        const state = (await promise) as MessageStoreState;

        expect(state.messages).toEqual([action.message]);
    });

    it('should dispatch and handle ActiveConversationMessageLoadedAction', async () => {
        const action: ActiveConversationMessageLoadedAction = {
            type: ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED,
            message: mockServerMessage,
        };

        const obs = messageStore.getState();

        const promise = obs.pipe(skip(1), take(1)).toPromise();

        messageStore.dispatch(action);

        const state = (await promise) as MessageStoreState;

        expect(state.messages).toEqual([action.message]);
    });

    it('should dispatch and handle StudentMessageSentAction', async () => {
        const action: StudentMessageSentAction = {
            type: ActionType.STUDENT_MESSAGE_SENT,
            message: mockClientMessage,
        };

        const obs = messageStore.getState();

        const promise = obs.pipe(skip(1), take(1)).toPromise();

        messageStore.dispatch(action);

        const state = (await promise) as MessageStoreState;

        expect(state.messages).toEqual([action.message]);
    });

    it('should dispatch and handle 3 messages', async () => {
        const action1: StudentMessageSentAction = {
            type: ActionType.STUDENT_MESSAGE_SENT,
            message: mockClientMessage,
        };

        const action2: ActiveConversationMessageLoadedAction = {
            type: ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED,
            message: mockServerMessage,
        };

        const action3: ActiveConversationMessageLoadedAction = {
            type: ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED,
            message: mockServerMessage,
        };

        const obs = messageStore.getState();

        const promise1 = obs.pipe(skip(1), take(1)).toPromise();

        messageStore.dispatch(action1);

        const state1 = (await promise1) as MessageStoreState;

        expect(state1.messages).toEqual([action1.message]);

        const promise2 = obs.pipe(skip(1), take(1)).toPromise();

        messageStore.dispatch(action2);

        const state2 = (await promise2) as MessageStoreState;

        expect(state2.messages).toEqual([action1.message, action2.message]);

        // the observable should only be aware of the previously emitted value
        const promise3 = obs.pipe(skip(1), take(1)).toPromise();

        messageStore.dispatch(action3);

        const state3 = (await promise3) as MessageStoreState;

        expect(state3.messages).toEqual([action1.message, action2.message, action3.message]);
    });

    it.skip('should not dispatch new message actions with an empty session id', async () => {
        messageStore.dispatch(new SessionReceivedAction(1, []));

        const action: ActiveConversationMessageLoadedAction = {
            type: ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED,
            message: mockServerMessage,
        };

        const youAreTryingToAppendMessagesToAConversationWithAnEmptySessionId = 'You are trying to append messages to a conversation with an empty session id!';
        try {
            messageStore.dispatch(action);
        } catch (error) {
            expect(error.message).toBe(youAreTryingToAppendMessagesToAConversationWithAnEmptySessionId);
        }
    });
});
