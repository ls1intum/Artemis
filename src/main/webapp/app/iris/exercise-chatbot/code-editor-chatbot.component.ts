import { Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { IrisStateStore } from 'app/iris/state-store.service';
import { ActivatedRoute } from '@angular/router';
import { SharedService } from 'app/iris/shared.service';
import { IrisChatbotComponent } from 'app/iris/exercise-chatbot/chatbot.component';
import { IrisCodeEditorSessionService } from 'app/iris/code-editor-session.service';
import { IrisCodeEditorWebsocketService } from 'app/iris/code-editor-websocket.service';
import { IrisLogoSize } from '../iris-logo/iris-logo.component';

@Component({
    selector: 'jhi-code-editor-chatbot',
    templateUrl: './code-editor-chatbot.component.html',
    styleUrls: ['./code-editor-chatbot.component.scss'],
    providers: [IrisStateStore, IrisCodeEditorWebsocketService, IrisCodeEditorSessionService],
})
export class CodeEditorChatbotComponent extends IrisChatbotComponent {
    protected readonly IrisLogoSize = IrisLogoSize;

    constructor(
        dialog: MatDialog,
        overlay: Overlay,
        sessionService: IrisCodeEditorSessionService,
        stateStore: IrisStateStore,
        // Note: This unused service is injected to ensure that it is instantiated
        websocketService: IrisCodeEditorWebsocketService,
        route: ActivatedRoute,
        sharedService: SharedService,
    ) {
        super(dialog, overlay, sessionService, stateStore, route, sharedService);
    }
}
