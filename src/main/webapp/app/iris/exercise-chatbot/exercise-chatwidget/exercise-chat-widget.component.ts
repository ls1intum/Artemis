import { faArrowDown, faCircle, faCircleInfo, faCompress, faExpand, faPaperPlane, faRobot, faThumbsDown, faThumbsUp, faXmark } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, NavigationStart, Router } from '@angular/router';
import { LocalStorageService } from 'ngx-webstorage';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ButtonType } from 'app/shared/components/button.component';
import { AfterViewInit, Component, ElementRef, Inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { IrisStateStore } from 'app/iris/state-store.service';
import { ConversationErrorOccurredAction, NumNewMessagesResetAction, RateMessageSuccessAction, StudentMessageSentAction } from 'app/iris/state-store.model';
import { IrisHttpMessageService } from 'app/iris/http-message.service';
import { IrisClientMessage, IrisMessage, IrisSender, IrisServerMessage, isServerSentMessage, isStudentSentMessage } from 'app/entities/iris/iris-message.model';
import { IrisMessageContent, IrisMessageContentType } from 'app/entities/iris/iris-content-type.model';
import { Subscription } from 'rxjs';
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
    numNewMessages = 0;
    unreadMessageIndex: number;
    error = '';
    dots = 1;

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
    private navigationSubscription: Subscription;

    constructor(
        private dialog: MatDialog,
        private route: ActivatedRoute,
        private localStorage: LocalStorageService,
        private accountService: AccountService,
        @Inject(MAT_DIALOG_DATA) public data: any,
        private httpMessageService: IrisHttpMessageService,
        private overlay: Overlay,
        private router: Router,
    ) {
        this.stateStore = data.stateStore;
        this.navigationSubscription = this.router.events.subscribe((event) => {
            if (event instanceof NavigationStart) {
                this.dialog.closeAll();
            }
        });
    }

    // Icons
    faCircle = faCircle;
    faPaperPlane = faPaperPlane;
    faExpand = faExpand;
    faXmark = faXmark;
    faArrowDown = faArrowDown;
    faRobot = faRobot;
    faCircleInfo = faCircleInfo;
    faCompress = faCompress;
    faThumbsUp = faThumbsUp;
    faThumbsDown = faThumbsDown;

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

    scrollToBottom(behavior: ScrollBehavior) {
        setTimeout(() => {
            const chatBodyElement: HTMLElement = this.chatBody.nativeElement;
            chatBodyElement.scrollTo({
                top: chatBodyElement.scrollHeight,
                behavior: behavior,
            });
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
                .catch(() => this.stateStore.dispatch(new ConversationErrorOccurredAction('Something went wrong. Please try again later!')));
            this.newMessageTextContent = '';
        }
        this.scrollToBottom('smooth');
        this.resetChatBodyHeight();
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

    onPaste() {
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

        this.adjustChatBodyHeight(Math.min(textarea.scrollHeight, maxHeight) / lineHeight);
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
        setTimeout(() => {
            scrollArrow.style.bottom = `calc(11% + ${rowHeight}px)`;
        }, 10);
        setTimeout(() => {
            chatBody.style.height = `calc(100% - ${rowHeight}px - 64px)`;
        }, 10);
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

    rateMessage(message_id: number, index: number, helpful: boolean) {
        this.httpMessageService
            .rateMessage(<number>this.sessionId, message_id, helpful)
            .toPromise()
            .then(() => this.stateStore.dispatch(new RateMessageSuccessAction(index, helpful)))
            .catch(() => {
                this.stateStore.dispatch(new ConversationErrorOccurredAction('Something went wrong. Please try again later!'));
                this.scrollToBottom('smooth');
            });
    }

    isStudentSentMessage(message: IrisMessage): message is IrisClientMessage {
        return isStudentSentMessage(message);
    }

    isServerSentMessage(message: IrisMessage): message is IrisServerMessage {
        return isServerSentMessage(message);
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
