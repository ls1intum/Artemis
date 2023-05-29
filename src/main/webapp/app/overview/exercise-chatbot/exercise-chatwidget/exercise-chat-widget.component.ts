import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { faPaperPlane } from '@fortawesome/free-solid-svg-icons';
import { faExpand } from '@fortawesome/free-solid-svg-icons';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-exercise-chat-widget',
    templateUrl: './exercise-chat-widget.component.html',
    styleUrls: ['./exercise-chat-widget.component.scss'],
})
export class ExerciseChatWidgetComponent implements OnInit {
    @ViewChild('chatWidget') chatWidget!: ElementRef;
    @ViewChild('chatBody') chatBody!: ElementRef;
    messages: string[] = [];
    irisMessages: string[] = ['Hey! How can I help you?'];
    userMessages: string[] = [];
    newMessage = '';
    componentClass = 'chat-widget';

    constructor(private dialog: MatDialog, private route: ActivatedRoute) {}

    // Icons
    faPaperPlane = faPaperPlane;
    faExpand = faExpand;
    faXmark = faXmark;

    ngOnInit() {
        this.route.url.subscribe((data) => {
            if (data[0].path === 'chat') {
                this.setFullScreenClass();
            } else {
                this.setDefaultClass();
            }
        });
    }

    private setFullScreenClass() {
        // Add logic to set the CSS class for route A
        this.componentClass = 'chat-widget-fullscreen';
    }

    private setDefaultClass() {
        // Add logic to set the CSS class for route B
        this.componentClass = 'chat-widget';
    }

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

    closeChat() {
        this.dialog.closeAll();
    }

    openComponentInNewWindow() {
        window.open('/chat', '_blank', 'width=400,height=600');
        this.closeChat();
    }
}
