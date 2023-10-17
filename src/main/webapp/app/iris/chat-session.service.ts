import { Injectable } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisSessionService } from 'app/iris/session.service';
import { IrisHttpChatMessageService } from 'app/iris/http-chat-message.service';
import { IrisHttpChatSessionService } from 'app/iris/http-chat-session.service';

/**
 * The IrisChatSessionService is responsible for managing Iris chat sessions and retrieving their associated messages.
 */
@Injectable()
export class IrisChatSessionService extends IrisSessionService {
    /**
     * Uses the IrisHttpChatSessionService and IrisHttpChatMessageService to retrieve and manage Iris chat sessions.
     */
    constructor(stateStore: IrisStateStore, httpSessionService: IrisHttpChatSessionService, httpMessageService: IrisHttpChatMessageService) {
        super(stateStore, httpSessionService, httpMessageService);
    }
}
