import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';

export interface IrisSessionDTO {
    id: number;
    title?: string;
    creationDate: Date;
    mode: ChatServiceMode;
    entityId: number;
    entityName?: string;
}
