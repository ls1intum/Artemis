import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisMessageContent } from 'app/entities/iris/iris-content-type.model';
import dayjs from 'dayjs/esm';

export enum IrisSender {
    LLM = 'LLM',
    USER = 'USER',
    ARTEMIS_SERVER = 'ARTEMIS_SERVER',
    ARTEMIS_CLIENT = 'ARTEMIS_CLIENT',
}

export class IrisArtemisClientMessage implements BaseEntity {
    id?: number;
    content: IrisMessageContent[];
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

export class IrisClientMessage implements BaseEntity {
    id?: number;
    content: IrisMessageContent[];
    sentAt?: dayjs.Dayjs;
    sender: IrisSender.USER;
    messageDifferentiator?: number;
}

export type IrisMessage = IrisServerMessage | IrisClientMessage | IrisArtemisClientMessage;

export function isServerSentMessage(message: IrisMessage): message is IrisServerMessage {
    return message.sender === IrisSender.ARTEMIS_SERVER || message.sender === IrisSender.LLM;
}

export function isArtemisClientSentMessage(message: IrisMessage): message is IrisServerMessage {
    return message.sender === IrisSender.ARTEMIS_CLIENT;
}

export function isStudentSentMessage(message: IrisMessage): message is IrisClientMessage {
    return message.sender === IrisSender.USER;
}
