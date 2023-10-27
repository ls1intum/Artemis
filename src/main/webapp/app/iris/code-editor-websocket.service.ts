import { Injectable } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisRateLimitInformation, IrisWebsocketService } from 'app/iris/websocket.service';
import { IrisMessage } from 'app/entities/iris/iris-message.model';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import { Observable, Subject } from 'rxjs';

/**
 * The IrisCodeEditorWebsocketMessageType defines the type of message sent over the code editor websocket.
 */
enum IrisCodeEditorWebsocketMessageType {
    MESSAGE = 'MESSAGE',
    RELOAD = 'RELOAD',
    ERROR = 'ERROR',
}

/**
 * The IrisCodeEditorWebsocketDTO is the data transfer object for messages sent over the code editor websocket.
 * It either contains an IrisMessage or a map of changes or an error message.
 */
export class IrisCodeEditorWebsocketDTO {
    type: IrisCodeEditorWebsocketMessageType;
    message?: IrisMessage;
    errorTranslationKey?: IrisErrorMessageKey;
    translationParams?: Map<string, any>;
    rateLimitInfo?: IrisRateLimitInformation;
}

/**
 * The IrisCodeEditorWebsocketService handles the websocket communication for receiving messages in the code editor channels.
 */
@Injectable()
export class IrisCodeEditorWebsocketService extends IrisWebsocketService {
    private reloadSubject: Subject<void> = new Subject<void>();

    /**
     * Creates an instance of IrisCodeEditorWebsocketService.
     * @param jhiWebsocketService The JhiWebsocketService for websocket communication.
     * @param stateStore The IrisStateStore for managing the state of the application.
     */
    constructor(jhiWebsocketService: JhiWebsocketService, stateStore: IrisStateStore) {
        super(jhiWebsocketService, stateStore, 'code-editor-sessions');
    }

    protected handleWebsocketResponse(response: IrisCodeEditorWebsocketDTO): void {
        if (response.rateLimitInfo) {
            super.handleRateLimitInfo(response.rateLimitInfo);
        }
        switch (response.type) {
            case IrisCodeEditorWebsocketMessageType.MESSAGE:
                super.handleMessage(response.message);
                break;
            case IrisCodeEditorWebsocketMessageType.RELOAD:
                this.reloadSubject.next(); // notify subscribers to reload
                break;
            case IrisCodeEditorWebsocketMessageType.ERROR:
                super.handleError(response.errorTranslationKey, response.translationParams);
                break;
        }
    }

    /**
     * Returns a subject that notifies subscribers when the code editor should be reloaded.
     * @returns {Subject<void>}
     */
    public onPromptReload(): Observable<void> {
        return this.reloadSubject.asObservable();
    }
}
