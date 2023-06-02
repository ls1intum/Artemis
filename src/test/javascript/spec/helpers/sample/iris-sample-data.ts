import dayjs from 'dayjs';
import { ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { IrisClientMessage, IrisSender, IrisServerMessage } from 'app/entities/iris/iris-message.model';
import { IrisMessageContent, IrisMessageContentType } from 'app/entities/iris/iris-content-type.model';
import { IrisSession } from 'app/entities/iris/iris-session.model';

export const mockMessageContent = {
    textContent: 'Hello, world!',
    type: IrisMessageContentType.TEXT,
} as IrisMessageContent;

export const irisExercise = { id: 1, title: 'Metis  Exercise', type: ExerciseType.PROGRAMMING } as ProgrammingExercise;

export const mockServerMessage = {
    sender: IrisSender.LLM,
    id: 1,
    content: [mockMessageContent],
    sentAt: dayjs(),
} as IrisServerMessage;

export const mockClientMessage = {
    sender: IrisSender.USER,
    content: [mockMessageContent],
    sentAt: dayjs(),
} as IrisClientMessage;

export const mockConversation = {
    id: 1,
    exercise: irisExercise,
    messages: [mockClientMessage, mockServerMessage],
} as IrisSession;

export const mockState = {
    messages: [],
    isLoading: false,
    error: '',
    numNewMessages: 0,
    sessionId: 0,
};
