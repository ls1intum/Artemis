import { Component } from '@angular/core';
import { faCommentDots } from '@fortawesome/free-solid-svg-icons';
import { MatDialog } from '@angular/material/dialog';
import { ChatbotPopupComponent } from './chatbot-popup/chatbot-popup.component';
import { ExerciseChatWidgetComponent } from 'app/overview/exercise-chatbot/exercise-chatwidget/exercise-chat-widget.component';

@Component({
    selector: 'jhi-exercise-chatbot',
    templateUrl: './exercise-chatbot.component.html',
    styleUrls: ['./exercise-chatbot.component.scss'],
})
export class ExerciseChatbotComponent {
    public chatAccepted = 'false';

    // Icons
    faCommentDots = faCommentDots;

    constructor(private dialog: MatDialog) {}

    openDialog() {
        const dialogRef = this.dialog.open(ChatbotPopupComponent, {
            data: {
                name: 'User',
            },
        });

        dialogRef.afterClosed().subscribe((result) => {
            this.chatAccepted = result;
        });
    }

    openChat() {
        const dialogRef = this.dialog.open(ExerciseChatWidgetComponent);
    }
}
