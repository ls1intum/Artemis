import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';

export class IrisSessionDTO {
    id: number;
    title?: string;
    creationDate: Date;
    mode: ChatServiceMode;
    entityId: number;
    entityName?: string;
}
