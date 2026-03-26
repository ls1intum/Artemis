import { BaseEntity } from 'app/shared/model/base-entity';
import { User } from 'app/core/user/user.model';
import { IrisMessage } from 'app/iris/shared/entities/iris-message.model';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';

export class IrisSession implements BaseEntity {
    id: number;
    user?: User;
    messages?: IrisMessage[];
    latestSuggestions?: string;
    title?: string;
    creationDate: Date;
    courseId?: number;
    exerciseId?: number;
    lectureId?: number;
    type?: string;
    citationInfo?: IrisCitationMetaDTO[];
}
