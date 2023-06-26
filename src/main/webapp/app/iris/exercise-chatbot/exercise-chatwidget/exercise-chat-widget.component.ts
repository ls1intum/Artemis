import { ActivatedRoute } from '@angular/router';
import { LocalStorageService } from 'ngx-webstorage';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ButtonType } from 'app/shared/components/button.component';
import { AfterViewInit, Component, ElementRef, Inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { faArrowDown, faCircle, faCircleInfo, faCompress, faExpand, faPaperPlane, faRedo, faRobot, faThumbsDown, faThumbsUp, faXmark } from '@fortawesome/free-solid-svg-icons';
import { MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { IrisStateStore } from 'app/iris/state-store.service';
import { ConversationErrorOccurredAction, NumNewMessagesResetAction, RateMessageSuccessAction, StudentMessageSentAction } from 'app/iris/state-store.model';
import { IrisHttpMessageService } from 'app/iris/http-message.service';
import { IrisClientMessage, IrisMessage, IrisSender, IrisServerMessage } from 'app/entities/iris/iris-message.model';
import { IrisMessageContent, IrisMessageContentType } from 'app/entities/iris/iris-content-type.model';
import { Subscription } from 'rxjs';
import { IrisErrorMessageKey, IrisErrorType } from 'app/entities/iris/iris-errors.model';
import { ResizeSensor } from 'css-element-queries';
import { Overlay } from '@angular/cdk/overlay';

@Component({
    selector: 'jhi-exercise-chat-widget',
    templateUrl: './exercise-chat-widget.component.html',
    styleUrls: ['./exercise-chat-widget.component.scss'],
})
export class ExerciseChatWidgetComponent implements OnInit, OnDestroy, AfterViewInit {
    @ViewChild('chatWidget') chatWidget!: ElementRef;
    @ViewChild('chatBody') chatBody!: ElementRef;
    @ViewChild('scrollArrow') scrollArrow!: ElementRef;
    @ViewChild('messageTextarea', { static: false }) messageTextarea: ElementRef;
    @ViewChild('unreadMessage', { static: false }) unreadMessage!: ElementRef;

    readonly SENDER_USER = IrisSender.USER;
    readonly SENDER_SERVER = IrisSender.LLM;
    readonly stateStore: IrisStateStore;

    stateSubscription: Subscription;
    messages: IrisMessage[] = [];
    newMessageTextContent = '';
    isLoading: boolean;
    sessionId: number;
    error: IrisErrorType | null;
    numNewMessages = 0;
    unreadMessageIndex: number;
    dots = 1;
    resendAnimationActive = false;
    shakeErrorField = false;

    userAccepted = false;
    isScrolledToBottom = true;
    rows = 1;
    showChatWidget = true;
    initialWidth = 330;
    initialHeight = 430;
    fullWidth = '95vw';
    fullHeight = '85vh';
    fullSize = localStorage.getItem('fullSize') === 'true';
    widgetWidth = localStorage.getItem('widgetWidth') || `${this.initialWidth}px`;
    widgetHeight = localStorage.getItem('widgetHeight') || `${this.initialHeight}px`;
    public ButtonType = ButtonType;

    constructor(
        private dialog: MatDialog,
        private route: ActivatedRoute,
        private localStorage: LocalStorageService,
        private accountService: AccountService,
        @Inject(MAT_DIALOG_DATA) public data: any,
        private httpMessageService: IrisHttpMessageService,
        private overlay: Overlay,
    ) {
        this.stateStore = data.stateStore;
    }

    // Icons
    faCircle = faCircle;
    faPaperPlane = faPaperPlane;
    faExpand = faExpand;
    faXmark = faXmark;
    faThumbsUp = faThumbsUp;
    faThumbsDown = faThumbsDown;
    faRedo = faRedo;
    faArrowDown = faArrowDown;
    faRobot = faRobot;
    faCircleInfo = faCircleInfo;
    faCompress = faCompress;

    ngOnInit() {
        this.accountService.identity().then((user: User) => {
            if (typeof user!.login === 'string') {
                this.userAccepted = localStorage.getItem(user!.login) == 'true';
            }
        });
        this.animateDots();
        this.stateSubscription = this.stateStore.getState().subscribe((state) => {
            this.messages = state.messages as IrisMessage[];
            this.isLoading = state.isLoading;
            this.error = state.error;
            this.sessionId = Number(state.sessionId);
            this.numNewMessages = state.numNewMessages;
        });
    }

    ngAfterViewInit() {
        this.unreadMessageIndex = this.messages.length === 0 || this.numNewMessages === 0 ? -1 : this.messages.length - this.numNewMessages;
        if (this.numNewMessages > 0) {
            this.scrollToUnread();
        } else {
            this.scrollToBottom('auto');
        }

        const chatWidget: HTMLElement = this.chatWidget.nativeElement; // element you want to watch
        new ResizeSensor(chatWidget, () => {
            localStorage.setItem('widgetWidth', chatWidget.style.width);
            localStorage.setItem('widgetHeight', chatWidget.style.height);
        });
    }

    ngOnDestroy() {
        this.stateSubscription.unsubscribe();
    }

    animateDots() {
        setInterval(() => {
            this.dots = this.dots < 3 ? (this.dots += 1) : (this.dots = 1);
        }, 500);
    }

    onSend(): void {
        if (this.newMessageTextContent) {
            const message = this.newUserMessage(this.newMessageTextContent);
            this.stateStore
                .dispatchAndThen(new StudentMessageSentAction(message))
                .then(() => this.httpMessageService.createMessage(<number>this.sessionId, message).toPromise())
                .then(() => this.scrollToBottom('smooth'))
                .catch(() => {
                    this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.SEND_MESSAGE_FAILED));
                });
            // TODO show that iris has been disabled after the corresponding PR is merged
            this.newMessageTextContent = '';
        }
        this.scrollToBottom('smooth');
        this.resetChatBodyHeight();
    }

    scrollToBottom(behavior: ScrollBehavior) {
        setTimeout(() => {
            const chatBodyElement: HTMLElement = this.chatBody.nativeElement;
            chatBodyElement.scrollTo({
                top: chatBodyElement.scrollHeight,
                behavior: behavior,
            });
        });
    }

    scrollToUnread() {
        setTimeout(() => {
            const unreadMessageElement: HTMLElement = this.unreadMessage.nativeElement;
            unreadMessageElement.scrollIntoView({ behavior: 'auto' });
        });
    }

    closeChat() {
        this.stateStore.dispatch(new NumNewMessagesResetAction());
        this.dialog.closeAll();
    }

    acceptPermission() {
        this.accountService.identity().then((user: User) => {
            if (typeof user!.login === 'string') {
                localStorage.setItem(user!.login, 'true');
            }
        });

        this.userAccepted = true;
    }

    checkChatScroll() {
        const chatBody = this.chatBody.nativeElement;
        const scrollHeight = chatBody.scrollHeight;
        const scrollTop = chatBody.scrollTop;
        const clientHeight = chatBody.clientHeight;
        this.isScrolledToBottom = scrollHeight - scrollTop === clientHeight;
    }

    resetScreen() {
        setTimeout(() => {
            this.dialog.closeAll();
        }, 50);
        setTimeout(() => {
            this.dialog.open(ExerciseChatWidgetComponent, {
                hasBackdrop: false,
                scrollStrategy: this.overlay.scrollStrategies.noop(),
                position: { bottom: '0px', right: '0px' },
                data: {
                    stateStore: this.stateStore,
                    widgetWidth: localStorage.getItem('widgetWidth') || `${this.initialWidth}px`,
                    widgetHeight: localStorage.getItem('widgetHeight') || `${this.initialHeight}px`,
                    fullSize: localStorage.getItem('fullSize') === 'true',
                },
            });
        }, 50);
    }

    maximizeScreen() {
        this.resetScreen();
        localStorage.setItem('widgetWidth', this.fullWidth);
        localStorage.setItem('widgetHeight', this.fullHeight);
        localStorage.setItem('fullSize', 'true');
    }

    minimizeScreen() {
        this.resetScreen();
        localStorage.setItem('widgetWidth', `${this.initialWidth}px`);
        localStorage.setItem('widgetHeight', `${this.initialHeight}px`);
        localStorage.setItem('fullSize', 'false');
    }

    handleKey(event: KeyboardEvent): void {
        if (event.key === 'Enter') {
            if (!event.shiftKey) {
                event.preventDefault();
                this.onSend();
            } else {
                const textArea = event.target as HTMLTextAreaElement;
                const { selectionStart, selectionEnd } = textArea;
                const value = textArea.value;
                textArea.value = value.slice(0, selectionStart) + value.slice(selectionEnd);
                textArea.selectionStart = textArea.selectionEnd = selectionStart + 1;
            }
        }
    }

    onInput() {
        this.adjustTextareaRows();
    }

    onPaste(event: ClipboardEvent) {
        setTimeout(() => {
            this.adjustTextareaRows();
        }, 0);
    }

    adjustTextareaRows() {
        const textarea: HTMLTextAreaElement = this.messageTextarea.nativeElement;
        textarea.style.height = 'auto'; // Reset the height to auto
        const lineHeight = parseInt(getComputedStyle(textarea).lineHeight, 10);
        const maxRows = 3;
        const maxHeight = lineHeight * maxRows;

        textarea.style.height = `${Math.min(textarea.scrollHeight, maxHeight)}px`;

        if (Math.min(textarea.scrollHeight, maxHeight) / lineHeight > 2) this.adjustChatBodyHeight(Math.min(textarea.scrollHeight, maxHeight) / lineHeight);
        this.onRowChange();
    }

    onRowChange() {
        const textarea: HTMLTextAreaElement = this.messageTextarea.nativeElement;
        const newRows = textarea.value.split('\n').length;
        if (newRows != this.rows) {
            if (newRows <= 3) {
                textarea.rows = newRows;
                this.adjustChatBodyHeight(newRows);
                this.rows = newRows;
            }
        }
    }

    adjustChatBodyHeight(newRows: number) {
        const textarea: HTMLTextAreaElement = this.messageTextarea.nativeElement;
        const chatBody: HTMLElement = this.chatBody.nativeElement;
        const scrollArrow: HTMLElement = this.scrollArrow.nativeElement;
        const lineHeight = parseInt(window.getComputedStyle(textarea).lineHeight);
        const rowHeight = lineHeight * newRows;
        scrollArrow.style.bottom = `calc(11% + ${rowHeight}px)`;
        chatBody.style.height = `calc(100% - ${rowHeight}px - 77px)`;
    }

    resetChatBodyHeight() {
        const chatBody: HTMLElement = this.chatBody.nativeElement;
        const textarea: HTMLTextAreaElement = this.messageTextarea.nativeElement;
        const scrollArrow: HTMLElement = this.scrollArrow.nativeElement;
        textarea.rows = 1;
        scrollArrow.style.bottom = '60 px';
        chatBody.style.height = `calc(100% - 100px)`;
        this.stateStore.dispatch(new NumNewMessagesResetAction());
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

    rateMessage(message_id: number, index: number, helpful: boolean) {
        this.httpMessageService
            .rateMessage(<number>this.sessionId, message_id, helpful)
            .toPromise()
            .then(() => this.stateStore.dispatch(new RateMessageSuccessAction(index, helpful)))
            .catch(() => this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.RATE_MESSAGE_FAILED)));
    }

    resendMessage(message: IrisClientMessage) {
        this.resendAnimationActive = true;

        this.triggerShake();

        this.httpMessageService
            .createMessage(<number>this.sessionId, message)
            .toPromise()
            .then(() => this.stateStore.dispatch(new ConversationErrorOccurredAction(null)))
            .catch(() => {
                this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.SEND_MESSAGE_FAILED));
                this.triggerShake();
            })
            .finally(() => {
                this.resendAnimationActive = false;
            });
    }

    isSendMessageFailedError(): boolean {
        return this.error?.key == IrisErrorMessageKey.SEND_MESSAGE_FAILED;
    }

    triggerShake() {
        this.shakeErrorField = true;
        setTimeout(() => {
            this.shakeErrorField = false;
        }, 1000);
    }

    isArtemisSentMessage(message: IrisMessage): message is IrisServerMessage {
        return message.sender === IrisSender.LLM;
    }

    isStudentSentMessage(message: IrisMessage): message is IrisClientMessage {
        return message.sender === IrisSender.USER;
    }
}
