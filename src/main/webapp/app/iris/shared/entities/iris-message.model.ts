import { BaseEntity } from 'app/foundation/model/base-entity';
import { IrisMessageContent, IrisTextMessageContent } from 'app/iris/shared/entities/iris-content-type.model';
import dayjs from 'dayjs/esm';
import { MemirisMemory } from 'app/iris/shared/entities/memiris.model';
import { IrisActivityItem } from 'app/iris/shared/entities/iris-activity.model';

/**
 * The IrisMessage class is used to represent a message in the Iris system.
 * It can be either an assistant message, a user message, or a tutor suggestion message.
 */
export enum IrisSender {
    LLM = 'LLM',
    USER = 'USER',
    ARTIFACT = 'ARTIFACT',
    CTXSWAP = 'CTXSWAP',
    COMMAND = 'COMMAND',
}

/** Kept as a class because it is used as a value (constructor) with the `as` pipe in templates; fields are populated from server data after construction, hence the definite-assignment (!) markers. */
export class IrisAssistantMessage implements BaseEntity {
    id!: number;
    content!: IrisMessageContent[];
    sentAt!: dayjs.Dayjs;
    sender!: IrisSender.LLM;
    helpful?: boolean;
    accessedMemories?: MemirisMemory[];
    createdMemories?: MemirisMemory[];
    activities?: IrisActivityItem[];
    final?: boolean;
}

export interface IrisUserMessage extends BaseEntity {
    id?: number;
    content: IrisTextMessageContent[];
    sentAt?: dayjs.Dayjs;
    sender: IrisSender.USER;
    messageDifferentiator?: number;
    accessedMemories?: MemirisMemory[];
    createdMemories?: MemirisMemory[];
}

export interface IrisArtifactMessage extends BaseEntity {
    id?: number;
    content: IrisTextMessageContent[];
    sentAt?: dayjs.Dayjs;
    sender: IrisSender.ARTIFACT;
    accessedMemories?: MemirisMemory[];
    createdMemories?: MemirisMemory[];
}

export class IrisContextSwitchMessage implements BaseEntity {
    id?: number;
    content!: IrisMessageContent[];
    sentAt?: dayjs.Dayjs;
    sender!: IrisSender.CTXSWAP;
    accessedMemories?: never;
    createdMemories?: never;
}

/**
 * A system-generated marker recording an action Iris performed on the client, such as pointing the
 * student to a slide page / video timestamp in the combined view. Its content is JSON describing the
 * action; the client renders it as a clickable navigation marker.
 */
export class IrisCommandMessage implements BaseEntity {
    id?: number;
    content!: IrisMessageContent[];
    sentAt?: dayjs.Dayjs;
    sender!: IrisSender.COMMAND;
    accessedMemories?: MemirisMemory[];
    createdMemories?: MemirisMemory[];
}

export type IrisMessage = IrisAssistantMessage | IrisUserMessage | IrisArtifactMessage | IrisContextSwitchMessage | IrisCommandMessage;
