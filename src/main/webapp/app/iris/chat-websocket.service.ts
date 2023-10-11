import { Injectable } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import { RateLimitUpdatedAction } from 'app/iris/state-store.model';
import { IrisRateLimitInformation, IrisWebsocketService } from "app/iris/websocket.service";

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
    constructor(
        jhiWebsocketService: JhiWebsocketService,
        stateStore: IrisStateStore,
    ) {
        super(jhiWebsocketService, stateStore, 'sessions');
    }

    protected handleRateLimitInfo(rateLimitInfo: IrisRateLimitInformation) {
        this.stateStore.dispatch(new RateLimitUpdatedAction(rateLimitInfo.currentMessageCount, rateLimitInfo.rateLimit));
    }
}
