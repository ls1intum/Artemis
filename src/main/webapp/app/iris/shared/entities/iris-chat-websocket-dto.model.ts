import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';
import { IrisMessage } from 'app/iris/shared/entities/iris-message.model';
import { IrisStageDTO } from 'app/iris/shared/entities/iris-stage-dto.model';

/**
 * The IrisChatWebsocketDTO is the data transfer object for messages and status updates sent over the iris chat websocket.
 */
export class IrisChatWebsocketDTO {
    type: IrisChatWebsocketPayloadType;
    message?: IrisMessage;
    stages?: IrisStageDTO[];
    rateLimitInfo?: IrisRateLimitInformation;
    suggestions?: string[];
    sessionTitle?: string;
}

export enum IrisChatWebsocketPayloadType {
    MESSAGE = 'MESSAGE',
    STATUS = 'STATUS',
}
