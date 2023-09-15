import {
    faArrowDown,
    faCircle,
    faCircleInfo,
    faCompress,
    faExpand,
    faPaperPlane,
    faRedo,
    faRobot,
    faThumbsDown,
    faThumbsUp,
    faTrash,
    faXmark,
} from '@fortawesome/free-solid-svg-icons';
import { NavigationStart, Router } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ButtonType } from 'app/shared/components/button.component';
import { AfterViewInit, Component, ElementRef, Inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { IrisStateStore } from 'app/iris/state-store.service';
import { HttpErrorResponse } from '@angular/common/http';
import {
    ActiveConversationMessageLoadedAction,
    ConversationErrorOccurredAction,
    NumNewMessagesResetAction,
    RateMessageSuccessAction,
    StudentMessageSentAction,
} from 'app/iris/state-store.model';
import { IrisHttpMessageService } from 'app/iris/http-message.service';
import {
    IrisArtemisClientMessage,
    IrisClientMessage,
    IrisMessage,
    IrisSender,
    IrisServerMessage,
    isArtemisClientSentMessage,
    isServerSentMessage,
    isStudentSentMessage,
} from 'app/entities/iris/iris-message.model';
import { IrisMessageContent, IrisMessageContentType } from 'app/entities/iris/iris-content-type.model';
import { Subscription } from 'rxjs';
import { SharedService } from 'app/iris/shared.service';
import { IrisSessionService } from 'app/iris/session.service';
import { IrisErrorMessageKey, IrisErrorType } from 'app/entities/iris/iris-errors.model';
import dayjs from 'dayjs';
import { AnimationEvent, animate, state, style, transition, trigger } from '@angular/animations';
import { UserService } from 'app/core/user/user.service';
import { IrisLogoSize } from '../../iris-logo/iris-logo.component';
import interact from 'interactjs';
import { DOCUMENT } from '@angular/common';

@Component({
    selector: 'jhi-exercise-chat-widget',
    templateUrl: './exercise-chat-widget.component.html',
    styleUrls: ['./exercise-chat-widget.component.scss'],
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
export class ExerciseChatWidgetComponent implements OnInit, OnDestroy, AfterViewInit {
    // Icons
    faTrash = faTrash;
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
    faRedo = faRedo;

    // ViewChilds
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
    error: IrisErrorType | null;
    dots = 1;
    isFirstMessage = false;
    resendAnimationActive = false;
    shakeErrorField = false;
    shouldLoadGreetingMessage = true;
    fadeState = '';
    exerciseId: number;
    sessionService: IrisSessionService;
    shouldShowEmptyMessageError = false;

    // User preferences
    userAccepted: boolean;
    isScrolledToBottom = true;
    rows = 1;
    initialWidth = 330;
    initialHeight = 430;
    fullWidthFactor = 0.93;
    fullHeightFactor = 0.85;
    fullSize = false;
    public ButtonType = ButtonType;
    readonly IrisLogoSize = IrisLogoSize;
    private navigationSubscription: Subscription;

    constructor(
        private dialog: MatDialog,
        @Inject(MAT_DIALOG_DATA) public data: any,
        private httpMessageService: IrisHttpMessageService,
        private userService: UserService,
        private router: Router,
        private sharedService: SharedService,
        private modalService: NgbModal,
        @Inject(DOCUMENT) private document: Document,
    ) {
        this.stateStore = data.stateStore;
        this.exerciseId = data.exerciseId;
        this.sessionService = data.sessionService;
        this.navigationSubscription = this.router.events.subscribe((event) => {
            if (event instanceof NavigationStart) {
                this.dialog.closeAll();
            }
        });
        this.fullSize = data.fullSize ?? false;
    }

    ngOnInit() {
        this.userService.getIrisAcceptedAt().subscribe((res) => {
            this.userAccepted = !!res;
            if (this.userAccepted) {
                this.loadFirstMessage();
            }
        });

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

        interact('.chat-widget')
            .resizable({
                // resize from all edges and corners
                edges: { left: true, right: true, bottom: true, top: true },

                listeners: {
                    move: (event) => {
                        const target = event.target;
                        let x = parseFloat(target.getAttribute('data-x')) || 0;
                        let y = parseFloat(target.getAttribute('data-y')) || 0;

                        // update the element's style
                        target.style.width = event.rect.width + 'px';
                        target.style.height = event.rect.height + 'px';

                        // Reset fullsize if widget smaller than the full size factors times the overlay container size
                        const cntRect = (this.document.querySelector('.cdk-overlay-container') as HTMLElement).getBoundingClientRect();
                        this.fullSize = !(event.rect.width < cntRect.width * this.fullWidthFactor || event.rect.height < cntRect.height * this.fullHeightFactor);

                        // translate when resizing from top or left edges
                        x += event.deltaRect.left;
                        y += event.deltaRect.top;

                        target.style.transform = 'translate(' + x + 'px,' + y + 'px)';

                        target.setAttribute('data-x', x);
                        target.setAttribute('data-y', y);
                    },
                },
                modifiers: [
                    // keep the edges inside the parent
                    interact.modifiers.restrictEdges({
                        outer: '.cdk-overlay-container',
                    }),

                    // minimum size
                    interact.modifiers.restrictSize({
                        min: { width: this.initialWidth, height: this.initialHeight },
                    }),
                ],

                inertia: true,
            })
            .draggable({
                listeners: {
                    move: (event: any) => {
                        const target = event.target,
                            // keep the dragged position in the data-x/data-y attributes
                            x = (parseFloat(target.getAttribute('data-x')) || 0) + event.dx,
                            y = (parseFloat(target.getAttribute('data-y')) || 0) + event.dy;

                        // translate the element
                        target.style.transform = 'translate(' + x + 'px, ' + y + 'px)';

                        // update the posiion attributes
                        target.setAttribute('data-x', x);
                        target.setAttribute('data-y', y);
                    },
                },
                inertia: true,
                modifiers: [
                    interact.modifiers.restrictRect({
                        restriction: '.cdk-overlay-container',
                        endOnly: true,
                    }),
                ],
            });
        this.setPositionAndScale();
    }

    setPositionAndScale() {
        const cntRect = (this.document.querySelector('.cdk-overlay-container') as HTMLElement)?.getBoundingClientRect();
        if (!cntRect) {
            return;
        }

        const initX = this.fullSize ? (cntRect.width * (1 - this.fullWidthFactor)) / 2.0 : cntRect.width - this.initialWidth - 50;
        const initY = this.fullSize ? (cntRect.height * (1 - this.fullHeightFactor)) / 2.0 : cntRect.height - this.initialHeight - 100;

        const nE = this.document.querySelector('.chat-widget') as HTMLElement;
        nE.style.transform = `translate(${initX}px, ${initY}px)`;
        nE.setAttribute('data-x', String(initX));
        nE.setAttribute('data-y', String(initY));

        // Set width and height
        if (this.fullSize) {
            nE.style.width = `${cntRect.width * this.fullWidthFactor}px`;
            nE.style.height = `${cntRect.height * this.fullHeightFactor}px`;
        } else {
            nE.style.width = `${this.initialWidth}px`;
            nE.style.height = `${this.initialHeight}px`;
        }
    }

    ngOnDestroy() {
        this.stateSubscription.unsubscribe();
        this.navigationSubscription.unsubscribe();
        this.toggleScrollLock(false);
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
     * Loads the first message in the conversation if it's not already loaded.
     */
    loadFirstMessage(): void {
        const firstMessageContent = {
            textContent: 'artemisApp.exerciseChatbot.firstMessage',
            type: IrisMessageContentType.TEXT,
        } as IrisMessageContent;

        const firstMessage = {
            sender: IrisSender.ARTEMIS_CLIENT,
            content: [firstMessageContent],
            sentAt: dayjs(),
        } as IrisArtemisClientMessage;

        if (this.messages.length === 0) {
            this.isFirstMessage = true;
            this.stateStore.dispatch(new ActiveConversationMessageLoadedAction(firstMessage));
        } else if (this.messages[0].sender === IrisSender.ARTEMIS_CLIENT) {
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
        if (this.error?.fatal) {
            return;
        }
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
            }, 20000);
            this.stateStore
                .dispatchAndThen(new StudentMessageSentAction(message, timeoutId))
                .then(() => this.httpMessageService.createMessage(<number>this.sessionId, message).toPromise())
                .then(() => this.scrollToBottom('smooth'))
                .catch((error: HttpErrorResponse) => {
                    if (error.status === 403) {
                        this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.IRIS_DISABLED));
                    } else {
                        this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.SEND_MESSAGE_FAILED));
                    }
                    this.scrollToBottom('smooth');
                });
            this.newMessageTextContent = '';
        }
        this.scrollToBottom('smooth');
        this.resetChatBodyHeight();
    }

    /**
     * Clear session and start a new conversation.
     */
    onClearSession(content: any) {
        this.modalService.open(content).result.then((result: string) => {
            if (result === 'confirm') {
                this.createNewSession();
            }
        });
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
        this.userService.acceptIris().subscribe(() => {
            this.userAccepted = true;
        });
        this.loadFirstMessage();
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
     * Maximizes the chat widget screen.
     */
    maximizeScreen() {
        this.fullSize = true;
        this.setPositionAndScale();
    }

    /**
     * Minimizes the chat widget screen.
     */
    minimizeScreen() {
        this.fullSize = false;
        this.setPositionAndScale();
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
                this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.RATE_MESSAGE_FAILED));
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
     * Checks if a message is a welcome message generated by the client.
     * @param message - The message to check.
     * @returns A boolean indicating if the message is a client-sent message.
     */
    isArtemisClientSentMessage(message: IrisMessage): message is IrisServerMessage {
        return isArtemisClientSentMessage(message);
    }

    /**
     * Creates a new user message.
     * @param message - The content of the message.
     * @returns A new IrisClientMessage object representing the user message.
     */
    newUserMessage(message: string): IrisClientMessage {
        const content: IrisMessageContent = {
            type: IrisMessageContentType.TEXT,
            textContent: message,
        };
        return {
            sender: this.SENDER_USER,
            content: [content],
        };
    }

    resendMessage(message: IrisClientMessage) {
        this.resendAnimationActive = true;
        message.messageDifferentiator = message.id;

        const timeoutId = setTimeout(() => {
            // will be cleared by the store automatically
            this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.IRIS_SERVER_RESPONSE_TIMEOUT));
            this.scrollToBottom('smooth');
        }, 20000);
        this.stateStore
            .dispatchAndThen(new StudentMessageSentAction(message, timeoutId))
            .then(() => this.httpMessageService.resendMessage(<number>this.sessionId, message).toPromise())
            .then(() => {
                this.scrollToBottom('smooth');
            })
            .catch((error: HttpErrorResponse) => {
                if (error.status === 403) {
                    this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.IRIS_DISABLED));
                } else {
                    this.stateStore.dispatch(new ConversationErrorOccurredAction(IrisErrorMessageKey.SEND_MESSAGE_FAILED));
                }
            })
            .finally(() => {
                this.resendAnimationActive = false;
                this.scrollToBottom('smooth');
            });
    }

    isSendMessageFailedError(): boolean {
        return this.error?.key == IrisErrorMessageKey.SEND_MESSAGE_FAILED || this.error?.key == IrisErrorMessageKey.IRIS_SERVER_RESPONSE_TIMEOUT;
    }

    toggleScrollLock(lockParent: boolean): void {
        if (lockParent) {
            document.body.classList.add('cdk-global-scrollblock');
        } else {
            document.body.classList.remove('cdk-global-scrollblock');
        }
    }

    deactivateSubmitButton(): boolean {
        return !this.userAccepted || this.isLoading || (!!this.error && this.error.fatal);
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
            return Object.fromEntries(Object.entries(this.error.paramsMap as Map<string, any>));
        }
        return null;
    }

    isClearChatEnabled(): boolean {
        return this.messages.length > 1 || (this.messages.length === 1 && !isArtemisClientSentMessage(this.messages[0]));
    }

    createNewSession() {
        this.sessionService.createNewSession(this.exerciseId);
    }
}
