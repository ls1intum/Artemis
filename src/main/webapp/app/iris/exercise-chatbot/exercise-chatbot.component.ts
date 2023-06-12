import { Component, OnDestroy, OnInit } from '@angular/core';
import { faCommentDots } from '@fortawesome/free-solid-svg-icons';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { ChatbotPopupComponent } from './chatbot-popup/chatbot-popup.component';
import { ExerciseChatWidgetComponent } from 'app/iris/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';
import { IrisWebsocketService } from 'app/iris/websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisSessionService } from 'app/iris/session.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-exercise-chatbot',
    templateUrl: './exercise-chatbot.component.html',
    styleUrls: ['./exercise-chatbot.component.scss'],
    providers: [IrisStateStore, IrisWebsocketService, IrisSessionService],
})
export class ExerciseChatbotComponent implements OnDestroy, OnInit {
    public chatAccepted = false;
    public buttonDisabled = false;
    private dialogRef: MatDialogRef<ExerciseChatWidgetComponent> | null = null;
    private chatOpen = false;
    private exerciseId: number;
    // Icons
    faCommentDots = faCommentDots;

    constructor(
        private dialog: MatDialog,
        private readonly sessionService: IrisSessionService,
        private readonly stateStore: IrisStateStore,
        private readonly websocketService: IrisWebsocketService,
        private route: ActivatedRoute,
    ) {}

    ngOnInit() {
        this.route.params.subscribe((params) => {
            this.exerciseId = parseInt(params['exerciseId'], 10);
            if (this.exerciseId != null) {
                this.sessionService.getCurrentSessionOrCreate(this.exerciseId);
            }
        });
    }

    ngOnDestroy() {
        if (this.dialogRef) {
            this.dialogRef.close();
        }
    }

    handleButtonClick() {
        if (this.chatOpen && this.dialogRef) {
            this.dialogRef!.close();
            this.chatOpen = false;
        } else if (this.chatAccepted) {
            this.openChat();
        } else {
            this.openDialog();
        }
    }

    openDialog() {
        const dialogRef = this.dialog.open(ChatbotPopupComponent, {});

        dialogRef.afterClosed().subscribe((result) => {
            this.chatAccepted = result;
            if (this.chatAccepted) {
                this.openChat();
            }
        });
    }

    openChat() {
        if (!this.buttonDisabled) {
            this.chatOpen = true;
            this.dialogRef = this.dialog.open(ExerciseChatWidgetComponent, {
                hasBackdrop: false,
                position: { bottom: '0px', right: '0px' },
                data: {
                    stateStore: this.stateStore,
                },
            });
            this.dialogRef.afterClosed().subscribe(() => {
                this.buttonDisabled = false;
                this.chatOpen = false;
            });
        }
        this.buttonDisabled = true;
    }
}
