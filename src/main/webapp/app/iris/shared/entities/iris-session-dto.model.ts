import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisMessage } from 'app/iris/shared/entities/iris-message.model';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';

export class IrisSessionDto implements BaseEntity {
    id: number;
    userId: number;
    messages?: IrisMessage[];
    creationDate: Date;
    chatMode: ChatServiceMode;
    entityId: number;
}
