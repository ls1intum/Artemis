import { ChatServiceMode } from 'app/iris/shared/entities/iris-chat-mode.model';

export class IrisSessionDTO {
    id: number;
    title?: string;
    creationDate: Date;
    chatMode: ChatServiceMode;
    entityId: number;
    entityName?: string;
}
