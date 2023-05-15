import { Component } from '@angular/core';
import { faPaperPlane } from '@fortawesome/free-solid-svg-icons';
import { faCompress } from '@fortawesome/free-solid-svg-icons';
import { faXmark } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-exercise-chat-widget',
    templateUrl: './exercise-chat-widget.component.html',
    styleUrls: ['./exercise-chat-widget.component.scss'],
})
export class ExerciseChatWidgetComponent {
    messages: string[] = [];
    IrisMessages: string[] = ['Hey! how can I help you?'];
    userMessages: string[] = [];
    newMessage = '';

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
}
