import { Component } from '@angular/core';

@Component({
    selector: 'jhi-exercise-chatwidget',
    templateUrl: './exercise-chatwidget.component.html',
    styleUrls: ['./exercise-chatwidget.component.scss'],
})
export class ExerciseChatwidgetComponent {
    messages: string[] = [];
    IrisMessages: string[] = ['Hey! how can I help you?'];
    userMessages: string[] = [];
    newMessage = '';

    onSend(): void {
        if (this.newMessage) {
            this.userMessages.push(this.newMessage);
            this.newMessage = '';
        }
    }
}
