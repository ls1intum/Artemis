import { faChevronDown, faCircle, faCommentDots } from '@fortawesome/free-solid-svg-icons';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { ExerciseChatWidgetComponent } from 'app/iris/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';
import { Overlay } from '@angular/cdk/overlay';
import { shakeAnimation } from 'angular-animations';
import { IrisWebsocketService } from 'app/iris/websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisSessionService } from 'app/iris/session.service';
import { NumNewMessagesResetAction } from 'app/iris/state-store.model';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { SharedService } from 'app/iris/shared.service';

@Component({
    selector: 'jhi-exercise-chatbot',
    templateUrl: './exercise-chatbot.component.html',
    styleUrls: ['./exercise-chatbot.component.scss'],
    providers: [IrisStateStore, IrisWebsocketService, IrisSessionService],
    animations: [shakeAnimation({ anchor: 'shake', direction: '=>', duration: 700 })],
})
export class ExerciseChatbotComponent implements OnInit, OnDestroy {
    public chatAccepted = false;
    public buttonDisabled = false;
    dialogRef: MatDialogRef<ExerciseChatWidgetComponent> | null = null;
    chatOpen = false;
    runAnimation = false;
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
        private route: ActivatedRoute,
        private sharedService: SharedService,
    ) {}

    ngOnInit() {
        this.route.params.subscribe((params) => {
            this.exerciseId = parseInt(params['exerciseId'], 10);
            if (this.exerciseId != null) {
                this.sessionService.getCurrentSessionOrCreate(this.exerciseId);
            }
        });

        this.chatOpenSubscription = this.sharedService.chatOpen.subscribe((status) => (this.chatOpen = status));

        this.stateSubscription = this.stateStore.getState().subscribe((state) => {
            this.hasNewMessages = state.numNewMessages > 0;
        });
    }

    ngOnDestroy() {
        if (this.dialogRef) {
            this.dialogRef.close();
        }
        this.stateSubscription.unsubscribe();
    }

    handleButtonClick() {
        if (this.chatOpen && this.dialogRef) {
            this.stateStore.dispatch(new NumNewMessagesResetAction());
            this.dialog.closeAll();
            //this.runAnimation = false;
            this.sharedService.changeChatOpenStatus(false);
        } else {
            this.openChat();
            //this.runAnimation = true;
        }
    }

    openChat() {
        this.dialogRef = this.dialog.open(ExerciseChatWidgetComponent, {
            hasBackdrop: false,
            scrollStrategy: this.overlay.scrollStrategies.noop(),
            position: { bottom: '0px', right: '0px' },
            disableClose: true,
            data: {
                stateStore: this.stateStore,
                widgetWidth: localStorage.getItem('widgetWidth') || `${this.initialWidth}px`,
                widgetHeight: localStorage.getItem('widgetHeight') || `${this.initialHeight}px`,
                fullSize: localStorage.getItem('fullSize') === 'true',
            },
        });
        this.dialogRef.afterOpened().subscribe(() => {
            this.sharedService.changeChatOpenStatus(true);
        });
        this.dialogRef.afterClosed().subscribe(() => {
            this.sharedService.changeChatOpenStatus(false);
        });
    }
}
