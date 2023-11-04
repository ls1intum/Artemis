import { Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { IrisStateStore } from 'app/iris/state-store.service';
import { ActivatedRoute } from '@angular/router';
import { SharedService } from 'app/iris/shared.service';
import { IrisChatbotButtonComponent } from 'app/iris/exercise-chatbot/chatbot-button.component';
import { IrisCodeEditorSessionService } from 'app/iris/code-editor-session.service';
import { IrisCodeEditorWebsocketService } from 'app/iris/code-editor-websocket.service';
import { IrisLogoSize } from '../iris-logo/iris-logo.component';

@Component({
    selector: 'jhi-code-editor-chatbot-button',
    templateUrl: './code-editor-chatbot-button.component.html',
    providers: [IrisCodeEditorSessionService],
})
export class IrisCodeEditorChatbotButtonComponent extends IrisChatbotButtonComponent {
    protected readonly IrisLogoSize = IrisLogoSize;

    constructor(
        dialog: MatDialog,
        overlay: Overlay,
        codeEditorSessionService: IrisCodeEditorSessionService,
        stateStore: IrisStateStore,
        // Note: This unused service is injected to ensure that it is instantiated
        websocketService: IrisCodeEditorWebsocketService,
        route: ActivatedRoute,
        sharedService: SharedService,
    ) {
        super(dialog, overlay, codeEditorSessionService, stateStore, route, sharedService);
    }
}
