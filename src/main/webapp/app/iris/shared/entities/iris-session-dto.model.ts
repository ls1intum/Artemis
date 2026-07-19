import { ChatServiceMode } from 'app/iris/shared/entities/iris-session-context.model';

export interface IrisSessionDTO {
    id: number;
    title?: string;
    creationDate: Date;
    mode: ChatServiceMode;
    entityId: number;
    entityName?: string;
}
