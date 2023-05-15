import {
    IrisClientMessageDescriptor,
    IrisConversation,
    IrisMessageContent,
    IrisMessageContentType,
    IrisMessageDescriptor,
    IrisSender,
    IrisServerMessageDescriptor,
} from 'app/entities/iris/iris.model';
import dayjs from 'dayjs';

export const mockMessageContent: IrisMessageContent = {
    content: 'Hello, world!',
    type: IrisMessageContentType.TEXT,
};

export const mockServerMessage: IrisServerMessageDescriptor = {
    sender: IrisSender.SERVER,
    messageId: 1,
    messageContent: mockMessageContent,
    sentAt: dayjs(),
};

export const mockClientMessage: IrisClientMessageDescriptor = {
    sender: IrisSender.USER,
    messageContent: mockMessageContent,
    sentAt: dayjs(),
};

export const mockConversation: IrisConversation = {
    id: 1,
    messageDescriptors: [mockClientMessage, mockServerMessage],
    irisEnabled: true,
};
