import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { faCircle, faCompress, faPaperPlane, faXmark } from '@fortawesome/free-solid-svg-icons';
import { MatDialog } from '@angular/material/dialog';
import { HttpResponse } from '@angular/common/http';
import { IrisClientMessage, IrisMessage, IrisMessageContent, IrisMessageContentType, IrisSender, IrisServerMessage } from 'app/entities/iris/iris.model';
import { IrisMessageStore } from 'app/iris/message-store.service';
import { IrisHttpMessageService } from 'app/iris/http-message.service';

@Component({
    selector: 'jhi-exercise-chat-widget',
    templateUrl: './exercise-chat-widget.component.html',
    styleUrls: ['./exercise-chat-widget.component.scss'],
    providers: [IrisHttpMessageService, IrisMessageStore],
})
export class ExerciseChatWidgetComponent implements OnInit {
    @ViewChild('chatWidget') chatWidget!: ElementRef;
    @ViewChild('chatBody') chatBody!: ElementRef;

    readonly SENDER_USER = IrisSender.USER;
    readonly SENDER_SERVER = IrisSender.SERVER;

    messages: IrisMessage[] = [this.newServerMessage('Hey! How can I help you?')];
    newMessage = '';
    isLoading: boolean;
    dots = 1;

    constructor(private dialog: MatDialog, private irisHttpMessageService: IrisHttpMessageService) {}

    // Icons
    faPaperPlane = faPaperPlane;
    faCircle = faCircle;
    faCompress = faCompress;
    faXmark = faXmark;

    ngOnInit() {
        this.animateDots();
    }

    animateDots() {
        setInterval(() => {
            this.dots = this.dots < 3 ? (this.dots += 1) : (this.dots = 1);
        }, 500);
    }

    onSend(): void {
        if (this.newMessage) {
            this.isLoading = true;
            const message = this.newUserMessage(this.newMessage);
            this.messages.push(message);
            this.newMessage = '';
            this.irisHttpMessageService.createMessage(1, message).subscribe({
                next: (res: HttpResponse<IrisServerMessage>) => {
                    this.isLoading = false;
                    this.messages.push(this.newServerMessage('Some response'));
                },
                error: () => {
                    this.isLoading = false;
                    this.messages.push(this.newServerMessage('Something went wrong'));
                },
            });
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

    private newServerMessage(message: string): IrisServerMessage {
        const content: IrisMessageContent = {
            type: IrisMessageContentType.TEXT,
            textContent: message,
        };
        return {
            sender: this.SENDER_SERVER,
            content: content,
        };
    }

    private newUserMessage(message: string): IrisClientMessage {
        const content: IrisMessageContent = {
            type: IrisMessageContentType.TEXT,
            textContent: message,
        };
        return {
            sender: this.SENDER_USER,
            content: content,
        };
    }
}
