import { Component, OnDestroy } from '@angular/core';
import { faCommentDots } from '@fortawesome/free-solid-svg-icons';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { ChatbotPopupComponent } from './chatbot-popup/chatbot-popup.component';
import { ExerciseChatWidgetComponent } from 'app/overview/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';
import { IrisMessageStore } from 'app/iris/message-store.service';
import { Overlay } from '@angular/cdk/overlay';

@Component({
    selector: 'jhi-exercise-chatbot',
    templateUrl: './exercise-chatbot.component.html',
    styleUrls: ['./exercise-chatbot.component.scss'],
})
export class ExerciseChatbotComponent implements OnDestroy {
    public chatAccepted = 'true';
    public buttonDisabled = false;
    dialogRef: MatDialogRef<ExerciseChatWidgetComponent> | null = null;
    chatOpen = false;
    // Icons
    faCommentDots = faCommentDots;

    constructor(public dialog: MatDialog, private overlay: Overlay, private messageStore: IrisMessageStore) {}

    ngOnDestroy() {
        if (this.dialogRef) {
            this.dialogRef.close();
        }
    }

    handleButtonClick() {
        if (this.chatOpen && this.dialogRef) {
            this.dialogRef!.close();
            this.chatOpen = false;
        } else if (this.chatAccepted === 'true') {
            this.openChat();
        } else {
            this.openDialog();
        }
    }

    openDialog() {
        const dialogRef = this.dialog.open(ChatbotPopupComponent, {});

        dialogRef.afterClosed().subscribe((result) => {
            this.chatAccepted = result;
            if (this.chatAccepted === 'true') {
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
                    messageStore: this.messageStore,
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
