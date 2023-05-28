import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { faCircle, faExpand, faPaperPlane, faXmark } from '@fortawesome/free-solid-svg-icons';
import { MatDialog } from '@angular/material/dialog';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { IrisClientMessage, IrisMessage, IrisMessageContent, IrisMessageContentType, IrisSender, IrisServerMessage } from 'app/entities/iris/iris.model';
import { IrisMessageStore } from 'app/iris/message-store.service';
import { ActiveConversationMessageLoadedAction, ActiveConversationMessageLoadedErrorAction, StudentMessageSentAction } from 'app/iris/message-store.model';

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

    messages: IrisMessage[] = [];
    newMessageTextContent = '';
    isLoading: boolean;
    error = ''; // TODO: error object
    dots = 1;

    constructor(private dialog: MatDialog, private readonly messageStore: IrisMessageStore) {}

    // Icons
    faPaperPlane = faPaperPlane;
    faCircle = faCircle;
    faExpand = faExpand;
    faXmark = faXmark;

    ngOnInit() {
        this.scrollToBottom('auto');
        this.animateDots();
        this.messageStore.getState().subscribe((state) => {
            this.messages = state.messages as IrisMessage[];
            this.isLoading = state.isLoading;
            this.error = state.error;
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
            this.messageStore.dispatch(
                new StudentMessageSentAction(message, {
                    next: (res: HttpResponse<IrisServerMessage>) => {
                        this.messageStore.dispatch(new ActiveConversationMessageLoadedAction(res.body!));
                        this.scrollToBottom();
                    },
                    error: (error: HttpErrorResponse) => {
                        // this.messageStore.dispatch(new ActiveConversationMessageLoadedErrorAction(res.body!));
                        this.messageStore.dispatch(new ActiveConversationMessageLoadedErrorAction('Something went wrong. Please try again later!'));
                    },
                }),
            );
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
