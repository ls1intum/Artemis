import { IrisClientMessageDescriptor, IrisConversation, IrisMessageContent, IrisMessageContentType, IrisSender, IrisServerMessageDescriptor } from 'app/entities/iris/iris.model';
import dayjs from 'dayjs';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';

export const mockMessageContent = {
    content: 'Hello, world!',
    type: IrisMessageContentType.TEXT,
} as IrisMessageContent;

export const irisExercise = { id: 1, title: 'Metis  Exercise', type: ExerciseType.TEXT } as Exercise;

export const mockServerMessage = {
    sender: IrisSender.SERVER,
    messageId: 1,
    messageContent: mockMessageContent,
    sentAt: dayjs(),
} as IrisServerMessageDescriptor;

export const mockClientMessage = {
    sender: IrisSender.USER,
    messageContent: mockMessageContent,
    sentAt: dayjs(),
} as IrisClientMessageDescriptor;

export const mockConversation = {
    id: 1,
    programmingExercise: irisExercise,
    messageDescriptors: [mockClientMessage, mockServerMessage],
    irisEnabled: true,
} as IrisConversation;
