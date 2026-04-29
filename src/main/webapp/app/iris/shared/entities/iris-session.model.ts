import { BaseEntity } from 'app/shared/model/base-entity';
import { IrisMessage } from 'app/iris/shared/entities/iris-message.model';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-chat-mode.model';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';

export class IrisSession implements BaseEntity {
    id: number;
    userId: number;
    messages?: IrisMessage[];
    latestSuggestions?: string;
    title?: string;
    creationDate: Date;
    mode: ChatServiceMode;
    entityId: number;
    type?: string;
    citationInfo?: IrisCitationMetaDTO[];
}
