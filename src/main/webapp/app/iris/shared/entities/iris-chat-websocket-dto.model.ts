import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';
import { IrisMessageResponseDTO } from 'app/iris/shared/entities/iris-message-response-dto.model';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
import { IrisActivityItem, IrisRunState, IrisStatusError } from 'app/iris/shared/entities/iris-activity.model';

/**
 * Mirrors the server IrisChatWebsocketDTO record.
 * This is the wire format for status updates sent over the Iris chat WebSocket.
 */
export interface IrisChatWebsocketDTO {
    type: IrisChatWebsocketPayloadType;
    message?: IrisMessageResponseDTO;
    runState?: IrisRunState;
    error?: IrisStatusError;
    activities?: IrisActivityItem[];
    activitySeq?: number;
    final?: boolean;
    rateLimitInfo?: IrisRateLimitInformation;
    suggestions?: string[];
    sessionTitle?: string;
    citationInfo?: IrisCitationMetaDTO[];
    runId?: string;
    partialResult?: string;
    partialSeq?: number;
}

export enum IrisChatWebsocketPayloadType {
    MESSAGE = 'MESSAGE',
    STATUS = 'STATUS',
    PARTIAL = 'PARTIAL',
}
