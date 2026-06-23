import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';
import { IrisMessageResponseDTO } from 'app/iris/shared/entities/iris-message-response-dto.model';
import { IrisStageDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';

/**
 * Mirrors the server IrisChatWebsocketDTO record.
 * This is the wire format for status updates sent over the Iris chat WebSocket.
 */
export class IrisChatWebsocketDTO {
    type: IrisChatWebsocketPayloadType;
    message?: IrisMessageResponseDTO;
    stages?: IrisStageDTO[];
    rateLimitInfo?: IrisRateLimitInformation;
    suggestions?: string[];
    sessionTitle?: string;
    citationInfo?: IrisCitationMetaDTO[];
}

export enum IrisChatWebsocketPayloadType {
    MESSAGE = 'MESSAGE',
    STATUS = 'STATUS',
}
