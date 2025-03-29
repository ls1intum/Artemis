import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisMessageContent, IrisTextMessageContent } from 'app/entities/iris/iris-content-type.model';
import dayjs from 'dayjs/esm';

/**
 * The IrisMessage class is used to represent a message in the Iris system.
 * It can be either an assistant message, a user message, or a tutor suggestion message.
 */
export enum IrisSender {
    LLM = 'LLM',
    USER = 'USER',
    TUT_SUG = 'TUT_SUG',
}

export class IrisAssistantMessage implements BaseEntity {
    id: number;
    content: IrisMessageContent[];
    sentAt: dayjs.Dayjs;
    sender: IrisSender.LLM;
    helpful?: boolean;
}

export class IrisUserMessage implements BaseEntity {
    id?: number;
    content: IrisTextMessageContent[];
    sentAt?: dayjs.Dayjs;
    sender: IrisSender.USER;
    messageDifferentiator?: number;
}

/**
 * This message type is used to send a request for a tutor suggestion to Iris.
 */
export class IrisTutorSuggestionRequestMessage implements BaseEntity {
    id?: number;
    content: IrisTextMessageContent[];
    sentAt?: dayjs.Dayjs;
    sender: IrisSender.TUT_SUG;
}

export type IrisMessage = IrisAssistantMessage | IrisUserMessage | IrisTutorSuggestionRequestMessage;
