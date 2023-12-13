import { Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { IrisChatWebsocketService } from 'app/iris/chat-websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import { ActivatedRoute } from '@angular/router';
import { SharedService } from 'app/iris/shared.service';
import { IrisHeartbeatService } from 'app/iris/heartbeat.service';
import { IrisChatSessionService } from 'app/iris/chat-session.service';
import { IrisChatbotButtonComponent } from 'app/iris/exercise-chatbot/chatbot-button.component';
import { IrisLogoLookDirection, IrisLogoSize } from 'app/iris/iris-logo/iris-logo.component';

@Component({
    selector: 'jhi-tutor-chatbot-button',
    templateUrl: './tutor-chatbot-button.component.html',
    styleUrls: ['./tutor-chatbot-button.component.scss'],
    providers: [IrisChatWebsocketService, IrisChatSessionService, IrisHeartbeatService],
})
export class IrisTutorChatbotButtonComponent extends IrisChatbotButtonComponent {
    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly IrisLogoLookDirection = IrisLogoLookDirection;

    constructor(
        dialog: MatDialog,
        overlay: Overlay,
        sessionService: IrisChatSessionService,
        stateStore: IrisStateStore,
        // Note: These 2 unused services are injected to ensure that they are instantiated
        websocketService: IrisChatWebsocketService,
        heartbeatService: IrisHeartbeatService,
        route: ActivatedRoute,
        sharedService: SharedService,
    ) {
        super(dialog, overlay, sessionService, stateStore, route, sharedService);
    }
}
