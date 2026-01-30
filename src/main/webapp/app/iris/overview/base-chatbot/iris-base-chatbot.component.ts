import {
    faArrowDown,
    faCheck,
    faChevronRight,
    faCircle,
    faCircleInfo,
    faCircleNotch,
    faCompress,
    faCopy,
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
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AfterViewInit, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { IrisAssistantMessage, IrisMessage, IrisSender } from 'app/iris/shared/entities/iris-message.model';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { TranslateService } from '@ngx-translate/core';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { IrisStageDTO, IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { IrisMessageContentType, IrisTextMessageContent } from 'app/iris/shared/entities/iris-content-type.model';
import { AccountService } from 'app/core/auth/account.service';
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
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { Clipboard } from '@angular/cdk/clipboard';

@Component({
    selector: 'jhi-iris-base-chatbot',
    templateUrl: './iris-base-chatbot.component.html',
    styleUrls: ['./iris-base-chatbot.component.scss'],
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
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisBaseChatbotComponent implements AfterViewInit {
    private static readonly NEW_CHAT_TITLES = new Set(['new chat', 'neuer chat']);
    protected accountService = inject(AccountService);
    protected translateService = inject(TranslateService);
    protected statusService = inject(IrisStatusService);
    protected chatService = inject(IrisChatService);
    protected route = inject(ActivatedRoute);
    private readonly destroyRef = inject(DestroyRef);
    private readonly clipboard = inject(Clipboard);

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
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faCopy = faCopy;
    protected readonly faCheck = faCheck;

    // Types
    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly IrisMessageContentType = IrisMessageContentType;
    protected readonly IrisAssistantMessage = IrisAssistantMessage;
    protected readonly IrisTextMessageContent = IrisTextMessageContent;
    protected readonly IrisSender = IrisSender;
    protected readonly IrisErrorMessageKey = IrisErrorMessageKey;

    // Observable-derived signals (using toSignal for reactive state)
    private readonly currentRelatedEntityId = toSignal(this.chatService.currentRelatedEntityId(), { initialValue: undefined });
    private readonly currentChatMode = toSignal(this.chatService.currentChatMode(), { initialValue: undefined });
    readonly relatedEntityRoute = computed<string | undefined>(() => this.computeRelatedEntityRoute(this.currentChatMode(), this.currentRelatedEntityId()));
    readonly relatedEntityLinkButtonLabel = computed<string | undefined>(() => this.computeRelatedEntityLinkButtonLabel(this.currentChatMode()));

    readonly currentSessionId = toSignal(this.chatService.currentSessionId(), { initialValue: undefined });
    readonly chatSessions = toSignal(this.chatService.availableChatSessions(), { initialValue: [] as IrisSessionDTO[] });
    readonly stages = toSignal(this.chatService.currentStages(), { initialValue: [] as IrisStageDTO[] });
    readonly suggestions = toSignal(this.chatService.currentSuggestions(), { initialValue: [] as string[] });
    readonly error = toSignal(this.chatService.currentError(), { initialValue: undefined });
    readonly numNewMessages = toSignal(this.chatService.currentNumNewMessages(), { initialValue: 0 });
    readonly rateLimitInfo = toSignal(this.statusService.currentRatelimitInfo(), { requireSync: true });
    readonly active = toSignal(this.statusService.getActiveStatus(), { initialValue: true });

    // Messages with processing
    private readonly rawMessages = toSignal(this.chatService.currentMessages(), { initialValue: [] as IrisMessage[] });
    readonly messages = computed(() => this.processMessages(this.rawMessages()));

    // Computed state
    readonly hasActiveStage = computed(() => this.stages()?.some((stage) => [IrisStageStateDTO.IN_PROGRESS, IrisStageStateDTO.NOT_STARTED].includes(stage.state)) ?? false);
    readonly isEmptyState = computed(() => !this.messages()?.length && !this.isEmbeddedChat());
    readonly hasHeaderContent = computed(() => {
        const hasRelatedEntity = !!this.relatedEntityRoute() && !!this.relatedEntityLinkButtonLabel() && this.isChatHistoryAvailable();
        const rateLimit = this.rateLimitInfo()?.rateLimit ?? 0;
        const hasRateLimitInfo = rateLimit > 0;
        const hasClearButton = !this.isChatHistoryAvailable() && this.messages().length >= 1;
        const hasSizeToggle = this.fullSize() !== undefined;
        const hasCloseButton = this.showCloseButton();
        return hasRelatedEntity || hasRateLimitInfo || hasClearButton || hasSizeToggle || hasCloseButton;
    });

    // UI state signals
    readonly newMessageTextContent = signal('');
    readonly isLoading = signal(false);
    readonly isChatHistoryOpen = signal(true);
    readonly searchValue = signal('');
    readonly filteredSessions = computed(() => {
        const search = this.searchValue();
        const sessions = this.chatSessions();
        if (!search) {
            return sessions;
        }
        return sessions.filter((session) => (session.title ?? '').toLowerCase().includes(search));
    });
    readonly newChatSessions = computed(() => {
        const sessions = this.filteredSessions().filter((session) => this.isNewChatSession(session));
        if (sessions.length === 0) {
            return [];
        }
        const newestNewChat = sessions.reduce((latest, current) => (new Date(current.creationDate).getTime() > new Date(latest.creationDate).getTime() ? current : latest));
        return [newestNewChat];
    });
    readonly filteredNonNewSessions = computed(() => this.filteredSessions().filter((session) => !this.isNewChatSession(session)));
    readonly todaySessions = computed(() => this.filterSessionsBetween(this.filteredNonNewSessions(), 0, 0));
    readonly yesterdaySessions = computed(() => this.filterSessionsBetween(this.filteredNonNewSessions(), 1, 1));
    readonly last7DaysSessions = computed(() => this.filterSessionsBetween(this.filteredNonNewSessions(), 2, 6));
    readonly last30DaysSessions = computed(() => this.filterSessionsBetween(this.filteredNonNewSessions(), 7, 29));
    readonly olderSessions = computed(() => this.filterSessionsBetween(this.filteredNonNewSessions(), 30, undefined, true));
    readonly userAccepted = signal(false);
    readonly isScrolledToBottom = signal(true);
    readonly resendAnimationActive = signal(false);
    readonly clickedSuggestion = signal<string | undefined>(undefined);

    // Animation state (internal tracking)
    private shouldAnimate = false;
    readonly animatingMessageIds = signal(new Set<number>());
    private previousSessionId: number | undefined;
    private previousMessageCount = 0;
    private previousMessageIds = new Set<number>();
    private copyResetTimeoutId: ReturnType<typeof setTimeout> | undefined;
    public ButtonType = ButtonType;
    readonly copiedMessageKey = signal<number | undefined>(undefined);

    showDeclineButton = input<boolean>(true);
    isChatHistoryAvailable = input<boolean>(false);
    isEmbeddedChat = input<boolean>(false);
    readonly fullSize = input<boolean>();
    readonly showCloseButton = input<boolean>(false);
    readonly isChatGptWrapper = input<boolean>(false);
    readonly fullSizeToggle = output<void>();
    readonly closeClicked = output<void>();

    // ViewChilds
    readonly messagesElement = viewChild<ElementRef>('messagesElement');
    readonly scrollArrow = viewChild<ElementRef>('scrollArrow');
    readonly messageTextarea = viewChild<ElementRef<HTMLTextAreaElement>>('messageTextarea');
    readonly acceptButton = viewChild<ElementRef<HTMLButtonElement>>('acceptButton');

    constructor() {
        // Initialize user acceptance state
        this.userAccepted.set(!!this.accountService.userIdentity()?.externalLLMUsageAccepted);

        // Handle route query params (irisQuestion)
        this.route.queryParams?.pipe(takeUntilDestroyed()).subscribe((params: any) => {
            if (params?.irisQuestion) {
                this.newMessageTextContent.set(params.irisQuestion);
            }
        });

        // Handle session changes - reset animations
        effect((onCleanup) => {
            const sessionId = this.currentSessionId();
            if (this.previousSessionId !== sessionId) {
                this.animatingMessageIds.set(new Set<number>());
                this.shouldAnimate = false;
                const timeoutId = setTimeout(() => (this.shouldAnimate = true));
                onCleanup(() => clearTimeout(timeoutId));
            }
            this.previousSessionId = sessionId;
        });

        // Handle message scroll on new messages
        effect((onCleanup) => {
            const rawMessages = this.rawMessages();
            if (rawMessages.length !== this.previousMessageCount) {
                this.scrollToBottom('auto');
                const timeoutId = setTimeout(() => this.messageTextarea()?.nativeElement?.focus(), 10);
                onCleanup(() => clearTimeout(timeoutId));
            }
            // Track new messages for animation (compare against previous IDs, not current)
            if (this.shouldAnimate) {
                // Use untracked to read current value without creating a dependency
                // (otherwise updating animatingMessageIds would retrigger this effect infinitely)
                const newAnimatingIds = new Set(untracked(() => this.animatingMessageIds()));
                rawMessages.forEach((m) => {
                    if (m.id && !this.previousMessageIds.has(m.id)) {
                        newAnimatingIds.add(m.id);
                    }
                });
                this.animatingMessageIds.set(newAnimatingIds);
            }
            this.previousMessageIds = new Set(rawMessages.map((m) => m.id).filter((id): id is number => id !== undefined));
            this.previousMessageCount = rawMessages.length;
        });

        // Handle new message scroll
        effect(() => {
            const num = this.numNewMessages();
            if (num > 0) {
                this.scrollToBottom('smooth');
            }
        });

        // Handle active status changes
        effect(() => {
            const activeValue = this.active();
            if (!activeValue) {
                this.isLoading.set(false);
                this.resendAnimationActive.set(false);
            }
        });

        // Reset clicked suggestion when new suggestions arrive (read without using value to establish dependency)
        effect(() => {
            this.suggestions();
            this.clickedSuggestion.set(undefined);
        });

        // Focus on message textarea after initialization
        const focusTimeoutId = setTimeout(() => {
            const textarea = this.messageTextarea();
            const acceptBtn = this.acceptButton();
            if (textarea) {
                textarea.nativeElement.focus();
            } else if (acceptBtn) {
                acceptBtn.nativeElement.focus();
            }
        }, 150);
        this.destroyRef.onDestroy(() => clearTimeout(focusTimeoutId));
        this.destroyRef.onDestroy(() => {
            if (this.copyResetTimeoutId) {
                clearTimeout(this.copyResetTimeoutId);
            }
        });
    }

    /**
     * Process messages for display (clone, reverse, format)
     */
    private processMessages(rawMessages: IrisMessage[]): IrisMessage[] {
        const hasUserMessage = rawMessages.some((message) => message.sender === IrisSender.USER);
        if (!hasUserMessage && rawMessages.length > 0 && rawMessages.every((message) => message.sender === IrisSender.LLM)) {
            return [];
        }
        const processed = _.cloneDeep(rawMessages).reverse();
        processed.forEach((message) => {
            if (message.content?.[0] && 'textContent' in message.content[0]) {
                const cnt = message.content[0] as IrisTextMessageContent;
                cnt.textContent = cnt.textContent.replace(/\n\n/g, '\n\u00A0\n');
                cnt.textContent = cnt.textContent.replace(/\n/g, '\n\n');
            }
        });
        return processed;
    }

    ngAfterViewInit() {
        // Enable animations after initial messages have loaded
        // Delay ensures initial message batch doesn't trigger animations
        setTimeout(() => (this.shouldAnimate = true), 500);
    }

    /**
     * Handles the send button click event and sends the user's message.
     */
    onSend(): void {
        this.chatService.messagesRead();
        const content = this.newMessageTextContent().trim();
        if (content) {
            this.isLoading.set(true);
            this.chatService
                .sendMessage(content)
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe(() => {
                    this.isLoading.set(false);
                });
            this.newMessageTextContent.set('');
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
            this.resendAnimationActive.set(true);
        } else if (message.content?.[0]?.textContent) {
            observable = this.chatService.sendMessage(message.content[0].textContent);
        } else {
            this.resendAnimationActive.set(false);
            return;
        }
        this.isLoading.set(true);

        observable.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.resendAnimationActive.set(false);
            this.isLoading.set(false);
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
        this.chatService.rateMessage(message, helpful).pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
    }

    copyMessage(message: IrisMessage, messageIndex?: number) {
        const text = this.getCopyText(message);
        if (!text) {
            return;
        }
        const key = this.getMessageKey(message, messageIndex);
        this.setCopiedKey(key);
        if (navigator.clipboard?.writeText) {
            navigator.clipboard.writeText(text).catch(() => this.fallbackCopy(text, key));
            return;
        }
        this.fallbackCopy(text, key);
    }

    private fallbackCopy(text: string, key: number | undefined) {
        this.clipboard.copy(text);
        this.setCopiedKey(key);
    }

    private getCopyText(message: IrisMessage): string {
        if (!message.content?.length) {
            return '';
        }
        const parts = message.content
            .filter((content) => content.type === IrisMessageContentType.TEXT)
            .map((content) => (content as IrisTextMessageContent).textContent ?? '')
            .filter((value) => value);
        return parts.join('\n\n');
    }

    isCopied(message: IrisMessage, messageIndex?: number): boolean {
        return this.copiedMessageKey() === this.getMessageKey(message, messageIndex);
    }

    private getMessageKey(message: IrisMessage, messageIndex?: number): number | undefined {
        return message.id ?? messageIndex;
    }

    private setCopiedKey(key: number | undefined) {
        this.copiedMessageKey.set(key);
        if (this.copyResetTimeoutId) {
            clearTimeout(this.copyResetTimeoutId);
        }
        this.copyResetTimeoutId = setTimeout(() => {
            this.copiedMessageKey.set(undefined);
        }, 1500);
    }

    /**
     * Scrolls the chat body to the bottom.
     * @param behavior - The scroll behavior.
     */
    scrollToBottom(behavior: ScrollBehavior) {
        setTimeout(() => {
            const messagesElement: HTMLElement = this.messagesElement()?.nativeElement;
            messagesElement?.scrollTo({
                top: 0,
                behavior: behavior,
            });
        });
    }

    /**
     * Accepts the permission to use the chat widget.
     */
    acceptPermission() {
        this.chatService.updateExternalLLMUsageConsent(true);
        this.userAccepted.set(true);
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
            if (!this.isLoading() && this.active()) {
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
        const textareaRef = this.messageTextarea();
        if (!textareaRef) return;
        const textarea: HTMLTextAreaElement = textareaRef.nativeElement;
        textarea.style.height = 'auto'; // Reset the height to auto
        if (!textarea.value.trim()) {
            textarea.style.height = '';
            this.adjustScrollButtonPosition(1);
            return;
        }
        const bufferForSpaceBetweenLines = 4;
        const lineHeight = parseInt(getComputedStyle(textarea).lineHeight, 10) + bufferForSpaceBetweenLines;
        const maxHeight = 200;
        const newHeight = Math.min(textarea.scrollHeight, maxHeight);
        textarea.style.height = `${newHeight}px`;

        this.adjustScrollButtonPosition(newHeight / lineHeight);
    }

    /**
     * Adjusts the position of the scroll button based on the number of rows in the message textarea.
     * @param newRows - The new number of rows.
     */
    adjustScrollButtonPosition(newRows: number) {
        const textareaRef = this.messageTextarea();
        const scrollArrowRef = this.scrollArrow();
        if (!textareaRef || !scrollArrowRef) return;
        const textarea: HTMLTextAreaElement = textareaRef.nativeElement;
        const scrollArrow: HTMLElement = scrollArrowRef.nativeElement;
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
        const textareaRef = this.messageTextarea();
        const scrollArrowRef = this.scrollArrow();
        if (!textareaRef || !scrollArrowRef) return;
        const textarea: HTMLTextAreaElement = textareaRef.nativeElement;
        const scrollArrow: HTMLElement = scrollArrowRef.nativeElement;
        textarea.rows = 1;
        textarea.style.height = '';
        scrollArrow.style.bottom = '';
    }

    checkChatScroll() {
        const messagesElement = this.messagesElement()?.nativeElement;
        if (!messagesElement) return;
        const scrollTop = messagesElement.scrollTop;
        this.isScrolledToBottom.set(scrollTop < 50);
    }

    onSuggestionClick(suggestion: string) {
        this.clickedSuggestion.set(suggestion);
        this.newMessageTextContent.set(suggestion);
        this.onSend();
    }

    onSessionClick(session: IrisSessionDTO) {
        if (this.isNewChatSession(session)) {
            this.openNewSession();
            return;
        }
        this.chatService.switchToSession(session);
    }

    setChatHistoryVisibility(isOpen: boolean) {
        this.isChatHistoryOpen.set(isOpen);
        if (!isOpen) {
            this.setSearchValue('');
        }
    }

    private isNewChatSession(session: IrisSessionDTO): boolean {
        const title = session.title?.trim().toLowerCase();
        return title !== undefined && IrisBaseChatbotComponent.NEW_CHAT_TITLES.has(title);
    }

    /**
     * Retrieves chat sessions that occurred between a specified range of days ago.
     * @param daysAgoNewer The newer boundary of the range, in days ago (e.g., 0 for today, 1 for yesterday).
     * @param daysAgoOlder The older boundary of the range, in days ago (e.g., 0 for today, 7 for 7 days ago).
     *                     Must be greater than or equal to daysAgoNewer if ignoreOlderBoundary is false.
     * @param ignoreOlderBoundary If true, only the daysAgoNewer boundary is considered (sessions newer than or on this day).
     * @returns An array of IrisSession objects matching the criteria.
     */
    private filterSessionsBetween(sessions: IrisSessionDTO[], daysAgoNewer: number, daysAgoOlder?: number, ignoreOlderBoundary = false): IrisSessionDTO[] {
        if (daysAgoNewer < 0 || (!ignoreOlderBoundary && (daysAgoOlder === undefined || daysAgoOlder < 0 || daysAgoNewer > daysAgoOlder))) {
            return [];
        }

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

        return sessions.filter((session) => {
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
        this.searchValue.set(searchValue.trim().toLowerCase());
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
}
