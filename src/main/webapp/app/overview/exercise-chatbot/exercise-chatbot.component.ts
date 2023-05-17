import { Component } from '@angular/core';
import { faCommentDots } from '@fortawesome/free-solid-svg-icons';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { ChatbotPopupComponent } from './chatbot-popup/chatbot-popup.component';
import { ExerciseChatWidgetComponent } from 'app/overview/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';
import { NavigationStart, Router } from '@angular/router';

@Component({
    selector: 'jhi-exercise-chatbot',
    templateUrl: './exercise-chatbot.component.html',
    styleUrls: ['./exercise-chatbot.component.scss'],
})
export class ExerciseChatbotComponent {
    public chatAccepted = 'false';
    public buttonDisabled = false;
    private dialogRef: MatDialogRef<ExerciseChatWidgetComponent> | null = null;

    // Icons
    faCommentDots = faCommentDots;

    constructor(private dialog: MatDialog, private router: Router) {
        this.router.events.subscribe((event) => {
            if (event instanceof NavigationStart) {
                if (this.dialogRef) {
                    this.dialogRef.close();
                }
            }
        });
    }

    handleButtonClick() {
        if (this.chatAccepted === 'true') {
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
            this.dialogRef = this.dialog.open(ExerciseChatWidgetComponent, {
                hasBackdrop: false,
                position: { bottom: '0px', right: '0px' },
            });
            this.dialogRef.afterClosed().subscribe(() => {
                this.buttonDisabled = false;
            });
        }
        this.buttonDisabled = true;
    }
}
