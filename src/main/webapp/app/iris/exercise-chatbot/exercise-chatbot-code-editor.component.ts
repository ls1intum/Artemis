import { Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { IrisStateStore } from 'app/iris/state-store.service';
import { ActivatedRoute } from '@angular/router';
import { SharedService } from 'app/iris/shared.service';
import { ExerciseChatbotComponent } from 'app/iris/exercise-chatbot/exercise-chatbot.component';
import { IrisCodeEditorSessionService } from 'app/iris/code-editor-session.service';
import { IrisCodeEditorWebsocketService } from 'app/iris/code-editor-websocket.service';
import { IrisLogoSize } from '../iris-logo/iris-logo.component';

@Component({
    selector: 'jhi-code-editor-chatbot',
    templateUrl: './exercise-chatbot-code-editor.component.html',
    styleUrls: ['./exercise-chatbot-code-editor.component.scss'],
    providers: [IrisStateStore, IrisCodeEditorWebsocketService, IrisCodeEditorSessionService],
})
export class ExerciseChatbotCodeEditorComponent extends ExerciseChatbotComponent {
    constructor(
        dialog: MatDialog,
        overlay: Overlay,
        sessionService: IrisCodeEditorSessionService,
        stateStore: IrisStateStore,
        // Note: These 2 unused services are injected to ensure that they are instantiated
        websocketService: IrisCodeEditorWebsocketService,
        route: ActivatedRoute,
        sharedService: SharedService,
    ) {
        super(dialog, overlay, sessionService, stateStore, websocketService, route, sharedService);
    }

    protected readonly IrisLogoSize = IrisLogoSize;
}
