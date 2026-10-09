import { BaseEntity } from 'app/foundation/model/base-entity';
import { IrisMessage } from 'app/iris/shared/entities/iris-message.model';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-session-context.model';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';

/** Query parameter of the course Iris page naming an existing session to open instead of the current one. */
export const IRIS_SESSION_QUERY_PARAM = 'irisSession';

export interface IrisSession extends BaseEntity {
    id: number;
    userId: number;
    messages?: IrisMessage[];
    latestSuggestions?: string;
    title?: string;
    creationDate: Date;
    mode?: ChatServiceMode;
    entityId: number;
    citationInfo?: IrisCitationMetaDTO[];
}
