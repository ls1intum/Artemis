import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';

export class IrisSessionDTO {
    id: number;
    creationDate: Date;
    chatMode: ChatServiceMode;
    entityId: number;
    entityName: string;
}
