import { Injectable } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisSessionService } from 'app/iris/session.service';
import { IrisHttpCodeEditorSessionService } from 'app/iris/http-code-editor-session.service';
import { IrisHttpCodeEditorMessageService } from 'app/iris/http-code-editor-message.service';

/**
 * The IrisSessionService is responsible for managing Iris sessions and retrieving their associated messages.
 */
@Injectable()
export class IrisCodeEditorSessionService extends IrisSessionService {
    constructor(stateStore: IrisStateStore, httpSessionService: IrisHttpCodeEditorSessionService, httpMessageService: IrisHttpCodeEditorMessageService) {
        super(stateStore, httpSessionService, httpMessageService);
    }
}
