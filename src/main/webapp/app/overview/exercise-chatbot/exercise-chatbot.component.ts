import { Component } from '@angular/core';
import { faCommentDots } from '@fortawesome/free-solid-svg-icons';
import { MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { ChatbotPopupComponent } from './chatbot-popup/chatbot-popup.component';
import { ExerciseChatWidgetComponent } from 'app/overview/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';

@Component({
    selector: 'jhi-exercise-chatbot',
    templateUrl: './exercise-chatbot.component.html',
    styleUrls: ['./exercise-chatbot.component.scss'],
})
export class ExerciseChatbotComponent {
    public chatAccepted = 'false';
    public buttonDisabled = false;

    // Icons
    faCommentDots = faCommentDots;

    constructor(private dialog: MatDialog) {}

    handleButtonClick() {
        if (this.chatAccepted === 'true') {
            // Logic for chat accepted
            this.openChat();
        } else {
            // Logic for chat not accepted
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
            const dialogRef = this.dialog.open(ExerciseChatWidgetComponent, { hasBackdrop: false, position: { bottom: '0px', right: '0px' } });
            dialogRef.componentInstance.chatWidgetClosed.subscribe(() => {
                this.buttonDisabled = false;
            });
        }
        this.buttonDisabled = true;
    }
}
