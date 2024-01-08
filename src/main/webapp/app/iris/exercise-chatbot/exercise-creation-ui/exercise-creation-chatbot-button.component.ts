import { Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { IrisStateStore } from 'app/iris/state-store.service';
import { ActivatedRoute } from '@angular/router';
import { SharedService } from 'app/iris/shared.service';
import { IrisChatbotButtonComponent } from 'app/iris/exercise-chatbot/chatbot-button.component';
import { IrisLogoSize } from '../../iris-logo/iris-logo.component';
import { IrisExerciseCreationWebsocketService } from 'app/iris/exercise-creation-websocket.service';
import { IrisExerciseCreationSessionService } from 'app/iris/exercise-creation-session.service';
import { ChatbotService } from 'app/iris/exercise-chatbot/exercise-creation-ui/chatbot.service';
import { NumNewMessagesResetAction } from 'app/iris/state-store.model';

@Component({
    selector: 'jhi-new-exercise-creation-chatbot-button',
    templateUrl: 'exercise-creation-chatbot-button.component.html',
    styleUrl: '../widget/chatbot-widget.component.scss',
    providers: [IrisExerciseCreationSessionService, ChatbotService],
})
export class IrisNewExerciseCreationChatbotButtonComponent extends IrisChatbotButtonComponent {
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
        private chatbotService: ChatbotService,
    ) {
        super(dialog, overlay, sessionService, stateStore, route, sharedService);
    }

    /**
     * Handles the click event of the button.
     * If the chat is open, it resets the number of new messages, closes the dialog, and sets chatOpen to false.
     * If the chat is closed, it opens the chat dialog and sets chatOpen to true.
     */
    public handleButtonClick() {
        if (this.chatOpen) {
            this.stateStore.dispatch(new NumNewMessagesResetAction());
            this.chatOpen = false;
        } else {
            this.openChat();
        }
    }

    openChat() {
        this.chatOpen = true;
        if (this.chatbotService) {
            console.log(this.chatOpen);
            this.chatbotService.displayChat();
        } else {
            console.error('ChatbotService is not initialized properly.');
        }
    }
}
