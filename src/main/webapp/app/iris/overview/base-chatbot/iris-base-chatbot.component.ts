import {
    faArrowDown,
    faChevronRight,
    faCircle,
    faCircleInfo,
    faCompress,
    faExpand,
    faLink,
    faPaperPlane,
    faPenToSquare,
    faRedo,
    faThumbsDown,
    faThumbsUp,
    faTrash,
    faXmark,
} from '@fortawesome/free-solid-svg-icons';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild, computed, inject, input, signal } from '@angular/core';
import { IrisAssistantMessage, IrisMessage, IrisSender } from 'app/iris/shared/entities/iris-message.model';
import { Subscription } from 'rxjs';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { TranslateService } from '@ngx-translate/core';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { IrisStageDTO, IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { IrisRateLimitInformation } from 'app/iris/shared/entities/iris-ratelimit-info.model';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { IrisMessageContentType, IrisTextMessageContent } from 'app/iris/shared/entities/iris-content-type.model';
import { AccountService } from 'app/core/auth/account.service';
import { animate, group, style, transition, trigger } from '@angular/animations';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import * as _ from 'lodash-es';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ChatStatusBarComponent } from './chat-status-bar/chat-status-bar.component';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AsPipe } from 'app/shared/pipes/as.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ChatHistoryItemComponent } from './chat-history-item/chat-history-item.component';
import { NgClass } from '@angular/common';
import { facSidebar } from 'app/shared/icons/icons';
import { User } from 'app/core/user/user.model';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { LLMSelectionDecision } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';

@Component({
    selector: 'jhi-iris-base-chatbot',
    templateUrl: './iris-base-chatbot.component.html',
    styleUrls: ['./iris-base-chatbot.component.scss'],
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
    imports: [
        IrisLogoComponent,
        RouterLink,
        FaIconComponent,
        NgbTooltip,
        TranslateDirective,
        ChatStatusBarComponent,
        FormsModule,
        ButtonComponent,
        ArtemisTranslatePipe,
        AsPipe,
        HtmlForMarkdownPipe,
        ChatHistoryItemComponent,
        NgClass,
        SearchFilterComponent,
    ],
})
export class IrisBaseChatbotComponent implements OnInit, OnDestroy, AfterViewInit {
    protected accountService = inject(AccountService);
    protected modalService = inject(NgbModal);
    protected translateService = inject(TranslateService);
    protected statusService = inject(IrisStatusService);
    protected chatService = inject(IrisChatService);
    protected route = inject(ActivatedRoute);
    protected llmModalService = inject(LLMSelectionModalService);

    // Icons
    protected readonly faTrash = faTrash;
    protected readonly faCircle = faCircle;
    protected readonly faPaperPlane = faPaperPlane;
    protected readonly faExpand = faExpand;
    protected readonly faXmark = faXmark;
    protected readonly faArrowDown = faArrowDown;
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faCompress = faCompress;
    protected readonly faThumbsUp = faThumbsUp;
    protected readonly faThumbsDown = faThumbsDown;
    protected readonly faRedo = faRedo;
    protected readonly faPenToSquare = faPenToSquare;
    protected readonly faChevronRight = faChevronRight;
    protected readonly facSidebar = facSidebar;
    protected readonly faLink = faLink;

    // Types
    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly IrisMessageContentType = IrisMessageContentType;
    protected readonly IrisAssistantMessage = IrisAssistantMessage;
    protected readonly IrisTextMessageContent = IrisTextMessageContent;
    protected readonly IrisSender = IrisSender;
    protected readonly IrisErrorMessageKey = IrisErrorMessageKey;

    // State variables
    relatedEntityIdSubscription: Subscription;
    chatModeSubscription: Subscription;
    sessionIdSubscription: Subscription;
    messagesSubscription: Subscription;
    stagesSubscription: Subscription;
    errorSubscription: Subscription;
    numNewMessageSubscription: Subscription;
    rateLimitSubscription: Subscription;
    activeStatusSubscription: Subscription;
    suggestionsSubscription: Subscription;
    routeSubscription: Subscription;
    chatSessionsSubscription: Subscription;

    private currentRelatedEntityId = signal<number | undefined>(undefined);
    private currentChatMode = signal<ChatServiceMode | undefined>(undefined);
    relatedEntityRoute = computed<string | undefined>(() => this.computeRelatedEntityRoute(this.currentChatMode(), this.currentRelatedEntityId()));
    relatedEntityLinkButtonLabel = computed<string | undefined>(() => this.computeRelatedEntityLinkButtonLabel(this.currentChatMode()));
    currentSessionId: number | undefined;
    chatSessions: IrisSessionDTO[] = [];
    messages: IrisMessage[] = [];
    stages?: IrisStageDTO[] = [];
    suggestions?: string[] = [];
    error?: IrisErrorMessageKey;
    numNewMessages: number = 0;
    rateLimitInfo: IrisRateLimitInformation;
    active = true;

    newMessageTextContent = '';
    isLoading: boolean;
    shouldAnimate = false;
    hasActiveStage = false;

    isChatHistoryOpen = true;

    searchValue = '';

    // User preferences
    user: User | undefined;
    userAccepted: LLMSelectionDecision | undefined;
    isScrolledToBottom = true;
    rows = 1;
    resendAnimationActive: boolean;
    public ButtonType = ButtonType;

    showDeclineButton = input<boolean>(true);
    isChatHistoryAvailable = input<boolean>(false);
    isEmbeddedChat = input<boolean>(false);
    @Input() fullSize: boolean | undefined;
    @Input() showCloseButton = false;
    @Input() isChatGptWrapper = false;
    @Output() fullSizeToggle = new EventEmitter<void>();
    @Output() closeClicked = new EventEmitter<void>();

    // ViewChilds
    @ViewChild('messagesElement') messagesElement!: ElementRef;
    @ViewChild('scrollArrow') scrollArrow!: ElementRef;
    @ViewChild('messageTextarea') messageTextarea: ElementRef<HTMLTextAreaElement>;
    @ViewChild('acceptButton') acceptButton: ElementRef<HTMLButtonElement>;

    ngOnInit() {
        this.routeSubscription = this.route.queryParams?.subscribe((params: any) => {
            if (params?.irisQuestion) {
                this.newMessageTextContent = params.irisQuestion;
            }
        });
        this.sessionIdSubscription = this.chatService.currentSessionId().subscribe((sessionId) => {
            this.currentSessionId = sessionId;
        });
        this.relatedEntityIdSubscription = this.chatService.currentRelatedEntityId().subscribe((entityId) => {
            this.currentRelatedEntityId.set(entityId);
        });
        this.chatModeSubscription = this.chatService.currentChatMode().subscribe((chatMode) => {
            this.currentChatMode.set(chatMode);
        });
        this.messagesSubscription = this.chatService.currentMessages().subscribe((messages) => {
            if (messages.length !== this.messages?.length) {
                this.scrollToBottom('auto');
                setTimeout(() => this.messageTextarea?.nativeElement?.focus(), 10);
            }
            this.messages = _.cloneDeep(messages).reverse();
            this.messages.forEach((message) => {
                if (message.content?.[0] && 'textContent' in message.content[0]) {
                    // Double all \n
                    const cnt = message.content[0] as IrisTextMessageContent;
                    cnt.textContent = cnt.textContent.replace(/\n\n/g, '\n\u00A0\n');
                    cnt.textContent = cnt.textContent.replace(/\n/g, '\n\n');
                }
                if ('accessedMemories' in message) {
                    // eslint-disable-next-line no-undef
                    console.log('Accessed memories found in message:', message.accessedMemories);
                }
                if ('createdMemories' in message) {
                    // eslint-disable-next-line no-undef
                    console.log('Created memories found in message:', message.createdMemories);
                }
            });
        });
        this.chatSessionsSubscription = this.chatService.availableChatSessions().subscribe((sessions) => {
            this.chatSessions = sessions;
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

        this.checkIfUserAcceptedLLMUsage();
        if (!this.userAccepted) {
            this.showAISelectionModal();
        } else {
            this.focusInputAfterAcceptance();
        }
    }

    private focusInputAfterAcceptance() {
        setTimeout(() => {
            if (this.messageTextarea) {
                this.messageTextarea.nativeElement.focus();
            } else if (this.acceptButton) {
                this.acceptButton.nativeElement.focus();
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
        this.routeSubscription?.unsubscribe();
        this.chatSessionsSubscription.unsubscribe();
    }

    checkIfUserAcceptedLLMUsage(): void {
        this.userAccepted = this.accountService.userIdentity()?.selectedLLMUsage;
        setTimeout(() => this.adjustTextareaRows(), 0);
    }

    async showAISelectionModal(): Promise<void> {
        const choice = await this.llmModalService.open();

        switch (choice) {
            case 'cloud':
                this.acceptPermission(LLMSelectionDecision.CLOUD_AI);
                break;
            case 'local':
                this.acceptPermission(LLMSelectionDecision.LOCAL_AI);
                break;
            case 'no_ai':
                this.chatService.updateLLMUsageConsent(LLMSelectionDecision.NO_AI);
                this.closeChat();
                break;
            case 'none':
                this.closeChat();
                break;
        }
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
            const messagesElement: HTMLElement = this.messagesElement?.nativeElement;
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
    acceptPermission(decision: LLMSelectionDecision) {
        this.chatService.updateLLMUsageConsent(decision);
        this.userAccepted = decision;
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
        const textarea: HTMLTextAreaElement = this.messageTextarea.nativeElement;
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
        const textarea: HTMLTextAreaElement = this.messageTextarea.nativeElement;
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
        const textarea: HTMLTextAreaElement = this.messageTextarea.nativeElement;
        const scrollArrow: HTMLElement = this.scrollArrow.nativeElement;
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
        const textarea: HTMLTextAreaElement = this.messageTextarea.nativeElement;
        const scrollArrow: HTMLElement = this.scrollArrow.nativeElement;
        textarea.rows = 1;
        textarea.style.height = '';
        scrollArrow.style.bottom = '';
    }

    checkChatScroll() {
        const messagesElement = this.messagesElement.nativeElement;
        const scrollTop = messagesElement.scrollTop;
        this.isScrolledToBottom = scrollTop < 50;
    }

    onSuggestionClick(suggestion: string) {
        this.newMessageTextContent = suggestion;
        this.onSend();
    }

    onSessionClick(session: IrisSessionDTO) {
        this.chatService.switchToSession(session);
    }

    setChatHistoryVisibility(isOpen: boolean) {
        this.isChatHistoryOpen = isOpen;
        if (!isOpen) {
            this.setSearchValue('');
        }
    }

    /**
     * Retrieves chat sessions that occurred between a specified range of days ago.
     * @param daysAgoNewer The newer boundary of the range, in days ago (e.g., 0 for today, 1 for yesterday).
     * @param daysAgoOlder The older boundary of the range, in days ago (e.g., 0 for today, 7 for 7 days ago).
     *                     Must be greater than or equal to daysAgoNewer if ignoreOlderBoundary is false.
     * @param ignoreOlderBoundary If true, only the daysAgoNewer boundary is considered (sessions newer than or on this day).
     * @returns An array of IrisSession objects matching the criteria.
     */
    getSessionsBetween(daysAgoNewer: number, daysAgoOlder?: number, ignoreOlderBoundary = false): IrisSessionDTO[] {
        if (daysAgoNewer < 0 || (!ignoreOlderBoundary && (daysAgoOlder === undefined || daysAgoOlder < 0 || daysAgoNewer > daysAgoOlder))) {
            return [];
        }

        const source = this.getFilteredSessions();

        const today = new Date();
        const rangeEndDate = new Date(today);
        rangeEndDate.setDate(today.getDate() - daysAgoNewer);
        rangeEndDate.setHours(23, 59, 59, 999); // Set to the end of the 'daysAgoNewer' day

        let rangeStartDate: Date | undefined = undefined;
        if (!ignoreOlderBoundary && daysAgoOlder !== undefined) {
            rangeStartDate = new Date(today);
            rangeStartDate.setDate(today.getDate() - daysAgoOlder);
            rangeStartDate.setHours(0, 0, 0, 0); // Set to the start of the 'daysAgoOlder' day
        }

        return source.filter((session) => {
            const sessionCreationDate = new Date(session.creationDate);

            const isAfterOrOnStartDate = ignoreOlderBoundary || (rangeStartDate && sessionCreationDate.getTime() >= rangeStartDate.getTime());
            const isBeforeOrOnEndDate = sessionCreationDate.getTime() <= rangeEndDate.getTime();

            if (ignoreOlderBoundary) {
                return isBeforeOrOnEndDate;
            } else {
                return isAfterOrOnStartDate && isBeforeOrOnEndDate;
            }
        });
    }

    openNewSession() {
        this.chatService.clearChat();
    }

    setSearchValue(searchValue: string) {
        this.searchValue = searchValue.trim().toLowerCase();
    }

    private getFilteredSessions(): IrisSessionDTO[] {
        if (!this.searchValue) {
            return this.chatSessions;
        }
        return this.chatSessions.filter((s) => (s.title ?? '').toLowerCase().includes(this.searchValue));
    }

    private computeRelatedEntityRoute(currentChatMode: ChatServiceMode | undefined, currentRelatedEntityId: number | undefined): string | undefined {
        if (!currentChatMode || !currentRelatedEntityId) {
            return undefined;
        }
        switch (currentChatMode) {
            case ChatServiceMode.PROGRAMMING_EXERCISE:
                return `../exercises/${currentRelatedEntityId}`;
            case ChatServiceMode.LECTURE:
                return `../lectures/${currentRelatedEntityId}`;
            default:
                return undefined;
        }
    }

    private computeRelatedEntityLinkButtonLabel(currentChatMode: ChatServiceMode | undefined): string | undefined {
        switch (currentChatMode) {
            case ChatServiceMode.PROGRAMMING_EXERCISE:
                return `artemisApp.exerciseChatbot.goToRelatedEntityButton.exerciseLabel`;
            case ChatServiceMode.LECTURE:
                return `artemisApp.exerciseChatbot.goToRelatedEntityButton.lectureLabel`;
            default:
                return undefined;
        }
    }

    protected readonly LLMSelectionDecision = LLMSelectionDecision;
}
