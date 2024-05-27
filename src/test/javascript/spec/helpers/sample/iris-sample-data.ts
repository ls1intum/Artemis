import dayjs from 'dayjs/esm';
import { ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { IrisAssistantMessage, IrisSender, IrisUserMessage } from 'app/entities/iris/iris-message.model';
import { IrisTextMessageContent } from 'app/entities/iris/iris-content-type.model';
import { IrisSession } from 'app/entities/iris/iris-session.model';
import { IrisChatWebsocketDTO, IrisChatWebsocketPayloadType } from 'app/entities/iris/iris-chat-websocket-dto.model';
import { IrisStageStateDTO } from 'app/entities/iris/iris-stage-dto.model';

const map = new Map<string, any>();
map.set('model', 'gpt-4');

export const mockMessageContent = {
    textContent: 'Hello, world!',
} as IrisTextMessageContent;

export const irisExercise = { id: 1, title: 'Metis  Exercise', type: ExerciseType.PROGRAMMING } as ProgrammingExercise;

export const mockServerMessage = {
    sender: IrisSender.LLM,
    id: 1,
    content: [mockMessageContent],
    sentAt: dayjs(),
} as IrisAssistantMessage;

export const mockClientMessage = {
    sender: IrisSender.USER,
    content: [mockMessageContent],
    sentAt: dayjs(),
} as IrisUserMessage;

export const mockWebsocketServerMessage = {
    type: IrisChatWebsocketPayloadType.MESSAGE,
    message: mockServerMessage,
    stages: [],
} as IrisChatWebsocketDTO;

export const mockWebsocketClientMessage = {
    type: IrisChatWebsocketPayloadType.MESSAGE,
    message: mockClientMessage,
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
} as IrisSession;

export const mockConversationWithNoMessages = {
    id: 1,
    exercise: irisExercise,
    messages: [],
} as IrisSession;

export function mockServerMessageWithContent(content: string): IrisUserMessage {
    return {
        sender: IrisSender.USER,
        id: 2,
        content: [{ textContent: content } as IrisTextMessageContent],
    } as IrisUserMessage;
}
