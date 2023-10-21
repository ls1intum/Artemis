import { Injectable } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisSessionService } from 'app/iris/session.service';
import { IrisHttpChatMessageService } from 'app/iris/http-chat-message.service';
import { IrisHttpChatSessionService } from 'app/iris/http-chat-session.service';
import { IrisClientMessage, IrisMessage } from 'app/entities/iris/iris-message.model';
import { firstValueFrom } from 'rxjs';

/**
 * The IrisChatSessionService is responsible for managing Iris chat sessions and retrieving their associated messages.
 */
@Injectable()
export class IrisChatSessionService extends IrisSessionService {
    /**
     * Uses the IrisHttpChatSessionService and IrisHttpChatMessageService to retrieve and manage Iris chat sessions.
     */
    constructor(
        stateStore: IrisStateStore,
        private irisHttpChatSessionService: IrisHttpChatSessionService,
        private irisHttpChatMessageService: IrisHttpChatMessageService,
    ) {
        super(stateStore, irisHttpChatSessionService, irisHttpChatMessageService);
    }

    async createMessage(sessionId: number, message: IrisClientMessage): Promise<IrisMessage> {
        const response = await firstValueFrom(this.irisHttpChatMessageService.createMessage(sessionId, message));
        return response.body!;
    }

    async resendMessage(sessionId: number, message: IrisClientMessage): Promise<IrisMessage> {
        const response = await firstValueFrom(this.irisHttpChatMessageService.resendMessage(sessionId, message));
        return response.body!;
    }
}
