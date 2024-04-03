import { faArrowDown, faCircle, faPaperPlane, faRedo, faRobot, faThumbsDown, faThumbsUp, faTrash, faXmark } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ButtonType } from 'app/shared/components/button.component';
import { AfterViewInit, Component, ElementRef, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ConversationErrorOccurredAction, NumNewMessagesResetAction, RateMessageSuccessAction, StudentMessageSentAction } from 'app/iris/state-store.model';
import { IrisMessage, IrisSender, IrisUserMessage, isArtemisClientSentMessage, isServerSentMessage, isStudentSentMessage } from 'app/entities/iris/iris-message.model';
import { IrisMessageContent, IrisTextMessageContent, getTextContent, isHidden, isTextContent } from 'app/entities/iris/iris-content-type.model';
import { Subscription } from 'rxjs';
import { IrisSessionService } from 'app/iris/session.service';
import { IrisErrorMessageKey, IrisErrorType } from 'app/entities/iris/iris-errors.model';
import { AnimationEvent, animate, state, style, transition, trigger } from '@angular/animations';
import { UserService } from 'app/core/user/user.service';
import { IrisLogoSize } from 'app/iris/iris-logo/iris-logo.component';
import { IrisChatSessionService } from 'app/iris/chat-session.service';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-iris-base-chatbot-component',
    templateUrl: './iris-base-chatbot-component.component.html',
    styleUrl: './iris-base-chatbot-component.component.scss',
    animations: [
        trigger('fadeAnimation', [
            state(
                'start',
                style({
                    opacity: 1,
                }),
            ),
            state(
                'end',
                style({
                    opacity: 0,
                }),
            ),
            transition('start => end', [animate('2s ease')]),
        ]),
    ],
})
export class IrisBaseChatbotComponentComponent implements OnInit, AfterViewInit, OnDestroy {
    // Icons
    faTrash = faTrash;
    faCircle = faCircle;
    faPaperPlane = faPaperPlane;
    faXmark = faXmark;
    faArrowDown = faArrowDown;
    faRobot = faRobot;
    faThumbsUp = faThumbsUp;
    faThumbsDown = faThumbsDown;
    faRedo = faRedo;

    // ViewChilds
    @ViewChild('chatBody') chatBody!: ElementRef;
    @ViewChild('scrollArrow') scrollArrow!: ElementRef;
    @ViewChild('messageTextarea') messageTextarea: ElementRef<HTMLTextAreaElement>;
    @ViewChild('unreadMessage') unreadMessage!: ElementRef;

    // State variables
    stateStore: IrisStateStore;
    stateSubscription: Subscription;
    messages: IrisMessage[] = [];
    content: IrisMessageContent;
    newMessageTextContent = '';
    isLoading: boolean;
    sessionId: number;
    numNewMessages = 0;
    unreadMessageIndex: number;
    error: IrisErrorType | null;
    dots = 1;
    resendAnimationActive = false;
    fadeState = '';
    courseId: number;
    exerciseId: number;
    sessionService: IrisSessionService;
    shouldShowEmptyMessageError = false;
    currentMessageCount: number;
    rateLimit: number;
    rateLimitTimeframeHours: number;
    importExerciseUrl: string;

    // User preferences
    userAccepted: boolean;
    isScrolledToBottom = true;
    rows = 1;

    public ButtonType = ButtonType;
    readonly IrisLogoSize = IrisLogoSize;
    private readonly MAX_INT_JAVA = 2147483647;

    @Input({ required: true }) data: any;
    constructor(
        private userService: UserService,
        private modalService: NgbModal,
        private translateService: TranslateService,
    ) {}

    ngOnInit() {
        this.stateStore = this.data.stateStore;
        this.courseId = this.data.courseId;
        this.exerciseId = this.data.exerciseId;
        this.sessionService = this.data.sessionService;
        console.log(this.data);

        this.animateDots();

        // Subscribe to state changes
        this.stateSubscription = this.stateStore.getState().subscribe((state) => {
            this.messages = state.messages as IrisMessage[];
            this.isLoading = state.isLoading;
            this.error = state.error;
            this.sessionId = Number(state.sessionId);
            this.numNewMessages = state.numNewMessages;
            if (state.error?.key == IrisErrorMessageKey.EMPTY_MESSAGE) {
                this.shouldShowEmptyMessageError = true;
                this.fadeState = 'start';
            }
            if (this.error) {
                this.getConvertedErrorMap();
            }
            this.currentMessageCount = state.currentMessageCount;
            this.rateLimit = state.rateLimit;
            this.rateLimitTimeframeHours = state.rateLimitTimeframeHours;
        });

        // Focus on message textarea
        setTimeout(() => {
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
    }

    ngOnDestroy() {
        this.stateSubscription.unsubscribe();
    }

    /**
     * Inserts the correct link to import the current programming exercise for a new variant generation.
     */
    getFirstMessageContent(): string {
        if (this.isChatSession()) {
            return this.translateService.instant('artemisApp.exerciseChatbot.tutorFirstMessage');
        }
        this.importExerciseUrl = `/course-management/${this.courseId}/programming-exercises/import/${this.exerciseId}`;
        return this.translateService
            .instant('artemisApp.exerciseChatbot.codeEditorFirstMessage')
            .replace(/{link:(.*)}/, '<a href="' + this.importExerciseUrl + '" target="_blank">$1</a>');
    }

    /**
     * Handles the send button click event and sends the user's message.
     */
    onSend(): void {
        console.log(this.error);
        if (this.error?.fatal) {
            return;
        }
        console.log('content', this.newMessageTextContent);
        if (this.newMessageTextContent.trim() === '') {
            this.stateStore.dispatchAndThen(new ConversationErrorOccurredAction(IrisErrorMessageKey.EMPTY_MESSAGE)).catch(() => this.scrollToBottom('smooth'));
            return;
        }
        if (this.newMessageTextContent) {
            const message = this.newUserMessage(this.newMessageTextContent);
            const timeoutId = setTimeout(() => {
                // will be cleared by the store automatically
                this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.IRIS_SERVER_RESPONSE_TIMEOUT));
                this.scrollToBottom('smooth');
            }, 60000);
            this.stateStore
                .dispatchAndThen(new StudentMessageSentAction(message, timeoutId))
                .then(() => this.sessionService.sendMessage(this.sessionId, message))
                .then(() => this.scrollToBottom('smooth'))
                .catch((error) => this.handleIrisError(error));
            this.newMessageTextContent = '';
        }
        this.scrollToBottom('smooth');
        this.resetChatBodyHeight();
    }

    resendMessage(message: IrisUserMessage) {
        this.resendAnimationActive = true;
        message.messageDifferentiator = message.id ?? Math.floor(Math.random() * this.MAX_INT_JAVA);

        const timeoutId = setTimeout(() => {
            // will be cleared by the store automatically
            this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.IRIS_SERVER_RESPONSE_TIMEOUT));
            this.scrollToBottom('smooth');
        }, 60000);
        this.stateStore
            .dispatchAndThen(new StudentMessageSentAction(message, timeoutId))
            .then(() => {
                if (message.id) {
                    return this.sessionService.resendMessage(this.sessionId, message);
                } else {
                    return this.sessionService.sendMessage(this.sessionId, message);
                }
            })
            .then(() => {
                this.scrollToBottom('smooth');
            })
            .catch((error) => this.handleIrisError(error))
            .finally(() => {
                this.resendAnimationActive = false;
                this.scrollToBottom('smooth');
            });
    }

    private handleIrisError(error: HttpErrorResponse) {
        if (error.status === 403) {
            this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.IRIS_DISABLED));
        } else if (error.status === 429) {
            const map = new Map<string, any>();
            map.set('hours', this.rateLimitTimeframeHours);
            this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.RATE_LIMIT_EXCEEDED, map));
        } else {
            this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.SEND_MESSAGE_FAILED));
        }
    }

    /**
     * Rates a message as helpful or unhelpful.
     * @param messageId - The ID of the message to rate.
     * @param index - The index of the message in the messages array.
     * @param helpful - A boolean indicating if the message is helpful or not.
     */
    rateMessage(messageId: number, index: number, helpful: boolean) {
        this.sessionService
            .rateMessage(this.sessionId, messageId, helpful)
            .then(() => this.stateStore.dispatch(new RateMessageSuccessAction(index, helpful)))
            .catch(() => {
                this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.RATE_MESSAGE_FAILED));
                this.scrollToBottom('smooth');
            });
    }

    /**
     * Animates the dots while loading each Iris message in the chat widget.
     */
    animateDots() {
        setInterval(() => {
            this.dots = this.dots < 3 ? (this.dots += 1) : (this.dots = 1);
        }, 500);
    }

    /**
     * Scrolls to the unread message.
     */
    scrollToUnread() {
        setTimeout(() => {
            const unreadMessageElement: HTMLElement = this.unreadMessage?.nativeElement;
            if (unreadMessageElement) {
                unreadMessageElement.scrollIntoView({ behavior: 'auto' });
            }
        });
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
     * Checks if the chat body is scrolled to the bottom.
     */
    checkChatScroll() {
        const chatBody = this.chatBody.nativeElement;
        const scrollHeight = chatBody.scrollHeight;
        const scrollTop = chatBody.scrollTop;
        const clientHeight = chatBody.clientHeight;
        this.isScrolledToBottom = scrollHeight - scrollTop - clientHeight < 50;
    }

    /**
     * Clear session and start a new conversation.
     */
    onClearSession(content: any) {
        this.modalService.open(content).result.then((result: string) => {
            if (result === 'confirm') {
                this.isLoading = false;
                this.createNewSession();
            }
        });
    }

    /**
     * Accepts the permission to use the chat widget.
     */
    acceptPermission() {
        this.userService.acceptIris().subscribe(() => {
            this.userAccepted = true;
        });
    }

    /**
     * Handles the key events in the message textarea.
     * @param event - The keyboard event.
     */
    handleKey(event: KeyboardEvent): void {
        if (event.key === 'Enter') {
            if (!this.deactivateSubmitButton()) {
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
     * Creates a new user message.
     * @param message - The content of the message.
     * @returns A new IrisClientMessage object representing the user message.
     */
    newUserMessage(message: string): IrisUserMessage {
        const content = new IrisTextMessageContent(message);
        return {
            sender: IrisSender.USER,
            content: [content],
        };
    }

    deactivateSubmitButton(): boolean {
        return this.isLoading || (!!this.error && this.error.fatal);
    }

    isEmptyMessageError(): boolean {
        return !!this.error && this.error.key == IrisErrorMessageKey.EMPTY_MESSAGE;
    }

    onFadeAnimationPhaseEnd(event: AnimationEvent) {
        if (event.fromState === 'void' && event.toState === 'start') {
            this.fadeState = 'end';
        }
        if (event.fromState === 'start' && event.toState === 'end') {
            this.shouldShowEmptyMessageError = false;
        }
    }

    getConvertedErrorMap() {
        if (this.error?.paramsMap) {
            // Check if paramsMap is iterable.
            if (typeof this.error?.paramsMap[Symbol.iterator] === 'function') {
                return Object.fromEntries(this.error.paramsMap);
            }
            return this.error.paramsMap;
        }
        return null;
    }
    createNewSession() {
        this.sessionService.createNewSession(this.exerciseId);
    }
    isChatSession() {
        return this.sessionService instanceof IrisChatSessionService;
    }
    protected readonly IrisSender = IrisSender;
    protected readonly getTextContent = getTextContent;
    protected readonly isTextContent = isTextContent;
    protected readonly isHidden = isHidden;
    protected readonly isStudentSentMessage = isStudentSentMessage;
    protected readonly isServerSentMessage = isServerSentMessage;
    protected readonly isArtemisClientSentMessage = isArtemisClientSentMessage;
}
