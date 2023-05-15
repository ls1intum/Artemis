import { Component, EventEmitter, Output } from '@angular/core';
import { faPaperPlane } from '@fortawesome/free-solid-svg-icons';
import { faCompress } from '@fortawesome/free-solid-svg-icons';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { MatDialog } from '@angular/material/dialog';

@Component({
    selector: 'jhi-exercise-chat-widget',
    templateUrl: './exercise-chat-widget.component.html',
    styleUrls: ['./exercise-chat-widget.component.scss'],
})
export class ExerciseChatWidgetComponent {
    messages: string[] = [];
    irisMessages: string[] = ['Hey! How can I help you?'];
    userMessages: string[] = [];
    newMessage = '';
    @Output() chatWidgetClosed = new EventEmitter<void>();

    constructor(private dialog: MatDialog) {}

    // Icons
    faPaperPlane = faPaperPlane;
    faCompress = faCompress;
    faXmark = faXmark;

    onSend(): void {
        if (this.newMessage) {
            this.userMessages.push(this.newMessage);
            this.newMessage = '';
        }
    }

    closeChat() {
        this.dialog.closeAll();
        this.chatWidgetClosed.emit();
    }
}
