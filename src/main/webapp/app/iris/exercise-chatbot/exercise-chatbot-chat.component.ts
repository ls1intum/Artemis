import { Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { IrisChatWebsocketService } from 'app/iris/chat-websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import { ActivatedRoute } from '@angular/router';
import { SharedService } from 'app/iris/shared.service';
import { IrisHeartbeatService } from 'app/iris/heartbeat.service';
import { IrisChatSessionService } from 'app/iris/chat-session.service';
import { ExerciseChatbotComponent } from 'app/iris/exercise-chatbot/exercise-chatbot.component';

@Component({
    selector: 'jhi-exercise-chatbot',
    templateUrl: './exercise-chatbot-chat.component.html',
    styleUrls: ['./exercise-chatbot-chat.component.scss'],
    providers: [IrisStateStore, IrisChatWebsocketService, IrisChatSessionService, IrisHeartbeatService],
})
export class ExerciseChatbotChatComponent extends ExerciseChatbotComponent {
    constructor(
        dialog: MatDialog,
        overlay: Overlay,
        sessionService: IrisChatSessionService,
        stateStore: IrisStateStore,
        // Note: These 2 unused services are injected to ensure that they are instantiated
        websocketService: IrisChatWebsocketService,
        private readonly heartbeatService: IrisHeartbeatService,
        route: ActivatedRoute,
        sharedService: SharedService,
    ) {
        super(dialog, overlay, sessionService, stateStore, websocketService, route, sharedService);
    }
}
