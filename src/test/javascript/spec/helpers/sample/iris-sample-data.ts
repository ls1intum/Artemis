import { IrisClientMessage, IrisMessageContent, IrisMessageContentType, IrisSender, IrisServerMessage, IrisSession } from 'app/entities/iris/iris.model';
import dayjs from 'dayjs';
import { ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

export const mockMessageContent = {
    textContent: 'Hello, world!',
    type: IrisMessageContentType.TEXT,
} as IrisMessageContent;

export const irisExercise = { id: 1, title: 'Metis  Exercise', type: ExerciseType.PROGRAMMING } as ProgrammingExercise;

export const mockServerMessage = {
    sender: IrisSender.SERVER,
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
