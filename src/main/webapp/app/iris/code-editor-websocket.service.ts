import { Injectable } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisWebsocketDTO, IrisWebsocketService } from "app/iris/websocket.service";

/**
 * The IrisCodeEditorWebsocketService handles the websocket communication for receiving messages in the code editor channels.
 */
@Injectable()
export class IrisCodeEditorWebsocketService extends IrisWebsocketService {

    /**
     * Creates an instance of IrisCodeEditorWebsocketService.
     * @param jhiWebsocketService The JhiWebsocketService for websocket communication.
     * @param stateStore The IrisStateStore for managing the state of the application.
     */
    constructor(
        jhiWebsocketService: JhiWebsocketService,
        stateStore: IrisStateStore,
    ) {
        super(jhiWebsocketService, stateStore, 'code-editor-sessions');
    }

    protected handleChanges(_websocketResponse: IrisWebsocketDTO) {
        //TODO: interact with code editor
    }
}
