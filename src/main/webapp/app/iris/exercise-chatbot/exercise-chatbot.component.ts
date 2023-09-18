import { faChevronDown, faCircle, faCommentDots } from '@fortawesome/free-solid-svg-icons';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { ExerciseChatWidgetComponent } from 'app/iris/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';
import { Overlay } from '@angular/cdk/overlay';
import { IrisWebsocketService } from 'app/iris/websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisSessionService } from 'app/iris/session.service';
import { NumNewMessagesResetAction } from 'app/iris/state-store.model';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { SharedService } from 'app/iris/shared.service';
import { IrisHeartbeatService } from 'app/iris/heartbeat.service';

@Component({
    selector: 'jhi-exercise-chatbot',
    templateUrl: './exercise-chatbot.component.html',
    styleUrls: ['./exercise-chatbot.component.scss'],
    providers: [IrisStateStore, IrisWebsocketService, IrisSessionService, IrisHeartbeatService],
})
export class ExerciseChatbotComponent implements OnInit, OnDestroy {
    public buttonDisabled = false;
    dialogRef: MatDialogRef<ExerciseChatWidgetComponent> | null = null;
    chatOpen = false;
    closed = true;
    hasNewMessages = false;
    private exerciseId: number;
    private stateSubscription: Subscription;
    private chatOpenSubscription: Subscription;
    initialWidth = 330;
    initialHeight = 430;

    // Icons
    faCircle = faCircle;
    faCommentDots = faCommentDots;
    faChevronDown = faChevronDown;

    constructor(
        public dialog: MatDialog,
        private overlay: Overlay,
        private readonly sessionService: IrisSessionService,
        private readonly stateStore: IrisStateStore,
        private readonly websocketService: IrisWebsocketService,
        private readonly heartbeatService: IrisHeartbeatService,
        private route: ActivatedRoute,
        private sharedService: SharedService,
    ) {}

    ngOnInit() {
        // Subscribes to route params and gets the exerciseId from the route
        this.route.params.subscribe((params) => {
            this.exerciseId = parseInt(params['exerciseId'], 10);
            if (this.exerciseId != null) {
                this.sessionService.getCurrentSessionOrCreate(this.exerciseId);
            }
        });

        // Subscribes to the stateStore to check for new messages
        this.stateSubscription = this.stateStore.getState().subscribe((state) => {
            this.hasNewMessages = state.numNewMessages > 0;
        });

        // Subscribes to the sharedService to get the status of chatOpen
        this.chatOpenSubscription = this.sharedService.chatOpen.subscribe((status) => {
            this.chatOpen = status;
            this.closed = !status;
        });
    }

    ngOnDestroy() {
        // Closes the dialog if it is open
        if (this.dialogRef) {
            this.dialogRef.close();
        }
        // Unsubscribes from the stateSubscription
        this.stateSubscription.unsubscribe();
    }

    /**
     * Handles the click event of the button.
     * If the chat is open, it resets the number of new messages, closes the dialog, and sets chatOpen to false.
     * If the chat is closed, it opens the chat dialog and sets chatOpen to true.
     */
    public handleButtonClick() {
        if (this.chatOpen && this.dialogRef) {
            this.stateStore.dispatch(new NumNewMessagesResetAction());
            this.dialog.closeAll();
            this.chatOpen = false;
            this.closed = true;
        } else {
            this.chatOpen = true;
            this.openChat();
            this.closed = false;
        }
    }

    /**
     * Opens the chat dialog using MatDialog.
     * Sets the configuration options for the dialog, including position, size, and data.
     */
    openChat() {
        this.chatOpen = true;
        this.dialogRef = this.dialog.open(ExerciseChatWidgetComponent, {
            hasBackdrop: false,
            scrollStrategy: this.overlay.scrollStrategies.noop(),
            position: { bottom: '0px', right: '0px' },
            disableClose: true,
            data: {
                stateStore: this.stateStore,
                exerciseId: this.exerciseId,
                sessionService: this.sessionService,
            },
        });
    }
}
