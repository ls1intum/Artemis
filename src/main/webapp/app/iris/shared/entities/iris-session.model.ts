import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import { IrisMessage } from 'app/iris/shared/entities/iris-message.model';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';

export class IrisSession implements BaseEntity {
    id: number;
    user?: User;
    messages?: IrisMessage[];
    latestSuggestions?: string;
    title?: string;
    creationDate: Date;
    chatMode: ChatServiceMode;
    entityId: number;
    type?: string;
}
