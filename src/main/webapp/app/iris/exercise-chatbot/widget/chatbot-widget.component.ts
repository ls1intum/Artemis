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
import { AfterViewInit, Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { IrisStateStore } from 'app/iris/state-store.service';
import { ActiveConversationMessageLoadedAction, ConversationErrorOccurredAction, NumNewMessagesResetAction, StudentMessageSentAction } from 'app/iris/state-store.model';
import {
    IrisArtemisClientMessage,
    IrisMessage,
    IrisSender,
    IrisUserMessage,
    isArtemisClientSentMessage,
    isServerSentMessage,
    isStudentSentMessage,
} from 'app/entities/iris/iris-message.model';
import { IrisMessageContentType, IrisTextMessageContent, getTextContent, isTextContent } from 'app/entities/iris/iris-content-type.model';
import { SharedService } from 'app/iris/shared.service';
import { IrisSessionService } from 'app/iris/session.service';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import dayjs from 'dayjs/esm';
import { AnimationEvent, animate, state, style, transition, trigger } from '@angular/animations';
import { UserService } from 'app/core/user/user.service';
import { IrisLogoSize } from '../../iris-logo/iris-logo.component';
import interact from 'interactjs';
import { DOCUMENT } from '@angular/common';
import { IrisChatSessionService } from 'app/iris/chat-session.service';
import { TranslateService } from '@ngx-translate/core';
import { IrisBaseChatbotComponent } from 'app/iris/iris-base-chatbot.component';

@Component({
    selector: 'jhi-chatbot-widget',
    templateUrl: './chatbot-widget.component.html',
    styleUrls: ['./chatbot-widget.component.scss'],
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
export class IrisChatbotWidgetComponent extends IrisBaseChatbotComponent implements OnInit, OnDestroy, AfterViewInit {
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

    // State variables
    stateStore: IrisStateStore;
    messages: IrisMessage[] = [];
    newMessageTextContent = '';
    numNewMessages = 0;
    dots = 1;
    resendAnimationActive = false;
    shakeErrorField = false;
    shouldLoadGreetingMessage = true;
    fadeState = '';
    courseId: number;
    exerciseId: number;
    sessionService: IrisSessionService;
    shouldShowEmptyMessageError = false;
    importExerciseUrl: string;

    // User preferences
    isScrolledToBottom = true;
    rows = 1;
    initialWidth = 330;
    initialHeight = 430;
    fullWidthFactor = 0.93;
    fullHeightFactor = 0.85;
    fullSize = false;
    public ButtonType = ButtonType;
    readonly IrisLogoSize = IrisLogoSize;
    private readonly MAX_INT_JAVA = 2147483647;

    constructor(
        private dialog: MatDialog,
        @Inject(MAT_DIALOG_DATA) public data: any,
        private router: Router,
        private sharedService: SharedService,
        @Inject(DOCUMENT) private document: Document,
        private translateService: TranslateService,
        userService: UserService,
        modalService: NgbModal,
    ) {
        super(userService, modalService);
        this.stateStore = data.stateStore;
        this.courseId = data.courseId;
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
        this.checkIfUserAcceptedIris();

        this.animateDots();

        // Subscribe to state changes
        this.subscribeToStateChanges();

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

        const initX = this.fullSize ? (cntRect.width * (1 - this.fullWidthFactor)) / 2.0 : cntRect.width - this.initialWidth - 20;
        const initY = this.fullSize ? (cntRect.height * (1 - this.fullHeightFactor)) / 2.0 : cntRect.height - this.initialHeight - 20;

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
     * Inserts the correct link to import the current programming exercise for a new variant generation.
     */
    getFirstMessageContent(): string {
        if (this.isChatSession()) {
            return this.translateService.instant('artemisApp.exerciseChatbot.tutorFirstMessage');
        }
        return '';
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

    /**
     * Closes the chat widget.
     */
    closeChat() {
        this.stateStore.dispatch(new NumNewMessagesResetAction());
        this.sharedService.changeChatOpenStatus(false);
        this.dialog.closeAll();
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

    isSendMessageFailedError(): boolean {
        return this.error?.key == IrisErrorMessageKey.SEND_MESSAGE_FAILED || this.error?.key == IrisErrorMessageKey.IRIS_SERVER_RESPONSE_TIMEOUT;
    }

    toggleScrollLock(lockParent: boolean): void {
        if (lockParent) {
            document.body.classList.add('cdk-global-scroll');
        } else {
            document.body.classList.remove('cdk-global-scroll');
        }
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

    isClearChatButtonEnabled(): boolean {
        return this.messages.length > 1 || (this.messages.length === 1 && !isArtemisClientSentMessage(this.messages[0]));
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
    protected readonly isStudentSentMessage = isStudentSentMessage;
    protected readonly isServerSentMessage = isServerSentMessage;
    protected readonly isArtemisClientSentMessage = isArtemisClientSentMessage;
}
