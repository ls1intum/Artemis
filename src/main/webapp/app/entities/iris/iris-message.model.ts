import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisMessageContent, IrisTextMessageContent } from 'app/entities/iris/iris-content-type.model';
import dayjs from 'dayjs/esm';

export enum IrisSender {
    LLM = 'LLM',
    USER = 'USER',
}

export class IrisAssistantMessage implements BaseEntity {
    id: number;
    content: IrisMessageContent[];
    sentAt: dayjs.Dayjs;
    sender: IrisSender.LLM;
    helpful?: boolean;
    num_input_tokens?: number;
    num_output_tokens?: number;
}

export class IrisUserMessage implements BaseEntity {
    id?: number;
    content: IrisTextMessageContent[];
    sentAt?: dayjs.Dayjs;
    sender: IrisSender.USER;
    messageDifferentiator?: number;
    num_input_tokens?: number;
    num_output_tokens?: number;
}

export type IrisMessage = IrisAssistantMessage | IrisUserMessage;
