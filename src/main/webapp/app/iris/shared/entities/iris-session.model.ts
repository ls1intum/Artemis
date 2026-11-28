import { BaseEntity } from 'app/foundation/model/base-entity';
import { IrisMessageResponseDTO } from 'app/iris/shared/entities/iris-message-response-dto.model';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-session-context.model';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';

/** Query parameter of the course Iris page naming an existing session to open instead of the current one. */
export const IRIS_SESSION_QUERY_PARAM = 'irisSession';

export interface IrisSession extends BaseEntity {
    id: number;
    userId: number;
    messages?: IrisMessageResponseDTO[];
    latestSuggestions?: string;
    title?: string;
    creationDate: string;
    mode?: ChatServiceMode;
    entityId?: number;
    citationInfo?: IrisCitationMetaDTO[];
}
