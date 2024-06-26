import { IrisRateLimitInformation } from 'app/entities/iris/iris-ratelimit-info.model';
import { IrisMessage } from 'app/entities/iris/iris-message.model';
import { IrisStageDTO } from 'app/entities/iris/iris-stage-dto.model';

/**
 * The IrisChatWebsocketDTO is the data transfer object for messages and status updates sent over the iris chat websocket.
 */
export class IrisChatWebsocketDTO {
    type: IrisChatWebsocketPayloadType;
    message?: IrisMessage;
    stages?: IrisStageDTO[];
    rateLimitInfo?: IrisRateLimitInformation;
    suggestions?: string[];
}

export enum IrisChatWebsocketPayloadType {
    MESSAGE = 'MESSAGE',
    STATUS = 'STATUS',
}
