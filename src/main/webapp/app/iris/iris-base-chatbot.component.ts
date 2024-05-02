import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ActiveConversationMessageLoadedAction, ConversationErrorOccurredAction, NumNewMessagesResetAction, RateMessageSuccessAction } from 'app/iris/state-store.model';
import { IrisArtemisClientMessage, IrisMessage, IrisSender, IrisUserMessage } from 'app/entities/iris/iris-message.model';
import { IrisMessageContent, IrisMessageContentType, IrisTextMessageContent, getTextContent } from 'app/entities/iris/iris-content-type.model';
import { Subscription } from 'rxjs';
import { IrisSessionService } from 'app/iris/session.service';
import { IrisErrorMessageKey, IrisErrorType } from 'app/entities/iris/iris-errors.model';
import { UserService } from 'app/core/user/user.service';
import dayjs from 'dayjs';

@Component({
    template: '',
})
export abstract class IrisBaseChatbotComponent implements OnInit, OnDestroy, AfterViewInit {
    /*
     * Base component for Iris chatbot components that have shared functionality.
     */

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
    sessionService: IrisSessionService;
    shouldShowEmptyMessageError = false;
    currentMessageCount: number;
    rateLimit: number;
    rateLimitTimeframeHours: number;
    fadeState = '';

    // User preferences
    userAccepted: boolean;
    isScrolledToBottom = true;
    rows = 1;
    resendAnimationActive: boolean;

    protected navigationSubscription: Subscription;

    // ViewChilds
    @ViewChild('chatBody') chatBody!: ElementRef;
    @ViewChild('scrollArrow') scrollArrow!: ElementRef;
    @ViewChild('messageTextarea') messageTextarea: ElementRef<HTMLTextAreaElement>;
    @ViewChild('unreadMessage') unreadMessage!: ElementRef;

    protected constructor(
        protected userService: UserService,
        protected modalService: NgbModal,
    ) {}

    ngOnInit() {
        this.checkIfUserAcceptedIris();

        // Subscribe to state changes
        this.subscribeToStateChanges();
    }

    protected subscribeToStateChanges() {
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
        this.navigationSubscription.unsubscribe();
    }

    checkIfUserAcceptedIris(): void {
        this.userService.getIrisAcceptedAt().subscribe((res) => {
            this.userAccepted = !!res;
            if (this.userAccepted) {
                this.loadFirstMessage();
            }
        });
    }

    /**
     * Gets the content of the first message in the conversation.
     */
    abstract getFirstMessageContent(): string;

    /**
     * Handles the send button click event and sends the user's message.
     */
    abstract onSend(): void;

    abstract resendMessage(message: IrisUserMessage): void;

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

    protected handleIrisError(error: HttpErrorResponse) {
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
     * Loads the first message in the conversation if it's not already loaded.
     */
    loadFirstMessage(): void {
        const firstMessageContent = {
            type: IrisMessageContentType.TEXT,
            textContent: this.getFirstMessageContent(),
        } as IrisTextMessageContent;

        const firstMessage = {
            sender: IrisSender.ARTEMIS_CLIENT,
            content: [firstMessageContent],
            sentAt: dayjs(),
        } as IrisArtemisClientMessage;

        if (this.messages.length === 0) {
            this.stateStore.dispatch(new ActiveConversationMessageLoadedAction(firstMessage));
        }
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
        this.loadFirstMessage();
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
    abstract createNewSession(): void;
    abstract isChatSession(): boolean;

    protected readonly getTextContent = getTextContent;
}
