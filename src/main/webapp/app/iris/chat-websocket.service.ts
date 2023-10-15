import { Injectable } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import { RateLimitUpdatedAction } from 'app/iris/state-store.model';
import { IrisWebsocketService } from 'app/iris/websocket.service';
import { IrisMessage } from 'app/entities/iris/iris-message.model';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';

/**
 * The IrisWebsocketMessageType defines the type of message sent over the websocket.
 */
export enum IrisChatWebsocketMessageType {
    MESSAGE = 'MESSAGE',
    ERROR = 'ERROR',
}

export class IrisRateLimitInformation {
    currentMessageCount: number;
    rateLimit: number;
    rateLimitTimeframeHours: number;

    constructor(currentMessageCount: number, rateLimit: number, rateLimitTimeframeHours: number) {
        this.currentMessageCount = currentMessageCount;
        this.rateLimit = rateLimit;
        this.rateLimitTimeframeHours = rateLimitTimeframeHours;
    }
}

/**
 * The IrisChatWebsocketDTO is the data transfer object for messages sent over the iris chat websocket.
 * It either contains an IrisMessage or an error message.
 */
export class IrisChatWebsocketDTO {
    type: IrisChatWebsocketMessageType;
    message?: IrisMessage;
    errorTranslationKey?: IrisErrorMessageKey;
    translationParams?: Map<string, any>;
    rateLimitInfo?: IrisRateLimitInformation;
}

/**
 * The IrisChatWebsocketService handles the websocket communication for receiving messages in exercise chat channels.
 */
@Injectable()
export class IrisChatWebsocketService extends IrisWebsocketService {
    /**
     * Creates an instance of IrisChatWebsocketService.
     * @param jhiWebsocketService The JhiWebsocketService for websocket communication.
     * @param stateStore The IrisStateStore for managing the state of the application.
     */
    constructor(jhiWebsocketService: JhiWebsocketService, stateStore: IrisStateStore) {
        super(jhiWebsocketService, stateStore, 'sessions');
    }

    protected handleWebsocketResponse(response: IrisChatWebsocketDTO) {
        if (response.rateLimitInfo) {
            this.handleRateLimitInfo(response.rateLimitInfo);
        }
        switch (response.type) {
            case IrisChatWebsocketMessageType.MESSAGE:
                super.handleMessage(response.message);
                break;
            case IrisChatWebsocketMessageType.ERROR:
                super.handleError(response.errorTranslationKey, response.translationParams);
                break;
        }
    }

    private handleRateLimitInfo(rateLimitInfo: IrisRateLimitInformation) {
        this.stateStore.dispatch(new RateLimitUpdatedAction(rateLimitInfo));
    }
}
