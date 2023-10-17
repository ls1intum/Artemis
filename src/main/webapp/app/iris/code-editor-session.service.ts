import { Injectable } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisSessionService } from 'app/iris/session.service';
import { IrisHttpCodeEditorSessionService } from 'app/iris/http-code-editor-session.service';
import { IrisHttpCodeEditorMessageService } from 'app/iris/http-code-editor-message.service';

/**
 * The IrisCodeEditorSessionService is responsible for managing Iris code editor sessions and retrieving their associated messages.
 */
@Injectable()
export class IrisCodeEditorSessionService extends IrisSessionService {
    /**
     * Uses the IrisHttpCodeEditorSessionService and IrisHttpCodeEditorMessageService to retrieve and manage Iris code editor sessions.
     */
    constructor(stateStore: IrisStateStore, httpSessionService: IrisHttpCodeEditorSessionService, httpMessageService: IrisHttpCodeEditorMessageService) {
        super(stateStore, httpSessionService, httpMessageService);
    }
}
