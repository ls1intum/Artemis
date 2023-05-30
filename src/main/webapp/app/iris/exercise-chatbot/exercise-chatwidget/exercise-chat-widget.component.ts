import { Component, ElementRef, Inject, OnInit, ViewChild } from '@angular/core';
import { faCircle, faExpand, faPaperPlane, faXmark } from '@fortawesome/free-solid-svg-icons';
import { MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { IrisClientMessage, IrisMessage, IrisMessageContent, IrisMessageContentType, IrisSender } from 'app/entities/iris/iris.model';
import { IrisStateStore } from 'app/iris/state-store.service';
import { ConversationErrorOccurredAction, StudentMessageSentAction } from 'app/iris/message-store.model';
import { IrisHttpMessageService } from 'app/iris/http-message.service';

@Component({
    selector: 'jhi-exercise-chat-widget',
    templateUrl: './exercise-chat-widget.component.html',
    styleUrls: ['./exercise-chat-widget.component.scss'],
})
export class ExerciseChatWidgetComponent implements OnInit {
    @ViewChild('chatWidget') chatWidget!: ElementRef;
    @ViewChild('chatBody') chatBody!: ElementRef;

    readonly SENDER_USER = IrisSender.USER;
    readonly SENDER_SERVER = IrisSender.SERVER;
    readonly stateStore: IrisStateStore;

    messages: IrisMessage[] = [];
    newMessageTextContent = '';
    isLoading: boolean;
    sessionId: number;
    error = ''; // TODO: error object
    dots = 1;

    constructor(private dialog: MatDialog, @Inject(MAT_DIALOG_DATA) public data: any, private httpMessageService: IrisHttpMessageService) {
        this.stateStore = data.stateStore;
    }

    // Icons
    faPaperPlane = faPaperPlane;
    faCircle = faCircle;
    faExpand = faExpand;
    faXmark = faXmark;

    ngOnInit() {
        this.scrollToBottom('auto');
        this.animateDots();
        this.stateStore.getState().subscribe((state) => {
            this.messages = state.messages as IrisMessage[];
            this.isLoading = state.isLoading;
            this.error = state.error;
            this.sessionId = Number(state.sessionId);
        });
    }

    animateDots() {
        setInterval(() => {
            this.dots = this.dots < 3 ? (this.dots += 1) : (this.dots = 1);
        }, 500);
    }

    onSend(): void {
        if (this.newMessageTextContent) {
            const message = this.newUserMessage(this.newMessageTextContent);
            this.stateStore.dispatchAndThen(new StudentMessageSentAction(message)).then(() => {
                this.httpMessageService.createMessage(<number>this.sessionId, message).subscribe({
                    next: () => this.scrollToBottom(),
                    error: () => this.stateStore.dispatch(new ConversationErrorOccurredAction('Something went wrong. Please try again later!')),
                });
            });
            this.newMessageTextContent = '';
        }
        this.scrollToBottom();
    }

    scrollToBottom(behavior = 'smooth') {
        setTimeout(() => {
            const chatBodyElement: HTMLElement = this.chatBody.nativeElement;
            chatBodyElement.scrollTo({
                top: chatBodyElement.scrollHeight,
                behavior: behavior as ScrollBehavior,
            });
        });
    }

    closeChat() {
        this.dialog.closeAll();
    }

    private newUserMessage(message: string): IrisClientMessage {
        const content: IrisMessageContent = {
            type: IrisMessageContentType.TEXT,
            textContent: message,
        };
        return {
            sender: this.SENDER_USER,
            content: [content],
        };
    }
}
