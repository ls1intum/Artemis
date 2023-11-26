import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisMessageContent, IrisTextMessageContent } from 'app/entities/iris/iris-content-type.model';
import dayjs from 'dayjs/esm';

export enum IrisSender {
    LLM = 'LLM',
    USER = 'USER',
    ARTEMIS_SERVER = 'ARTEMIS_SERVER',
    ARTEMIS_CLIENT = 'ARTEMIS_CLIENT',
}

export class IrisArtemisClientMessage implements BaseEntity {
    id?: number;
    content: IrisTextMessageContent[];
    sentAt: dayjs.Dayjs;
    sender: IrisSender.ARTEMIS_CLIENT;
}

export class IrisServerMessage implements BaseEntity {
    id: number;
    content: IrisMessageContent[];
    sentAt: dayjs.Dayjs;
    sender: IrisSender.LLM | IrisSender.ARTEMIS_SERVER;
    helpful?: boolean;
}

export class IrisUserMessage implements BaseEntity {
    id?: number;
    content: IrisTextMessageContent[];
    sentAt?: dayjs.Dayjs;
    sender: IrisSender.USER;
    messageDifferentiator?: number;
}

export type IrisMessage = IrisServerMessage | IrisUserMessage | IrisArtemisClientMessage;

/**
 * Checks if a message is a server-sent message.
 * @param message - The message to check.
 * @returns A boolean indicating if the message is a server-sent message.
 */
export function isServerSentMessage(message: IrisMessage): message is IrisServerMessage {
    return message.sender === IrisSender.ARTEMIS_SERVER || message.sender === IrisSender.LLM;
}

/**
 * Checks if a message is a welcome message generated by the client.
 * @param message - The message to check.
 * @returns A boolean indicating if the message is a client-sent message.
 */
export function isArtemisClientSentMessage(message: IrisMessage): message is IrisServerMessage {
    return message.sender === IrisSender.ARTEMIS_CLIENT;
}

/**
 * Checks if a message is a student-sent message.
 * @param message - The message to check.
 * @returns A boolean indicating if the message is a student-sent message.
 */
export function isStudentSentMessage(message: IrisMessage): message is IrisUserMessage {
    return message.sender === IrisSender.USER;
}
