import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import { IrisMessage } from 'app/iris/shared/entities/iris-message.model';

export class IrisSession implements BaseEntity {
    id: number;
    user?: User;
    messages?: IrisMessage[];
    latestSuggestions?: string;
}
