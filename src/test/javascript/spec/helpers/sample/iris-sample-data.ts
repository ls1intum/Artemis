import dayjs from 'dayjs/esm';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IrisAssistantMessage, IrisSender, IrisUserMessage } from 'app/iris/shared/entities/iris-message.model';
import { IrisMessageContentType, IrisTextMessageContent } from 'app/iris/shared/entities/iris-content-type.model';
import { IrisSession } from 'app/iris/shared/entities/iris-session.model';
import { IrisChatWebsocketDTO, IrisChatWebsocketPayloadType } from 'app/iris/shared/entities/iris-chat-websocket-dto.model';
import { IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { HttpResponse } from '@angular/common/http';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';
import { MemirisMemory } from 'app/iris/shared/entities/memiris.model';

const map = new Map<string, any>();
map.set('model', 'gpt-4');

export const mockMessageContent = {
    type: IrisMessageContentType.TEXT,
    textContent: 'Hello, world!',
} as IrisTextMessageContent;

export const irisExercise = { id: 1, title: 'Metis  Exercise', type: ExerciseType.PROGRAMMING } as ProgrammingExercise;

export const mockServerMessage = {
    sender: IrisSender.LLM,
    id: 1,
    content: [mockMessageContent],
    sentAt: dayjs(),
} as IrisAssistantMessage;

export const mockServerMessage2 = {
    sender: IrisSender.LLM,
    id: 4,
    content: [mockMessageContent],
    sentAt: dayjs(),
} as IrisAssistantMessage;

export const mockServerMessageWithMemories = {
    sender: IrisSender.LLM,
    id: 3,
    content: [mockMessageContent],
    sentAt: dayjs(),
    accessedMemories: [new MemirisMemory('UUID', 'Memory Title', 'Memory content', [], [], false, false)],
};

export const mockClientMessage = {
    id: 2,
    sender: IrisSender.USER,
    content: [mockMessageContent],
    sentAt: dayjs(),
} as IrisUserMessage;

export const mockClientMessageWithMemories = {
    id: 5,
    sender: IrisSender.USER,
    content: [mockMessageContent],
    sentAt: dayjs(),
    createdMemories: [new MemirisMemory('UUID', 'Memory Title', 'Memory content', [], [], false, false)],
} as IrisUserMessage;

export const mockWebsocketServerMessage = {
    type: IrisChatWebsocketPayloadType.MESSAGE,
    message: mockServerMessage2,
    stages: [],
} as IrisChatWebsocketDTO;

export const mockWebsocketServerMessageWithMemories = {
    type: IrisChatWebsocketPayloadType.MESSAGE,
    message: mockServerMessageWithMemories,
    stages: [],
} as IrisChatWebsocketDTO;

export const mockWebsocketClientMessage = {
    type: IrisChatWebsocketPayloadType.MESSAGE,
    message: mockClientMessage,
} as IrisChatWebsocketDTO;

export const mockWebsocketClientMessageWithMemories = {
    type: IrisChatWebsocketPayloadType.MESSAGE,
    message: mockClientMessageWithMemories,
} as IrisChatWebsocketDTO;

export const mockWebsocketStatusMessage = {
    type: IrisChatWebsocketPayloadType.STATUS,
    stages: [
        {
            name: 'Stage 1',
            state: IrisStageStateDTO.IN_PROGRESS,
        },
    ],
} as IrisChatWebsocketDTO;

export const mockConversation = {
    id: 1,
    exercise: irisExercise,
    messages: [mockClientMessage, mockServerMessage],
    chatMode: ChatServiceMode.COURSE,
    entityId: 1,
    creationDate: new Date(),
} as IrisSession;

export const mockConversationWithNoMessages = {
    id: 1,
    exercise: irisExercise,
    messages: [],
    chatMode: ChatServiceMode.COURSE,
    entityId: 1,
    creationDate: new Date(),
} as IrisSession;

export const mockServerSessionHttpResponse = {
    body: mockConversation,
} as HttpResponse<IrisSession>;

export const mockServerSessionHttpResponseWithEmptyConversation = {
    body: { ...mockConversationWithNoMessages, id: 123 },
} as HttpResponse<IrisSession>;

/**
 * Mocks a user message with the given content.
 * @param content - the content of the message
 */
export function mockUserMessageWithContent(content: string): IrisUserMessage {
    return {
        sender: IrisSender.USER,
        id: 3,
        content: [{ textContent: content } as IrisTextMessageContent],
        sentAt: dayjs(),
    } as IrisUserMessage;
}

/**
 * Mocks a server response with a conversation and an id.
 * @param id - the id of the conversation
 * @param empty - if true, the conversation will have no messages, defaults to false
 */
export function mockServerSessionHttpResponseWithId(id: number, empty: boolean = false): HttpResponse<IrisSession> {
    if (empty) {
        return {
            body: { ...mockConversationWithNoMessages, id },
        } as HttpResponse<IrisSession>;
    }
    return {
        body: { ...mockConversation, id },
    } as HttpResponse<IrisSession>;
}
