import { BaseEntity } from 'app/foundation/model/base-entity';
import { IrisMessageContent, IrisTextMessageContent } from 'app/iris/shared/entities/iris-content-type.model';
import dayjs from 'dayjs/esm';
import { MemirisMemory } from 'app/iris/shared/entities/memiris.model';

/**
 * The IrisMessage class is used to represent a message in the Iris system.
 * It can be either an assistant message, a user message, or a tutor suggestion message.
 */
export enum IrisSender {
    LLM = 'LLM',
    USER = 'USER',
    ARTIFACT = 'ARTIFACT',
    COMMAND = 'COMMAND',
}

export class IrisAssistantMessage implements BaseEntity {
    id: number;
    content: IrisMessageContent[];
    sentAt: dayjs.Dayjs;
    sender: IrisSender.LLM;
    helpful?: boolean;
    accessedMemories?: MemirisMemory[];
    createdMemories?: MemirisMemory[];
}

export class IrisUserMessage implements BaseEntity {
    id?: number;
    content: IrisTextMessageContent[];
    sentAt?: dayjs.Dayjs;
    sender: IrisSender.USER;
    messageDifferentiator?: number;
    accessedMemories?: MemirisMemory[];
    createdMemories?: MemirisMemory[];
}

export class IrisArtifactMessage implements BaseEntity {
    id?: number;
    content: IrisTextMessageContent[];
    sentAt?: dayjs.Dayjs;
    sender: IrisSender.ARTIFACT;
    accessedMemories?: MemirisMemory[];
    createdMemories?: MemirisMemory[];
}

/**
 * A system-generated marker recording an action Iris performed on the client, such as pointing the
 * student to a slide page / video timestamp in the combined view. Its content is JSON describing the
 * action; the client renders it as a clickable navigation marker.
 */
export class IrisCommandMessage implements BaseEntity {
    id?: number;
    content: IrisMessageContent[];
    sentAt?: dayjs.Dayjs;
    sender: IrisSender.COMMAND;
    accessedMemories?: MemirisMemory[];
    createdMemories?: MemirisMemory[];
}

export type IrisMessage = IrisAssistantMessage | IrisUserMessage | IrisArtifactMessage | IrisCommandMessage;
