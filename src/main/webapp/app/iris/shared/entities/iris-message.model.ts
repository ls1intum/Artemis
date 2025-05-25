import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisMessageContent, IrisTextMessageContent } from 'app/iris/shared/entities/iris-content-type.model';
import dayjs from 'dayjs/esm';

/**
 * The IrisMessage class is used to represent a message in the Iris system.
 * It can be either an assistant message, a user message, or a tutor suggestion message.
 */
export enum IrisSender {
    LLM = 'LLM',
    USER = 'USER',
    ARTIFACT = 'ARTIFACT',
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

export class IrisArtifactMessage implements BaseEntity {
    id?: number;
    content: IrisTextMessageContent[];
    sentAt?: dayjs.Dayjs;
    sender: IrisSender.ARTIFACT;
}

export type IrisMessage = IrisAssistantMessage | IrisUserMessage | IrisArtifactMessage;
