import { Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { IrisStateStore } from 'app/iris/state-store.service';
import { ActivatedRoute } from '@angular/router';
import { SharedService } from 'app/iris/shared.service';
import { IrisChatbotButtonComponent } from 'app/iris/exercise-chatbot/chatbot-button.component';
import { IrisLogoSize } from '../iris-logo/iris-logo.component';
import { IrisExerciseCreationWebsocketService } from 'app/iris/exercise-creation-websocket.service';
import { IrisExerciseCreationSessionService } from 'app/iris/exercise-creation-session.service';

@Component({
    selector: 'jhi-exercise-creation-chatbot-button',
    templateUrl: './code-editor-chatbot-button.component.html',
    providers: [IrisExerciseCreationSessionService],
})
export class IrisExerciseCreationChatbotButtonComponent extends IrisChatbotButtonComponent {
    protected readonly IrisLogoSize = IrisLogoSize;

    constructor(
        dialog: MatDialog,
        overlay: Overlay,
        sessionService: IrisExerciseCreationSessionService,
        stateStore: IrisStateStore,
        // Note: This unused service is injected to ensure that it is instantiated
        websocketService: IrisExerciseCreationWebsocketService,
        route: ActivatedRoute,
        sharedService: SharedService,
    ) {
        super(dialog, overlay, sessionService, stateStore, route, sharedService);
    }
}
