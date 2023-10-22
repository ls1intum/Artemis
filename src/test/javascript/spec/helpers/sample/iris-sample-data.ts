import dayjs from 'dayjs/esm';
import { ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { IrisArtemisClientMessage, IrisSender, IrisServerMessage, IrisUserMessage } from 'app/entities/iris/iris-message.model';
import { IrisMessagePlanContent, IrisMessageTextContent } from 'app/entities/iris/iris-content-type.model';
import { IrisSession } from 'app/entities/iris/iris-session.model';
import { IrisChatWebsocketDTO, IrisChatWebsocketMessageType } from 'app/iris/chat-websocket.service';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import { ExerciseComponent, IrisExercisePlanStep } from 'app/entities/iris/iris-exercise-plan-component.model';

const map = new Map<string, any>();
map.set('model', 'gpt-4');

export const mockMessageContent = {
    textContent: 'Hello, world!',
} as IrisMessageTextContent;

export const mockExercisePlanComponent = {
    id: 2,
    exercisePlan: 1,
    component: ExerciseComponent.PROBLEM_STATEMENT,
} as IrisExercisePlanStep;
export const mockMessagePlanContent = {
    id: 2,
    components: [mockExercisePlanComponent],
    currentComponentIndex: 0,
} as IrisMessagePlanContent;
export const irisExercise = { id: 1, title: 'Metis  Exercise', type: ExerciseType.PROGRAMMING } as ProgrammingExercise;

export const mockServerPlanMessage = {
    sender: IrisSender.LLM,
    id: 2,
    content: [mockMessagePlanContent],
    sentAt: dayjs(),
} as IrisServerMessage;

export const mockServerMessage = {
    sender: IrisSender.LLM,
    id: 1,
    content: [mockMessageContent],
    sentAt: dayjs(),
} as IrisServerMessage;

export const mockArtemisClientMessage = {
    sender: IrisSender.ARTEMIS_CLIENT,
    content: [mockMessageContent],
    sentAt: dayjs(),
} as IrisArtemisClientMessage;

export const mockWebsocketMessage = {
    type: IrisChatWebsocketMessageType.MESSAGE,
    message: mockServerMessage,
} as IrisChatWebsocketDTO;

export const mockClientMessage = {
    sender: IrisSender.USER,
    content: [mockMessageContent],
    sentAt: dayjs(),
} as IrisUserMessage;

export const mockWebsocketServerMessage = {
    type: IrisChatWebsocketMessageType.MESSAGE,
    message: mockServerMessage,
} as IrisChatWebsocketDTO;

export const mockWebsocketClientMessage = {
    type: IrisChatWebsocketMessageType.MESSAGE,
    message: mockClientMessage,
} as IrisChatWebsocketDTO;

export const mockWebsocketKnownError = {
    type: IrisChatWebsocketMessageType.ERROR,
    errorTranslationKey: IrisErrorMessageKey.NO_MODEL_AVAILABLE,
    translationParams: map,
} as IrisChatWebsocketDTO;

export const mockWebsocketUnknownError = {
    type: IrisChatWebsocketMessageType.ERROR,
} as IrisChatWebsocketDTO;

export const mockConversation = {
    id: 1,
    exercise: irisExercise,
    messages: [mockClientMessage, mockServerMessage],
} as IrisSession;

export const mockPlanConversation = {
    id: 2,
    exercise: irisExercise,
    messages: [mockClientMessage, mockServerPlanMessage],
} as IrisSession;

export const mockState = {
    messages: [],
    isLoading: false,
    error: null,
    numNewMessages: 0,
    sessionId: 0,
    serverResponseTimeout: null,
    currentMessageCount: -1,
    rateLimit: -1,
    rateLimitTimeframeHours: -1,
};
