import { IrisMessageStore } from 'app/iris/message-store.service';
import { ActionType, ActiveConversationMessageLoadedAction, HistoryMessageLoadedAction, MessageStoreState, StudentMessageSentAction } from 'app/iris/message-store.model';
import { IrisClientMessageDescriptor, IrisMessageContent, IrisMessageContentType, IrisSender, IrisServerMessageDescriptor } from 'app/entities/iris/iris.model';
import { skip, take } from 'rxjs';

describe('IrisMessageStore', () => {
    const mockMessageContent: IrisMessageContent = {
        content: 'Hello, world!',
        type: IrisMessageContentType.TEXT,
    };

    const mockServerMessage: IrisServerMessageDescriptor = {
        sender: IrisSender.SERVER,
        messageId: 1,
        messageContent: mockMessageContent,
        sentDatetime: new Date(),
    };

    const mockClientMessage: IrisClientMessageDescriptor = {
        sender: IrisSender.USER,
        messageContent: mockMessageContent,
        sentDatetime: new Date(),
    };

    let messageStore: IrisMessageStore;

    beforeEach(() => {
        messageStore = new IrisMessageStore();
    });

    it('should dispatch and handle HistoryMessageLoadedAction', async () => {
        const action: HistoryMessageLoadedAction = {
            type: ActionType.HISTORY_MESSAGE_LOADED,
            message: mockServerMessage,
        };

        const obs = messageStore.getState();

        const promise = obs
            .pipe(
                take(1), // Take the next emitted value
            )
            .toPromise();

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

        const promise = obs
            .pipe(
                take(1), // Take the next emitted value
            )
            .toPromise();

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

        const promise = obs
            .pipe(
                take(1), // Take the next emitted value
            )
            .toPromise();

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

        const promise1 = obs.pipe(take(1)).toPromise();

        messageStore.dispatch(action1);

        const state1 = (await promise1) as MessageStoreState;

        expect(state1.messages).toEqual([action1.message]);

        const promise2 = obs.pipe(skip(1), take(1)).toPromise();

        messageStore.dispatch(action2);

        const state2 = (await promise2) as MessageStoreState;

        expect(state2.messages).toEqual([action2.message, action1.message]);

        // the observable should only be aware of the previously emitted value
        const promise3 = obs.pipe(skip(1), take(1)).toPromise();

        messageStore.dispatch(action3);

        const state3 = (await promise3) as MessageStoreState;

        expect(state3.messages).toEqual([action3.message, action2.message, action1.message]);
    });
});
