import { Injectable } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisSessionService } from 'app/iris/session.service';
import { IrisHttpChatMessageService } from 'app/iris/http-chat-message.service';
import { IrisHttpCourseChatSessionService } from 'app/iris/http-course-chat-session.service';

/**
 * The IrisChatSessionService is responsible for managing Iris chat sessions and retrieving their associated messages.
 */
@Injectable()
export class IrisCourseChatSessionService extends IrisSessionService {
    /**
     * Uses the IrisHttpChatSessionService and IrisHttpChatMessageService to retrieve and manage Iris chat sessions.
     */
    constructor(stateStore: IrisStateStore, irisSessionService: IrisHttpCourseChatSessionService, irisHttpChatMessageService: IrisHttpChatMessageService) {
        super(stateStore, irisSessionService, irisHttpChatMessageService);
    }
}
