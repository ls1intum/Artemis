import { faArrowDown, faCircle, faCircleInfo, faCompress, faExpand, faPaperPlane, faRobot, faThumbsDown, faThumbsUp, faXmark } from '@fortawesome/free-solid-svg-icons';
import { ActivatedRoute, NavigationStart, Router } from '@angular/router';
import { LocalStorageService } from 'ngx-webstorage';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ButtonType } from 'app/shared/components/button.component';
import { AfterViewInit, Component, ElementRef, Inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { IrisStateStore } from 'app/iris/state-store.service';
import {
    ActiveConversationMessageLoadedAction,
    ConversationErrorOccurredAction,
    NumNewMessagesResetAction,
    RateMessageSuccessAction,
    StudentMessageSentAction,
} from 'app/iris/state-store.model';
import { IrisHttpMessageService } from 'app/iris/http-message.service';
import { IrisClientMessage, IrisMessage, IrisSender, IrisServerMessage, isServerSentMessage, isStudentSentMessage } from 'app/entities/iris/iris-message.model';
import { IrisMessageContent, IrisMessageContentType } from 'app/entities/iris/iris-content-type.model';
import { Subscription } from 'rxjs';
import { ResizeSensor } from 'css-element-queries';
import { Overlay } from '@angular/cdk/overlay';
import { SharedService } from 'app/iris/shared.service';
import dayjs from 'dayjs';

@Component({
    selector: 'jhi-exercise-chat-widget',
    templateUrl: './exercise-chat-widget.component.html',
    styleUrls: ['./exercise-chat-widget.component.scss'],
})
export class ExerciseChatWidgetComponent implements OnInit, OnDestroy, AfterViewInit {
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

    // ViewChilds
    @ViewChild('chatWidget') chatWidget!: ElementRef;
    @ViewChild('chatBody') chatBody!: ElementRef;
    @ViewChild('scrollArrow') scrollArrow!: ElementRef;
    @ViewChild('messageTextarea', { static: false }) messageTextarea: ElementRef<HTMLTextAreaElement>;
    @ViewChild('unreadMessage', { static: false }) unreadMessage!: ElementRef;

    // Constants
    readonly SENDER_USER = IrisSender.USER;
    readonly SENDER_SERVER = IrisSender.LLM;

    // State variables
    stateStore: IrisStateStore;
    stateSubscription: Subscription;
    messages: IrisMessage[] = [];
    newMessageTextContent = '';
    isLoading: boolean;
    sessionId: number;
    numNewMessages = 0;
    unreadMessageIndex: number;
    error = '';
    dots = 1;
    isInitializing = false;
    isFirstMessage = false;

    // User preferences
    userAccepted = false;
    isScrolledToBottom = true;
    rows = 1;
    initialWidth = 330;
    initialHeight = 430;
    fullWidth = '93vw';
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
        private sharedService: SharedService,
    ) {
        this.stateStore = data.stateStore;
        this.navigationSubscription = this.router.events.subscribe((event) => {
            if (event instanceof NavigationStart) {
                this.dialog.closeAll();
            }
        });
    }

    ngOnInit() {
        // Check if the user has accepted the chat widget permissions
        this.accountService.identity().then((user: User) => {
            if (typeof user!.login === 'string') {
                this.userAccepted = localStorage.getItem(user!.login) == 'true';
            }
        });

        // Animate dots
        this.animateDots();

        // Set initializing flag to true and load the first message
        setTimeout(() => {
            this.isInitializing = true;
        }, 50);
        this.loadFirstMessage();

        // Subscribe to state changes
        this.stateSubscription = this.stateStore.getState().subscribe((state) => {
            this.messages = state.messages as IrisMessage[];
            this.isLoading = state.isLoading;
            this.error = state.error;
            this.sessionId = Number(state.sessionId);
            this.numNewMessages = state.numNewMessages;
        });

        // Set initializing flag to false and focus on message textarea
        setTimeout(() => {
            this.isInitializing = false;
            this.messageTextarea.nativeElement.focus();
        }, 150);
    }

    ngAfterViewInit() {
        // Determine the unread message index and scroll to the unread message if applicable
        this.unreadMessageIndex = this.messages.length <= 1 || this.numNewMessages === 0 ? -1 : this.messages.length - this.numNewMessages;
        if (this.numNewMessages > 0) {
            this.scrollToUnread();
        } else {
            this.scrollToBottom('auto');
        }

        // Add a resize sensor to the chat widget to store the new width and height in local storage
        const chatWidget: HTMLElement = this.chatWidget.nativeElement;
        new ResizeSensor(chatWidget, () => {
            localStorage.setItem('widgetWidth', chatWidget.style.width);
            localStorage.setItem('widgetHeight', chatWidget.style.height);
        });
    }
    ngOnDestroy() {
        this.stateSubscription.unsubscribe();
    }

    /**
     * Animates the dots in the chat widget.
     */
    animateDots() {
        setInterval(() => {
            this.dots = this.dots < 3 ? (this.dots += 1) : (this.dots = 1);
        }, 500);
    }

    /**
     * Loads the first message in the conversation if it's not already loaded.
     */
    loadFirstMessage(): void {
        const firstMessageContent = {
            textContent: 'artemisApp.exerciseChatbot.firstMessage',
            type: IrisMessageContentType.TEXT,
        } as IrisMessageContent;

        const firstMessage = {
            sender: IrisSender.LLM,
            id: 0,
            content: [firstMessageContent],
            sentAt: dayjs(),
        } as IrisServerMessage;

        if (this.messages.length === 0) {
            this.isFirstMessage = true;
            this.stateStore.dispatch(new ActiveConversationMessageLoadedAction(firstMessage));
        } else if (this.messages[0].id === firstMessage.id) {
            this.isFirstMessage = true;
        }
    }

    /**
     * Scrolls the chat body to the bottom.
     * @param behavior - The scroll behavior.
     */
    scrollToBottom(behavior: ScrollBehavior) {
        setTimeout(() => {
            const chatBodyElement: HTMLElement = this.chatBody.nativeElement;
            chatBodyElement.scrollTo({
                top: chatBodyElement.scrollHeight,
                behavior: behavior,
            });
        });
    }

    /**
     * Handles the send button click event and sends the user's message.
     */
    onSend(): void {
        if (this.newMessageTextContent.trim()) {
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

    /**
     * Scrolls to the unread message.
     */
    scrollToUnread() {
        setTimeout(() => {
            const unreadMessageElement: HTMLElement = this.unreadMessage.nativeElement;
            unreadMessageElement.scrollIntoView({ behavior: 'auto' });
        });
    }

    /**
     * Closes the chat widget.
     */
    closeChat() {
        this.stateStore.dispatch(new NumNewMessagesResetAction());
        this.sharedService.changeChatOpenStatus(false);
        this.dialog.closeAll();
    }

    /**
     * Accepts the permission to use the chat widget.
     */
    acceptPermission() {
        this.accountService.identity().then((user: User) => {
            if (typeof user!.login === 'string') {
                localStorage.setItem(user!.login, 'true');
            }
        });

        this.userAccepted = true;
    }

    /**
     * Checks if the chat body is scrolled to the bottom.
     */
    checkChatScroll() {
        const chatBody = this.chatBody.nativeElement;
        const scrollHeight = chatBody.scrollHeight;
        const scrollTop = chatBody.scrollTop;
        const clientHeight = chatBody.clientHeight;
        this.isScrolledToBottom = scrollHeight - scrollTop === clientHeight;
    }

    /**
     * Resets the screen by closing and reopening the chat widget.
     */
    resetScreen() {
        setTimeout(() => {
            this.dialog.closeAll();
        }, 50);
        setTimeout(() => {
            this.dialog.open(ExerciseChatWidgetComponent, {
                hasBackdrop: false,
                scrollStrategy: this.overlay.scrollStrategies.noop(),
                position: { bottom: '0px', right: '0px' },
                disableClose: true,
                data: {
                    stateStore: this.stateStore,
                    widgetWidth: localStorage.getItem('widgetWidth') || `${this.initialWidth}px`,
                    widgetHeight: localStorage.getItem('widgetHeight') || `${this.initialHeight}px`,
                    fullSize: localStorage.getItem('fullSize') === 'true',
                },
            });
        }, 140);
    }

    /**
     * Maximizes the chat widget screen.
     */
    maximizeScreen() {
        this.resetScreen();
        localStorage.setItem('widgetWidth', this.fullWidth);
        localStorage.setItem('widgetHeight', this.fullHeight);
        localStorage.setItem('fullSize', 'true');
    }

    /**
     * Minimizes the chat widget screen.
     */
    minimizeScreen() {
        this.resetScreen();
        localStorage.setItem('widgetWidth', `${this.initialWidth}px`);
        localStorage.setItem('widgetHeight', `${this.initialHeight}px`);
        localStorage.setItem('fullSize', 'false');
    }

    /**
     * Handles the key events in the message textarea.
     * @param event - The keyboard event.
     */
    handleKey(event: KeyboardEvent): void {
        if (event.key === 'Enter') {
            if (!this.isLoading) {
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
            } else {
                event.preventDefault();
            }
        }
    }

    /**
     * Handles the input event in the message textarea.
     */
    onInput() {
        this.adjustTextareaRows();
    }

    /**
     * Handles the paste event in the message textarea.
     */
    onPaste() {
        setTimeout(() => {
            this.adjustTextareaRows();
        }, 0);
    }

    /**
     * Adjusts the height of the message textarea based on its content.
     */
    adjustTextareaRows() {
        const textarea: HTMLTextAreaElement = this.messageTextarea.nativeElement;
        textarea.style.height = 'auto'; // Reset the height to auto
        const lineHeight = parseInt(getComputedStyle(textarea).lineHeight, 10);
        const maxRows = 3;
        const maxHeight = lineHeight * maxRows;

        textarea.style.height = `${Math.min(textarea.scrollHeight, maxHeight)}px`;

        this.adjustChatBodyHeight(Math.min(textarea.scrollHeight, maxHeight) / lineHeight);
    }

    /**
     * Handles the row change event in the message textarea.
     */
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

    /**
     * Adjusts the height of the chat body based on the number of rows in the message textarea.
     * @param newRows - The new number of rows.
     */
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

    /**
     * Resets the height of the chat body.
     */
    resetChatBodyHeight() {
        const chatBody: HTMLElement = this.chatBody.nativeElement;
        const textarea: HTMLTextAreaElement = this.messageTextarea.nativeElement;
        const scrollArrow: HTMLElement = this.scrollArrow.nativeElement;
        textarea.rows = 1;
        textarea.style.height = '';
        scrollArrow.style.bottom = '';
        chatBody.style.height = '';
        this.stateStore.dispatch(new NumNewMessagesResetAction());
    }

    /**
     * Rates a message as helpful or unhelpful.
     * @param message_id - The ID of the message to rate.
     * @param index - The index of the message in the messages array.
     * @param helpful - A boolean indicating if the message is helpful or not.
     */
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

    /**
     * Checks if a message is a student-sent message.
     * @param message - The message to check.
     * @returns A boolean indicating if the message is a student-sent message.
     */
    isStudentSentMessage(message: IrisMessage): message is IrisClientMessage {
        return isStudentSentMessage(message);
    }

    /**
     * Checks if a message is a server-sent message.
     * @param message - The message to check.
     * @returns A boolean indicating if the message is a server-sent message.
     */
    isServerSentMessage(message: IrisMessage): message is IrisServerMessage {
        return isServerSentMessage(message);
    }

    /**
     * Creates a new user message.
     * @param message - The content of the message.
     * @returns A new IrisClientMessage object representing the user message.
     */
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
