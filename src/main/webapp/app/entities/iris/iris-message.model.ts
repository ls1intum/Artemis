import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisMessageContent } from 'app/entities/iris/iris-content-type.model';
import dayjs from 'dayjs/esm';

export enum IrisSender {
    LLM = 'LLM',
    USER = 'USER',
    ARTEMIS = 'ARTEMIS',
}

export class IrisServerMessage implements BaseEntity {
    id: number;
    content: IrisMessageContent[];
    sentAt: dayjs.Dayjs;
    sender: IrisSender.LLM | IrisSender.ARTEMIS;
    helpful?: boolean;
}

export class IrisClientMessage implements BaseEntity {
    id?: number;
    content: IrisMessageContent[];
    sentAt?: dayjs.Dayjs;
    sender: IrisSender.USER;
}

export type IrisMessage = IrisServerMessage | IrisClientMessage;

export function isServerSentMessage(message: IrisMessage): message is IrisServerMessage {
    return message.sender === IrisSender.ARTEMIS || message.sender === IrisSender.LLM;
}

export function isStudentSentMessage(message: IrisMessage): message is IrisServerMessage {
    return message.sender === IrisSender.USER;
}
