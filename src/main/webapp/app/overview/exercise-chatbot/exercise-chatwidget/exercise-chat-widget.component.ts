import { Component, ElementRef, EventEmitter, HostListener, Output, ViewChild } from '@angular/core';
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
    @ViewChild('chatWidget') chatWidget!: ElementRef;
    @ViewChild('chatBody') chatBody!: ElementRef;
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
        this.scrollToBottom();
    }

    scrollToBottom() {
        setTimeout(() => {
            const chatBodyElement: HTMLElement = this.chatBody.nativeElement;
            chatBodyElement.scrollTop = chatBodyElement.scrollHeight;
        });
    }

    @HostListener('document:click', ['$event'])
    onDocumentClick(event: MouseEvent) {
        this.chatWidgetClosed.emit();
    }

    closeChat() {
        this.dialog.closeAll();
        this.chatWidgetClosed.emit();
    }
}
