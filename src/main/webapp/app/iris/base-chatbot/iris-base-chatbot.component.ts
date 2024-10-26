import { faArrowDown, faCircle, faCircleInfo, faCompress, faExpand, faPaperPlane, faRedo, faThumbsDown, faThumbsUp, faTrash, faXmark } from '@fortawesome/free-solid-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, input, output, viewChild } from '@angular/core';
import { IrisAssistantMessage, IrisMessage, IrisSender } from 'app/entities/iris/iris-message.model';
import { Subscription } from 'rxjs';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import { ButtonType } from 'app/shared/components/button.component';
import { TranslateService } from '@ngx-translate/core';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/iris-logo/iris-logo.component';
import { IrisStageDTO, IrisStageStateDTO } from 'app/entities/iris/iris-stage-dto.model';
import { IrisRateLimitInformation } from 'app/entities/iris/iris-ratelimit-info.model';
import { IrisStatusService } from 'app/iris/iris-status.service';
import { IrisMessageContentType, IrisTextMessageContent } from 'app/entities/iris/iris-content-type.model';
import { AccountService } from 'app/core/auth/account.service';
import { animate, group, style, transition, trigger } from '@angular/animations';
import { IrisChatService } from 'app/iris/iris-chat.service';
import * as _ from 'lodash-es';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ChatStatusBarComponent } from 'app/iris/base-chatbot/chat-status-bar/chat-status-bar.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-iris-base-chatbot',
    templateUrl: './iris-base-chatbot.component.html',
    styleUrls: ['./iris-base-chatbot.component.scss'],
    standalone: true,
    animations: [
        trigger('messageAnimation', [
            transition(':enter', [
                style({
                    height: '0',
                    transform: 'scale(0)',
                }),
                group([
                    animate(
                        '0.3s ease-in-out',
                        style({
                            height: '*',
                        }),
                    ),
                    animate(
                        '0.3s 0.1s cubic-bezier(.2,1.22,.64,1)',
                        style({
                            transform: 'scale(1)',
                        }),
                    ),
                ]),
            ]),
        ]),
        trigger('suggestionAnimation', [
            transition(':enter', [
                style({ height: 0, opacity: 0 }),
                group([
                    animate(
                        '0.3s 0.5s ease-in-out',
                        style({
                            height: '*',
                            opacity: 1,
                        }),
                    ),
                ]),
            ]),
            transition(':leave', [
                style({ height: '*', opacity: 1 }),
                group([
                    animate(
                        '0.3s ease-in-out',
                        style({
                            height: 0,
                            opacity: 0,
                        }),
                    ),
                ]),
            ]),
        ]),
    ],
    imports: [ArtemisSharedComponentModule, ChatStatusBarComponent, IrisLogoComponent, ArtemisMarkdownModule, ArtemisSharedModule],
})
export class IrisBaseChatbotComponent implements OnInit, OnDestroy, AfterViewInit {
    // Icons
    faTrash = faTrash;
    faCircle = faCircle;
    faPaperPlane = faPaperPlane;
    faExpand = faExpand;
    faXmark = faXmark;
    faArrowDown = faArrowDown;
    faCircleInfo = faCircleInfo;
    faCompress = faCompress;
    faThumbsUp = faThumbsUp;
    faThumbsDown = faThumbsDown;
    faRedo = faRedo;

    // State variables
    messagesSubscription: Subscription;
    stagesSubscription: Subscription;
    errorSubscription: Subscription;
    numNewMessageSubscription: Subscription;
    rateLimitSubscription: Subscription;
    activeStatusSubscription: Subscription;
    suggestionsSubscription: Subscription;

    messages: IrisMessage[] = [];
    stages?: IrisStageDTO[] = [];
    suggestions?: string[] = [];
    error?: IrisErrorMessageKey;
    numNewMessages: number = 0;
    rateLimitInfo: IrisRateLimitInformation;
    active: boolean = true;

    newMessageTextContent = '';
    isLoading: boolean;
    shouldAnimate: boolean = false;
    hasActiveStage: boolean = false;

    // User preferences
    userAccepted: boolean;
    isScrolledToBottom = true;
    rows = 1;
    resendAnimationActive: boolean;
    public ButtonType = ButtonType;

    fullSize = input<boolean>();
    showCloseButton = input<boolean>(false);
    fullSizeToggle = output<void>();
    closeClicked = output<void>();

    // ViewChildren
    messagesElement = viewChild<ElementRef>('messagesElement');
    scrollArrow = viewChild<ElementRef>('scrollArrow');
    messageTextarea = viewChild<ElementRef<HTMLTextAreaElement>>('messageTextarea');
    acceptButton = viewChild<ElementRef<HTMLButtonElement>>('acceptButton');

    // Types
    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly IrisMessageContentType = IrisMessageContentType;
    protected readonly IrisAssistantMessage = IrisAssistantMessage;
    protected readonly IrisTextMessageContent = IrisTextMessageContent;
    protected readonly IrisSender = IrisSender;
    protected readonly IrisErrorMessageKey = IrisErrorMessageKey;

    constructor(
        protected accountService: AccountService,
        protected modalService: NgbModal,
        protected translateService: TranslateService,
        protected statusService: IrisStatusService,
        protected chatService: IrisChatService,
    ) {}

    ngOnInit() {
        this.messagesSubscription = this.chatService.currentMessages().subscribe((messages) => {
            if (messages.length !== this.messages?.length) {
                this.scrollToBottom('auto');
            }
            this.messages = _.cloneDeep(messages).reverse();
            this.messages.forEach((message) => {
                // @ts-expect-error - TS doesn't get that I'm checking for the type
                if (message.content?.[0]?.textContent) {
                    // Double all \n
                    const cnt = message.content[0] as IrisTextMessageContent;
                    cnt.textContent = cnt.textContent.replace(/\n\n/g, '\n\u00A0\n');
                    cnt.textContent = cnt.textContent.replace(/\n/g, '\n\n');
                }
            });
        });
        this.stagesSubscription = this.chatService.currentStages().subscribe((stages) => {
            this.stages = stages;
            this.hasActiveStage = stages?.some((stage) => [IrisStageStateDTO.IN_PROGRESS, IrisStageStateDTO.NOT_STARTED].includes(stage.state));
        });
        this.errorSubscription = this.chatService.currentError().subscribe((error) => (this.error = error));
        this.numNewMessageSubscription = this.chatService.currentNumNewMessages().subscribe((num) => {
            this.numNewMessages = num;
            this.checkUnreadMessageScroll();
        });
        this.rateLimitSubscription = this.statusService.currentRatelimitInfo().subscribe((info) => (this.rateLimitInfo = info));
        this.activeStatusSubscription = this.statusService.getActiveStatus().subscribe((active) => {
            if (!active) {
                this.isLoading = false;
                this.resendAnimationActive = false;
            }
            this.active = active;
        });
        this.suggestionsSubscription = this.chatService.currentSuggestions().subscribe((suggestions) => {
            this.suggestions = suggestions;
        });

        this.checkIfUserAcceptedIris();

        // Focus on message textarea
        setTimeout(() => {
            const textarea = this.messageTextarea()?.nativeElement;
            if (textarea) {
                textarea.focus();
            } else {
                this.acceptButton()?.nativeElement.focus();
            }
        }, 150);
    }

    ngAfterViewInit() {
        this.checkUnreadMessageScroll();
        setTimeout(() => (this.shouldAnimate = true));
    }

    checkUnreadMessageScroll() {
        if (this.numNewMessages > 0) {
            this.scrollToBottom('smooth');
        }
    }

    ngOnDestroy() {
        this.messagesSubscription.unsubscribe();
        this.stagesSubscription.unsubscribe();
        this.errorSubscription.unsubscribe();
        this.numNewMessageSubscription.unsubscribe();
        this.rateLimitSubscription.unsubscribe();
        this.activeStatusSubscription.unsubscribe();
        this.suggestionsSubscription.unsubscribe();
    }

    checkIfUserAcceptedIris(): void {
        this.userAccepted = !!this.accountService.userIdentity?.irisAccepted;
        setTimeout(() => this.adjustTextareaRows(), 0);
    }

    /**
     * Handles the send button click event and sends the user's message.
     */
    onSend(): void {
        this.chatService.messagesRead();
        if (this.newMessageTextContent) {
            this.isLoading = true;
            this.chatService.sendMessage(this.newMessageTextContent).subscribe(() => {
                this.isLoading = false;
            });
            this.newMessageTextContent = '';
        }
        this.resetChatBodyHeight();
    }

    resendMessage(message: IrisMessage) {
        if (message.sender !== IrisSender.USER) {
            return;
        }
        let observable;
        if (message.id) {
            observable = this.chatService.resendMessage(message);
            this.resendAnimationActive = true;
        } else if (message.content?.[0]?.textContent) {
            observable = this.chatService.sendMessage(message.content[0].textContent);
        } else {
            this.resendAnimationActive = false;
            return;
        }
        this.isLoading = true;

        observable.subscribe(() => {
            this.resendAnimationActive = false;
            this.isLoading = false;
            this.chatService.messagesRead();
        });
    }

    /**
     * Rates a message as helpful or unhelpful.
     * @param message The message to rate.
     * @param helpful A boolean indicating if the message is helpful or not.
     */
    rateMessage(message: IrisMessage, helpful?: boolean) {
        if (message.sender !== IrisSender.LLM) {
            return;
        }
        message.helpful = !!helpful;
        this.chatService.rateMessage(message, helpful).subscribe();
    }

    /**
     * Scrolls the chat body to the bottom.
     * @param behavior - The scroll behavior.
     */
    scrollToBottom(behavior: ScrollBehavior) {
        setTimeout(() => {
            const messagesElement: HTMLElement | undefined = this.messagesElement()?.nativeElement;
            messagesElement?.scrollTo({
                top: 0,
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
                this.chatService.clearChat();
            }
        });
    }

    /**
     * Accepts the permission to use the chat widget.
     */
    acceptPermission() {
        this.chatService.setUserAccepted();
        this.userAccepted = true;
    }

    /**
     * This method is intended to handle the closing of the chat interface.
     * It emits a close event which should be handled by the parent component.
     */
    closeChat() {
        this.chatService.messagesRead();
        this.closeClicked.emit();
    }

    /**
     * Handles the key events in the message textarea.
     * @param event - The keyboard event.
     */
    handleKey(event: KeyboardEvent): void {
        if (event.key === 'Enter') {
            if (!this.isLoading && this.active) {
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
        const textarea: HTMLTextAreaElement | undefined = this.messageTextarea()?.nativeElement;
        if (!textarea) return;

        textarea.style.height = 'auto'; // Reset the height to auto
        const bufferForSpaceBetweenLines = 4;
        const lineHeight = parseInt(getComputedStyle(textarea).lineHeight, 10) + bufferForSpaceBetweenLines;
        const maxRows = 3;
        const maxHeight = lineHeight * maxRows;

        textarea.style.height = `${Math.min(textarea.scrollHeight, maxHeight)}px`;

        this.adjustScrollButtonPosition(Math.min(textarea.scrollHeight, maxHeight) / lineHeight);
    }

    /**
     * Handles the row change event in the message textarea.
     */
    onModelChange() {
        const textarea: HTMLTextAreaElement | undefined = this.messageTextarea()?.nativeElement;
        if (!textarea) return;

        const newRows = textarea.value.split('\n').length;
        if (newRows != this.rows) {
            if (newRows <= 3) {
                textarea.rows = newRows;
                this.adjustScrollButtonPosition(newRows);
                this.rows = newRows;
            }
        }
    }

    /**
     * Adjusts the position of the scroll button based on the number of rows in the message textarea.
     * @param newRows - The new number of rows.
     */
    adjustScrollButtonPosition(newRows: number) {
        const textarea: HTMLTextAreaElement | undefined = this.messageTextarea()?.nativeElement;
        const scrollArrow: HTMLElement | undefined = this.scrollArrow()?.nativeElement;
        if (!textarea || !scrollArrow) return;

        const lineHeight = parseInt(window.getComputedStyle(textarea).lineHeight);
        const rowHeight = lineHeight * newRows - lineHeight;
        setTimeout(() => {
            scrollArrow.style.bottom = `calc(11% + ${rowHeight}px)`;
        }, 10);
    }

    /**
     * Resets the height of the chat body.
     */
    resetChatBodyHeight() {
        const textarea: HTMLTextAreaElement | undefined = this.messageTextarea()?.nativeElement;
        const scrollArrow: HTMLElement | undefined = this.scrollArrow()?.nativeElement;
        if (!textarea || !scrollArrow) return;

        textarea.rows = 1;
        textarea.style.height = '';
        scrollArrow.style.bottom = '';
    }

    checkChatScroll() {
        const messagesElement: HTMLElement | undefined = this.messagesElement()?.nativeElement;
        if (!messagesElement) return;

        const scrollTop = messagesElement.scrollTop;
        this.isScrolledToBottom = scrollTop < 50;
    }

    onSuggestionClick(suggestion: string) {
        this.newMessageTextContent = suggestion;
        this.onSend();
    }
}
