import { IrisMessageStore } from 'app/iris/message-store.service';
import { ActionType, ActiveConversationMessageLoadedAction, HistoryMessageLoadedAction, StudentMessageSentAction } from 'app/iris/message-store.model';
import { IrisClientMessageDescriptor, IrisMessageContent, IrisMessageContentType, IrisSender, IrisServerMessageDescriptor } from 'app/entities/iris/iris.model';
import { lastValueFrom } from 'rxjs';

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
        messageStore.dispatch(action);

        await expect(lastValueFrom(messageStore.getState())).resolves.toEqual([action.message]);
    });

    it('should dispatch and handle ActiveConversationMessageLoadedAction', async () => {
        const action: ActiveConversationMessageLoadedAction = {
            type: ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED,
            message: mockServerMessage,
        };

        messageStore.dispatch(action);
        await expect(lastValueFrom(messageStore.getState())).resolves.toEqual([action.message]);
    });

    it('should dispatch and handle StudentMessageSentAction', async () => {
        const action: StudentMessageSentAction = {
            type: ActionType.STUDENT_MESSAGE_SENT,
            message: mockClientMessage,
        };
        messageStore.dispatch(action);

        await expect(lastValueFrom(messageStore.getState())).resolves.toEqual([action.message]);
    });

    it('should dispatch and handle 2 messages', async () => {
        const action1: StudentMessageSentAction = {
            type: ActionType.STUDENT_MESSAGE_SENT,
            message: mockClientMessage,
        };
        messageStore.dispatch(action1);
        await expect(lastValueFrom(messageStore.getState())).resolves.toEqual([action1.message]);

        const action2: ActiveConversationMessageLoadedAction = {
            type: ActionType.ACTIVE_CONVERSATION_MESSAGE_LOADED,
            message: mockServerMessage,
        };
        messageStore.dispatch(action2);

        await expect(lastValueFrom(messageStore.getState())).resolves.toEqual([action1.message, action2.message]);
    });
});
