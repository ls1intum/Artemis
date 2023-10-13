import { Injectable } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisSessionService } from 'app/iris/session.service';
import { IrisHttpChatMessageService } from 'app/iris/http-chat-message.service';
import { IrisHttpChatSessionService } from 'app/iris/http-chat-session.service';

/**
 * The IrisSessionService is responsible for managing Iris sessions and retrieving their associated messages.
 */
@Injectable()
export class IrisChatSessionService extends IrisSessionService {
    constructor(stateStore: IrisStateStore, httpSessionService: IrisHttpChatSessionService, httpMessageService: IrisHttpChatMessageService) {
        super(stateStore, httpSessionService, httpMessageService);
    }
}
