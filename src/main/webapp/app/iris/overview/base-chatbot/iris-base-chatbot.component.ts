import {
    faArrowDown,
    faArrowsRotate,
    faCheck,
    faChevronDown,
    faCircleInfo,
    faCircleNotch,
    faCompress,
    faCopy,
    faExpand,
    faLink,
    faMagnifyingGlass,
    faPaperPlane,
    faPenToSquare,
    faThumbsDown,
    faThumbsUp,
    faXmark,
} from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/shared/service/alert.service';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import {
    AfterViewInit,
    ChangeDetectionStrategy,
    Component,
    DestroyRef,
    ElementRef,
    HostListener,
    computed,
    effect,
    inject,
    input,
    output,
    signal,
    untracked,
    viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Clipboard } from '@angular/cdk/clipboard';
import { IrisAssistantMessage, IrisMessage, IrisSender } from 'app/iris/shared/entities/iris-message.model';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { TranslateService } from '@ngx-translate/core';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { IrisStageDTO, IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import {
    IrisMessageContent,
    IrisMessageContentType,
    IrisTextMessageContent,
    McqData,
    McqResponseData,
    McqSetData,
    getMcqData,
    getMcqSetData,
    isMcqContent,
    isMcqSetContent,
} from 'app/iris/shared/entities/iris-content-type.model';
import { IrisMcqQuestionComponent } from 'app/iris/overview/mcq-question/iris-mcq-question.component';
import { IrisMcqCarouselComponent } from 'app/iris/overview/mcq-question/iris-mcq-carousel.component';
import { AccountService } from 'app/core/auth/account.service';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import * as _ from 'lodash-es';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
import { IrisCitationTextComponent } from 'app/iris/overview/citation-text/iris-citation-text.component';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AsPipe } from 'app/shared/pipes/as.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ChatHistoryItemComponent } from './chat-history-item/chat-history-item.component';
import { formatDate } from '@angular/common';
import { MenuModule } from 'primeng/menu';
import { MenuItem } from 'primeng/api';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';
import { ChatStatusBarComponent } from 'app/iris/overview/base-chatbot/chat-status-bar/chat-status-bar.component';
import { IrisThinkingBubbleComponent } from 'app/iris/overview/base-chatbot/iris-thinking-bubble/iris-thinking-bubble.component';
import { AboutIrisModalComponent } from 'app/iris/overview/about-iris-modal/about-iris-modal.component';
import { IrisChatMemoriesIndicatorComponent } from 'app/iris/overview/base-chatbot/memories-indicator/iris-chat-memories-indicator.component';
import { MemirisMemory } from 'app/iris/shared/entities/memiris.model';
import { EXERCISE_PLACEHOLDER_LABEL_KEYS, LECTURE_PLACEHOLDER_LABEL_KEYS } from './iris-chatbot-placeholder-labels';
import { createActiveSuggestionChips } from './iris-chatbot-suggestion-chips';
import { ContextSelectionComponent } from 'app/iris/overview/context-selection/context-selection.component';

// Session history time bucket boundaries (in days ago)
const YESTERDAY_OFFSET = 1;
const LAST_7_DAYS_START = 2;
const LAST_7_DAYS_END = 6;
const LAST_30_DAYS_START = 7;
const LAST_30_DAYS_END = 29;
const OLDER_SESSIONS_START = 30;

// Interval (in ms) to check if the date has changed for session bucket recalculation
const DAY_CHANGE_CHECK_INTERVAL_MS = 60000;

// Duration (in ms) to show the "copied" feedback before resetting
const COPY_FEEDBACK_DURATION_MS = 1500;

// Interval (in ms) between placeholder label cycling
const PLACEHOLDER_CYCLE_INTERVAL_MS = 5000;
const PLACEHOLDER_FADE_DURATION_MS = 300;

@Component({
    selector: 'jhi-iris-base-chatbot',
    templateUrl: './iris-base-chatbot.component.html',
    styleUrls: ['./iris-base-chatbot.component.scss'],
    host: {
        '[class.layout-client]': "layout() === 'client'",
        '[class.layout-widget]': "layout() === 'widget'",
        '[class.layout-embedded]': "layout() === 'embedded'",
    },
    imports: [
        IrisLogoComponent,
        RouterLink,
        FaIconComponent,
        TooltipModule,
        TranslateDirective,
        ChatStatusBarComponent,
        FormsModule,
        ButtonComponent,
        ArtemisTranslatePipe,
        AsPipe,
        HtmlForMarkdownPipe,
        ChatHistoryItemComponent,
        SearchFilterComponent,
        IrisCitationTextComponent,
        IrisMcqQuestionComponent,
        IrisMcqCarouselComponent,
        IrisChatMemoriesIndicatorComponent,
        IrisThinkingBubbleComponent,
        ConfirmDialogModule,
        MenuModule,
        ContextSelectionComponent,
    ],
    providers: [ConfirmationService],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisBaseChatbotComponent implements AfterViewInit {
    protected accountService = inject(AccountService);
    protected translateService = inject(TranslateService);
    private readonly dialogService = inject(DialogService);
    private readonly matDialog = inject(MatDialog);
    private aboutIrisDialogRef: DynamicDialogRef<AboutIrisModalComponent> | undefined;
    private aboutIrisMatDialogRef: MatDialogRef<AboutIrisModalComponent> | undefined;
    private readonly alertService = inject(AlertService);
    private readonly confirmationService = inject(ConfirmationService);

    // Known "new chat" titles from all languages (server-side: messages*.properties, client-side: iris.json).
    // Must match the values in src/main/resources/i18n/messages*.properties (iris.chat.session.newChatTitle)
    // and src/main/webapp/i18n/*/iris.json (artemisApp.iris.chatHistory.newChat).
    private static readonly NEW_CHAT_TITLES = new Set(['new chat', 'neuer chat']);
    protected statusService = inject(IrisStatusService);
    protected chatService = inject(IrisChatService);
    protected route = inject(ActivatedRoute);
    protected llmModalService = inject(LLMSelectionModalService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly clipboard = inject(Clipboard);
    private readonly irisChatHttpService = inject(IrisChatHttpService);

    // Icons
    protected readonly faPaperPlane = faPaperPlane;
    protected readonly faExpand = faExpand;
    protected readonly faXmark = faXmark;
    protected readonly faArrowDown = faArrowDown;
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faCompress = faCompress;
    protected readonly faThumbsUp = faThumbsUp;
    protected readonly faThumbsDown = faThumbsDown;
    protected readonly faPenToSquare = faPenToSquare;
    protected readonly faLink = faLink;
    protected readonly faMagnifyingGlass = faMagnifyingGlass;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faCopy = faCopy;
    protected readonly faCheck = faCheck;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faArrowsRotate = faArrowsRotate;

    // Types
    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly IrisMessageContentType = IrisMessageContentType;
    protected readonly IrisAssistantMessage = IrisAssistantMessage;
    protected readonly IrisTextMessageContent = IrisTextMessageContent;
    protected readonly IrisSender = IrisSender;
    protected readonly IrisErrorMessageKey = IrisErrorMessageKey;
    protected readonly LLMSelectionDecision = LLMSelectionDecision;

    // MCQ helpers
    protected readonly isMcqContent = isMcqContent;
    protected readonly isMcqSetContent = isMcqSetContent;
    protected readonly getMcqData = (content: IrisMessageContent): McqData | undefined => getMcqData(content);
    protected readonly getMcqSetData = (content: IrisMessageContent): McqSetData | undefined => getMcqSetData(content);
    protected messageHasMcq(message: IrisMessage): boolean {
        return message.content?.some((c) => isMcqContent(c) || isMcqSetContent(c)) ?? false;
    }

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
    readonly citationInfo = toSignal(this.chatService.currentCitationInfo(), { initialValue: [] as IrisCitationMetaDTO[] });

    // Messages with processing
    private readonly rawMessages = toSignal(this.chatService.currentMessages(), { initialValue: [] as IrisMessage[] });
    readonly messages = computed(() => this.processMessages(this.rawMessages()));

    // Computed state
    readonly hasActiveStage = computed(() => this.stages()?.some((stage) => [IrisStageStateDTO.IN_PROGRESS, IrisStageStateDTO.NOT_STARTED].includes(stage.state)) ?? false);
    readonly shouldShowStatusBar = computed(
        () => this.stages()?.some((stage) => !stage.internal && ![IrisStageStateDTO.DONE, IrisStageStateDTO.SKIPPED].includes(stage.state)) ?? false,
    );
    readonly activeChatMessage = computed(() => {
        const stages = this.stages();
        if (!stages) return undefined;
        const active = stages.find((s) => s.state === IrisStageStateDTO.IN_PROGRESS && s.chatMessage);
        return active?.chatMessage;
    });
    readonly isEmptyState = computed(() => !this.messages()?.length && !this.isEmbeddedChat());
    readonly hasCurrentSessionContent = computed(() => (this.messages()?.length ?? 0) > 0);
    readonly hasSessionSwitcher = computed(
        () => (this.layout() === 'widget' || this.layout() === 'embedded') && this.showWidgetHeader() && (this.hasCurrentSessionContent() || this.hasPastSessions()),
    );
    readonly hasHeaderContent = computed(() => {
        const hasRelatedEntity = !!this.relatedEntityRoute() && !!this.relatedEntityLinkButtonLabel() && this.isChatHistoryAvailable();
        const rateLimit = this.rateLimitInfo()?.rateLimit ?? 0;
        const hasRateLimitInfo = rateLimit > 0;
        const hasAboutIrisButton = !this.isChatHistoryAvailable() && !this.isChatGptWrapper();
        const hasClearButton = !this.isChatHistoryAvailable() && this.messages().length >= 1;
        const hasSizeToggle = this.fullSize() !== undefined;
        const hasCloseButton = this.showCloseButton();
        const hasSessionSwitcher = this.hasSessionSwitcher();
        return hasRelatedEntity || hasRateLimitInfo || hasAboutIrisButton || hasClearButton || hasSizeToggle || hasCloseButton || hasSessionSwitcher;
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
    readonly filteredNonNewSessions = computed(() => this.filteredSessions().filter((session) => !this.isNewChatSession(session)));
    readonly todaySessions = computed(() => this.filterSessionsBetween(this.filteredNonNewSessions(), 0, 0, false, this.dayTick()));
    readonly yesterdaySessions = computed(() => this.filterSessionsBetween(this.filteredNonNewSessions(), YESTERDAY_OFFSET, YESTERDAY_OFFSET, false, this.dayTick()));
    readonly last7DaysSessions = computed(() => this.filterSessionsBetween(this.filteredNonNewSessions(), LAST_7_DAYS_START, LAST_7_DAYS_END, false, this.dayTick()));
    readonly last30DaysSessions = computed(() => this.filterSessionsBetween(this.filteredNonNewSessions(), LAST_30_DAYS_START, LAST_30_DAYS_END, false, this.dayTick()));
    readonly olderSessions = computed(() => this.filterSessionsBetween(this.filteredNonNewSessions(), OLDER_SESSIONS_START, undefined, true, this.dayTick()));

    // Daily tick signal for reactive date-based session buckets
    readonly dayTick = signal(new Date().toDateString());
    private dayTickIntervalId: ReturnType<typeof setInterval> | undefined;

    readonly userAccepted = signal<LLMSelectionDecision | undefined>(undefined);
    readonly isAIEnabled = computed(() => {
        const decision = this.userAccepted();
        return decision === LLMSelectionDecision.CLOUD_AI || decision === LLMSelectionDecision.LOCAL_AI;
    });
    readonly isInputDisabled = computed(
        () =>
            this.isLoading() ||
            !this.active() ||
            !!(this.rateLimitInfo()?.rateLimit && this.rateLimitInfo()!.currentMessageCount === this.rateLimitInfo()!.rateLimit) ||
            this.hasActiveStage(),
    );
    readonly isSendDisabled = computed(() => !this.newMessageTextContent().trim() || this.isInputDisabled());
    readonly canShowSuggestions = computed(
        () =>
            !!this.suggestions()?.length &&
            this.isAIEnabled() &&
            this.active() &&
            (!this.rateLimitInfo()?.rateLimit || this.rateLimitInfo()!.currentMessageCount !== this.rateLimitInfo()!.rateLimit) &&
            !this.hasActiveStage(),
    );
    readonly isScrolledToBottom = signal(true);
    readonly resendAnimationActive = signal(false);
    readonly clickedSuggestion = signal<string | undefined>(undefined);
    private readonly isSuggestionAnimating = signal(false);

    // Animation state (internal tracking)
    private shouldAnimate = false;
    readonly animatingMessageIds = signal(new Set<number>());
    private previousSessionId: number | undefined;
    private previousMessageCount = 0;
    private previousMessageIds = new Set<number>();
    private copyResetTimeoutId: ReturnType<typeof setTimeout> | undefined;
    protected readonly ButtonType = ButtonType;
    readonly copiedMessageKey = signal<number | undefined>(undefined);

    protected readonly activeSuggestionChips = createActiveSuggestionChips(this.currentChatMode);

    readonly chipPreviewText = signal('');
    private readonly isChipTextApplied = signal(false);

    readonly newChatTitle = computed(() => this.translateService.instant('artemisApp.iris.chatHistory.newChat'));

    showDeclineButton = input<boolean>(true);
    isChatHistoryAvailable = input<boolean>(false);
    isEmbeddedChat = input<boolean>(false);
    readonly fullSize = input<boolean>();
    readonly showCloseButton = input<boolean>(false);
    readonly isChatGptWrapper = input<boolean>(false);
    readonly layout = input<'client' | 'widget' | 'embedded'>('client');
    readonly fullSizeToggle = output<void>();
    readonly closeClicked = output<void>();

    // ViewChilds
    readonly messagesElement = viewChild<ElementRef>('messagesElement');
    readonly messageTextarea = viewChild<ElementRef<HTMLTextAreaElement>>('messageTextarea');
    readonly acceptButton = viewChild<ElementRef<HTMLButtonElement>>('acceptButton');

    // Session switcher (widget layout)
    readonly sessionMenuOpen = signal(false);
    readonly sessionMenuItems = computed(() => {
        const currentId = this.currentSessionId();
        const newChatLabel = this.newChatTitle();
        const items: MenuItem[] = [];

        const addGroup = (label: string, groupSessions: IrisSessionDTO[]) => {
            if (groupSessions.length === 0) {
                return;
            }
            items.push({
                label,
                disabled: true,
                styleClass: 'session-menu-group-label',
            });
            for (const session of groupSessions) {
                const isActive = session.id === currentId;
                items.push({
                    label: this.getSessionMenuLabel(session, newChatLabel),
                    styleClass: isActive ? 'session-menu-item-active' : undefined,
                    data: { isActive },
                    command: () => {
                        this.onSessionClick(session);
                        this.onSessionMenuHide();
                    },
                });
            }
        };

        addGroup(this.translateService.instant('artemisApp.iris.chatHistory.today'), this.todaySessions());
        addGroup(this.translateService.instant('artemisApp.iris.chatHistory.yesterday'), this.yesterdaySessions());
        addGroup(this.translateService.instant('artemisApp.iris.chatHistory.last7Days'), this.last7DaysSessions());
        addGroup(this.translateService.instant('artemisApp.iris.chatHistory.last30Days'), this.last30DaysSessions());
        addGroup(this.translateService.instant('artemisApp.iris.chatHistory.older'), this.olderSessions());

        return items;
    });
    readonly currentSessionTitle = computed(() => {
        const currentId = this.currentSessionId();
        const sessions = this.chatSessions();
        if (currentId === undefined) {
            return this.newChatTitle() || '';
        }
        const session = sessions.find((s) => s.id === currentId);
        if (!session || !session.title || this.isNewChatSession(session)) {
            return this.newChatTitle() || '';
        }
        return session.title;
    });
    readonly hasPastSessions = computed(() => {
        const currentId = this.currentSessionId();
        return this.chatSessions().some((s) => s.id !== currentId && this.isSessionRelatedToCurrentContext(s));
    });

    readonly showWidgetHeader = computed(() => {
        if (this.layout() !== 'widget' && this.layout() !== 'embedded') {
            return true;
        }
        return !this.isEmptyState() || this.hasPastSessions();
    });

    // Placeholder cycling and ghost text state
    readonly isExerciseOrLectureMode = computed(() => {
        const mode = this.currentChatMode();
        return mode === ChatServiceMode.PROGRAMMING_EXERCISE || mode === ChatServiceMode.TEXT_EXERCISE || mode === ChatServiceMode.LECTURE;
    });

    private readonly isExerciseMode = computed(() => {
        const mode = this.currentChatMode();
        return mode === ChatServiceMode.PROGRAMMING_EXERCISE || mode === ChatServiceMode.TEXT_EXERCISE;
    });

    readonly interpolatedLabels = signal<string[]>([]);

    readonly placeholderIndex = signal(0);
    readonly placeholderVisible = signal(true);
    readonly isFocused = signal(false);
    private cycleIntervalId: ReturnType<typeof setInterval> | undefined;
    private cycleFadeTimeoutId: ReturnType<typeof setTimeout> | undefined;

    readonly currentPlaceholder = computed(() => {
        const labels = this.interpolatedLabels();
        if (!labels.length) return '';
        return labels[this.placeholderIndex() % labels.length];
    });

    readonly shouldUseRotatingPlaceholder = computed(() => this.isExerciseOrLectureMode() && !this.messages().length && this.layout() !== 'client');

    readonly ghostText = computed(() => {
        const input = this.newMessageTextContent();
        const labels = this.interpolatedLabels();
        if (!input || !labels.length || !this.isExerciseOrLectureMode()) {
            return '';
        }
        const inputLower = input.toLowerCase();
        const match = labels.find((label) => label.toLowerCase().startsWith(inputLower));
        return match ? match.slice(input.length) : '';
    });

    readonly textareaPlaceholder = computed(() => {
        if (this.chipPreviewText()) return '';
        if (this.shouldUseRotatingPlaceholder() && !this.isInputDisabled() && this.currentPlaceholder()) {
            return this.currentPlaceholder();
        }
        return this.translateService.instant('artemisApp.exerciseChatbot.inputMessage');
    });

    protected getAccessedMemories(message: IrisMessage): MemirisMemory[] {
        return message.accessedMemories ?? [];
    }

    protected getCreatedMemories(message: IrisMessage): MemirisMemory[] {
        return message.createdMemories ?? [];
    }

    protected hasMemories(message: IrisMessage): boolean {
        return this.getAccessedMemories(message).length > 0 || this.getCreatedMemories(message).length > 0;
    }

    private startCycling(): void {
        if (this.cycleIntervalId) return;
        this.cycleIntervalId = setInterval(() => {
            this.placeholderVisible.set(false);
            this.cycleFadeTimeoutId = setTimeout(() => {
                const labels = this.interpolatedLabels();
                if (labels.length) {
                    this.placeholderIndex.update((i) => (i + 1) % labels.length);
                }
                this.placeholderVisible.set(true);
            }, PLACEHOLDER_FADE_DURATION_MS);
        }, PLACEHOLDER_CYCLE_INTERVAL_MS);
    }

    private stopCycling(): void {
        if (this.cycleIntervalId) {
            clearInterval(this.cycleIntervalId);
            this.cycleIntervalId = undefined;
        }
        if (this.cycleFadeTimeoutId) {
            clearTimeout(this.cycleFadeTimeoutId);
            this.cycleFadeTimeoutId = undefined;
        }
        this.placeholderVisible.set(true);
    }

    onTextareaFocus(): void {
        this.isFocused.set(true);
        this.stopCycling();
    }

    onTextareaBlur(): void {
        this.isFocused.set(false);
        if (!this.newMessageTextContent() && this.shouldUseRotatingPlaceholder()) {
            this.startCycling();
        }
    }

    constructor() {
        // Initialize user acceptance state
        this.checkIfUserAcceptedLLMUsage();

        // Show AI selection modal if user hasn't accepted
        if (!this.userAccepted()) {
            setTimeout(() => this.showAISelectionModal(), 0);
        } else {
            this.focusInputAfterAcceptance();
        }

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
                this.isScrolledToBottom.set(true);
                const timeoutId = setTimeout(() => (this.shouldAnimate = true));
                onCleanup(() => clearTimeout(timeoutId));
            }
            this.previousSessionId = sessionId;
        });

        // Handle message scroll on new messages
        effect((onCleanup) => {
            const rawMessages = this.rawMessages();
            if (rawMessages.length !== this.previousMessageCount) {
                if (this.isScrolledToBottom() && !this.isSuggestionAnimating()) {
                    this.scrollToBottom('smooth');
                }
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

        // Handle active status changes
        effect(() => {
            const activeValue = this.active();
            if (!activeValue) {
                this.isLoading.set(false);
                this.resendAnimationActive.set(false);
            }
        });

        // Scroll when thinking bubble appears, only if user is already at the bottom
        effect(() => {
            if (this.activeChatMessage() && this.isScrolledToBottom()) {
                this.scrollToBottom('smooth');
            }
        });

        // Reset clicked suggestion when new suggestions arrive and scroll to show them
        effect(() => {
            this.suggestions();
            this.clickedSuggestion.set(undefined);
        });

        // Suppress auto-scroll during suggestion animation window
        effect((onCleanup) => {
            if (this.canShowSuggestions()) {
                this.isSuggestionAnimating.set(true);
                const timeoutId = setTimeout(() => this.isSuggestionAnimating.set(false), 1300);
                onCleanup(() => clearTimeout(timeoutId));
            }
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

        // Set up interval to detect date changes and refresh date-based session buckets
        this.dayTickIntervalId = setInterval(() => {
            const currentDay = new Date().toDateString();
            if (this.dayTick() !== currentDay) {
                this.dayTick.set(currentDay);
            }
        }, DAY_CHANGE_CHECK_INTERVAL_MS);
        this.destroyRef.onDestroy(() => {
            if (this.dayTickIntervalId) {
                clearInterval(this.dayTickIntervalId);
            }
        });
        this.destroyRef.onDestroy(() => {
            this.aboutIrisDialogRef?.close();
            this.aboutIrisMatDialogRef?.close();
        });

        // Placeholder cycling lifecycle
        effect((onCleanup) => {
            const shouldCycle = this.shouldUseRotatingPlaceholder() && !this.newMessageTextContent() && !this.isFocused() && !this.isInputDisabled();
            if (shouldCycle) {
                this.startCycling();
            } else {
                this.stopCycling();
            }
            onCleanup(() => this.stopCycling());
        });
        this.destroyRef.onDestroy(() => this.stopCycling());

        // Reset chip applied state when textarea is cleared
        effect(() => {
            if (!this.newMessageTextContent()) {
                untracked(() => this.isChipTextApplied.set(false));
            }
        });

        // Shuffle labels once when chat mode or entity changes (not on every computed read)
        effect(() => {
            const mode = this.currentChatMode();
            let keys: readonly string[];
            if (this.isExerciseMode()) {
                keys = EXERCISE_PLACEHOLDER_LABEL_KEYS;
            } else if (mode === ChatServiceMode.LECTURE) {
                keys = LECTURE_PLACEHOLDER_LABEL_KEYS;
            } else {
                untracked(() => this.interpolatedLabels.set([]));
                return;
            }
            const labels = keys.map((key) => this.translateService.instant(key));
            // Fisher-Yates shuffle for random display order
            for (let i = labels.length - 1; i > 0; i--) {
                const j = Math.floor(Math.random() * (i + 1));
                [labels[i], labels[j]] = [labels[j], labels[i]];
            }
            untracked(() => this.interpolatedLabels.set(labels));
        });
    }

    /**
     * Process messages for display (clone)
     */
    private processMessages(rawMessages: IrisMessage[]): IrisMessage[] {
        return _.cloneDeep(rawMessages);
    }

    ngAfterViewInit() {
        // Enable animations after initial messages have loaded
        // Delay ensures initial message batch doesn't trigger animations
        setTimeout(() => (this.shouldAnimate = true), 500);
    }

    checkIfUserAcceptedLLMUsage(): void {
        this.userAccepted.set(this.accountService.userIdentity()?.selectedLLMUsage);
        setTimeout(() => this.adjustTextareaRows(), 0);
    }

    readonly reopenChat = output<void>();

    async showAISelectionModal(): Promise<void> {
        this.closeChat();
        const choice = await this.llmModalService.open(this.accountService.userIdentity()?.selectedLLMUsage);

        switch (choice) {
            case LLMSelectionDecision.CLOUD_AI:
                this.acceptPermission(LLMSelectionDecision.CLOUD_AI);
                this.reopenChat.emit();
                break;
            case LLMSelectionDecision.LOCAL_AI:
                this.acceptPermission(LLMSelectionDecision.LOCAL_AI);
                this.reopenChat.emit();
                break;
            case LLMSelectionDecision.NO_AI:
                this.chatService.updateLLMUsageConsent(LLMSelectionDecision.NO_AI);
                break;
            case LLM_MODAL_DISMISSED:
                break;
        }
    }

    private focusInputAfterAcceptance() {
        setTimeout(() => {
            if (this.messageTextarea()) {
                this.messageTextarea()!.nativeElement.focus();
            } else if (this.acceptButton()) {
                this.acceptButton()!.nativeElement.focus();
            }
        }, 150);
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

    onMcqAnswerChanged(message: IrisMessage, event: { selectedIndex: number | undefined; submitted: boolean }): void {
        if (!event.submitted || event.selectedIndex === undefined || !message.id) {
            return;
        }
        const sessionId = this.currentSessionId();
        if (!sessionId) {
            return;
        }
        this.irisChatHttpService.saveMcqResponse(sessionId, message.id, { selectedIndex: event.selectedIndex, submitted: true }).subscribe();
    }

    onMcqResponseSaved(message: IrisMessage, response: McqResponseData): void {
        if (!response.submitted || !message.id) {
            return;
        }
        const sessionId = this.currentSessionId();
        if (!sessionId) {
            return;
        }
        this.irisChatHttpService.saveMcqResponse(sessionId, message.id, response).subscribe();
    }

    copyMessage(message: IrisMessage, messageIndex?: number) {
        const text = this.getCopyText(message);
        if (!text) {
            return;
        }
        const key = this.getMessageKey(message, messageIndex);
        if (navigator.clipboard?.writeText) {
            navigator.clipboard
                .writeText(text)
                .then(() => this.setCopiedKey(key))
                .catch(() => {
                    if (this.fallbackCopy(text)) {
                        this.setCopiedKey(key);
                    }
                });
            return;
        }
        if (this.fallbackCopy(text)) {
            this.setCopiedKey(key);
        }
    }

    private fallbackCopy(text: string): boolean {
        return this.clipboard.copy(text);
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
        }, COPY_FEEDBACK_DURATION_MS);
    }

    /**
     * Scrolls the chat body to the bottom.
     * @param behavior - The scroll behavior.
     */
    scrollToBottom(behavior: ScrollBehavior) {
        setTimeout(() => {
            const messagesElement: HTMLElement = this.messagesElement()?.nativeElement;
            if (!messagesElement) return;
            messagesElement.scrollTo({
                top: messagesElement.scrollHeight,
                behavior: behavior,
            });
        });
    }

    /**
     * Accepts the permission to use the chat widget.
     */
    acceptPermission(decision: LLMSelectionDecision) {
        this.chatService.updateLLMUsageConsent(decision);
        this.userAccepted.set(decision);
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
        if (((event.key === 'Tab' && !event.shiftKey) || event.key === 'ArrowRight') && this.ghostText()) {
            const textarea = this.messageTextarea()?.nativeElement;
            // Only accept ghost text on ArrowRight if cursor is at end of input
            if (event.key === 'ArrowRight' && textarea && textarea.selectionStart !== this.newMessageTextContent().length) {
                return;
            }
            event.preventDefault();
            this.newMessageTextContent.set(this.newMessageTextContent() + this.ghostText());
            this.adjustTextareaRows();
            // Move cursor to end
            if (textarea) {
                const len = this.newMessageTextContent().length;
                textarea.setSelectionRange(len, len);
            }
            return;
        }
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
            return;
        }
        const maxHeight = 200;
        const newHeight = Math.min(textarea.scrollHeight, maxHeight);
        textarea.style.height = `${newHeight}px`;
    }

    /**
     * Resets the textarea height and scroll button position.
     */
    resetChatBodyHeight() {
        const textareaRef = this.messageTextarea();
        if (!textareaRef) return;
        const textarea: HTMLTextAreaElement = textareaRef.nativeElement;
        textarea.rows = 1;
        textarea.style.height = '';
    }

    checkChatScroll() {
        const messagesElement = this.messagesElement()?.nativeElement;
        if (!messagesElement) return;
        const { scrollTop, scrollHeight, clientHeight } = messagesElement;
        this.isScrolledToBottom.set(scrollTop >= scrollHeight - clientHeight - 50);
    }

    onSuggestionClick(suggestion: string) {
        this.clickedSuggestion.set(suggestion);
        this.newMessageTextContent.set(suggestion);
        this.onSend();
    }

    applyChipText(translationKey: string): void {
        if (this.isInputDisabled()) return;
        const text = this.translateService.instant(translationKey);
        this.chipPreviewText.set('');
        this.isChipTextApplied.set(true);
        this.newMessageTextContent.set(text);
        setTimeout(() => {
            const textarea = this.messageTextarea()?.nativeElement;
            if (textarea) {
                textarea.focus();
                textarea.setSelectionRange(text.length, text.length);
            }
            this.adjustTextareaRows();
        });
    }

    onChipMouseEnter(starterKey: string): void {
        if (this.isInputDisabled()) return;
        if (this.isChipTextApplied()) return;
        this.chipPreviewText.set(this.translateService.instant(starterKey));
    }

    onChipMouseLeave(): void {
        if (this.isChipTextApplied()) return;
        this.chipPreviewText.set('');
    }

    onSessionClick(session: IrisSessionDTO) {
        if (this.isNewChatSession(session)) {
            this.openNewSession();
            return;
        }
        this.chatService.switchToSession(session);
    }

    onDeleteSession(session: IrisSessionDTO) {
        const title = session.title || formatDate(session.creationDate, 'short', this.translateService.getCurrentLang() || 'en');
        this.confirmationService.confirm({
            header: this.translateService.instant('artemisApp.iris.chatHistory.deleteSessionHeader'),
            message: this.translateService.instant('artemisApp.iris.chatHistory.deleteSessionQuestion', { title }),
            acceptLabel: this.translateService.instant('entity.action.delete'),
            rejectLabel: this.translateService.instant('entity.action.cancel'),
            acceptButtonStyleClass: 'p-button-danger',
            rejectButtonStyleClass: 'p-button-secondary',
            accept: () => {
                this.chatService
                    .deleteSession(session.id)
                    .pipe(takeUntilDestroyed(this.destroyRef))
                    .subscribe({
                        next: () => {
                            this.alertService.success('artemisApp.iris.chatHistory.deleteSessionSuccess');
                        },
                        error: () => {
                            this.alertService.error('artemisApp.iris.chatHistory.deleteSessionError');
                        },
                    });
            },
        });
    }

    setChatHistoryVisibility(isOpen: boolean) {
        this.isChatHistoryOpen.set(isOpen);
        if (!isOpen) {
            this.setSearchValue('');
        }
    }

    private isNewChatSession(session: IrisSessionDTO): boolean {
        const title = session.title?.trim().toLowerCase();
        if (!title) {
            return false;
        }
        return IrisBaseChatbotComponent.NEW_CHAT_TITLES.has(title);
    }

    private isSessionRelatedToCurrentContext(session: IrisSessionDTO): boolean {
        const currentMode = this.currentChatMode();
        if (!currentMode || session.mode !== currentMode) {
            return false;
        }

        const currentEntityId = this.currentRelatedEntityId();
        if (currentEntityId === undefined) {
            return session.entityId === undefined;
        }
        return session.entityId === currentEntityId;
    }

    /**
     * Retrieves chat sessions that occurred between a specified range of days ago.
     * @param sessions The sessions to filter.
     * @param daysAgoNewer The newer boundary of the range, in days ago (e.g., 0 for today, 1 for yesterday).
     * @param daysAgoOlder The older boundary of the range, in days ago (e.g., 0 for today, 7 for 7 days ago).
     *                     Must be greater than or equal to daysAgoNewer if ignoreOlderBoundary is false.
     * @param ignoreOlderBoundary If true, only the daysAgoNewer boundary is considered (sessions newer than or on this day).
     * @param _currentDay Used for reactive dependency tracking. Passing dayTick() ensures computed signals re-run when the day changes.
     * @returns An array of IrisSession objects matching the criteria.
     */
    private filterSessionsBetween(sessions: IrisSessionDTO[], daysAgoNewer: number, daysAgoOlder?: number, ignoreOlderBoundary = false, _currentDay?: string): IrisSessionDTO[] {
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

        const filtered = sessions.filter((session) => {
            const sessionCreationDate = new Date(session.creationDate);
            const isAfterOrOnStartDate = ignoreOlderBoundary || (rangeStartDate && sessionCreationDate.getTime() >= rangeStartDate.getTime());
            const isBeforeOrOnEndDate = sessionCreationDate.getTime() <= rangeEndDate.getTime();
            return isBeforeOrOnEndDate && (ignoreOlderBoundary || isAfterOrOnStartDate);
        });

        // Sort by creation date descending (most recent first)
        return filtered.sort((a, b) => new Date(b.creationDate).getTime() - new Date(a.creationDate).getTime());
    }

    toggleSessionMenu(event: Event) {
        event.stopPropagation();
        this.sessionMenuOpen.update((open) => !open);
    }

    onSessionMenuHide() {
        this.sessionMenuOpen.set(false);
    }

    @HostListener('document:click')
    onDocumentClick() {
        this.onSessionMenuHide();
    }

    openNewSession() {
        if (this.isChatHistoryAvailable()) {
            // Dashboard: always create a new session with the course as context
            const courseId = this.chatService.getCourseId();
            if (courseId !== undefined) {
                this.chatService.switchToNewSession(ChatServiceMode.COURSE, courseId);
                return;
            }
        }
        this.chatService.clearChat();
    }

    openAboutIrisModal(): void {
        // When opened from the exercise/lecture chat widget, the chat lives inside a CDK
        // MatDialog overlay. A PrimeNG dialog cannot render above it because the chat widget
        // uses CSS transforms (for drag/resize) which create an isolated stacking context.
        // Solution: open via CDK MatDialog so it stacks correctly above the chat overlay.
        if (this.layout() === 'widget') {
            this.aboutIrisMatDialogRef?.close();
            this.aboutIrisMatDialogRef = this.matDialog.open(AboutIrisModalComponent, {
                hasBackdrop: true,
                disableClose: true,
                panelClass: 'about-iris-dialog',
                backdropClass: 'about-iris-backdrop',
                width: '40rem',
                maxWidth: '95vw',
            });
        } else {
            this.aboutIrisDialogRef?.close();
            this.aboutIrisDialogRef =
                this.dialogService.open(AboutIrisModalComponent, {
                    modal: true,
                    closable: false,
                    showHeader: false,
                    styleClass: 'about-iris-dialog',
                    maskStyleClass: 'about-iris-dialog',
                    width: '40rem',
                    breakpoints: { '640px': '95vw' },
                }) ?? undefined;
        }
    }

    setSearchValue(searchValue: string) {
        this.searchValue.set(searchValue.trim().toLowerCase());
    }

    private getSessionMenuLabel(session: IrisSessionDTO, newChatLabel: string): string {
        if (session.title && !this.isNewChatSession(session)) {
            return session.title;
        }
        const creationLabel = formatDate(session.creationDate, 'short', this.translateService.getCurrentLang() || 'en');
        return `${newChatLabel} (${creationLabel})`;
    }

    /**
     * Retrieves chat sessions that occurred between a specified range of days ago.
     * Public wrapper for filterSessionsBetween that uses the current filtered sessions.
     * @param daysAgoNewer The newer boundary of the range, in days ago (e.g., 0 for today, 1 for yesterday).
     * @param daysAgoOlder The older boundary of the range, in days ago (e.g., 0 for today, 7 for 7 days ago).
     * @param ignoreOlderBoundary If true, only the daysAgoNewer boundary is considered.
     * @returns An array of IrisSessionDTO objects matching the criteria.
     */
    getSessionsBetween(daysAgoNewer: number, daysAgoOlder?: number, ignoreOlderBoundary = false): IrisSessionDTO[] {
        return this.filterSessionsBetween(this.filteredNonNewSessions(), daysAgoNewer, daysAgoOlder, ignoreOlderBoundary, this.dayTick());
    }

    private computeRelatedEntityRoute(currentChatMode: ChatServiceMode | undefined, currentRelatedEntityId: number | undefined): string | undefined {
        if (!currentChatMode || !currentRelatedEntityId) {
            return undefined;
        }
        switch (currentChatMode) {
            case ChatServiceMode.PROGRAMMING_EXERCISE:
            case ChatServiceMode.TEXT_EXERCISE:
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
            case ChatServiceMode.TEXT_EXERCISE:
                return `artemisApp.exerciseChatbot.goToRelatedEntityButton.exerciseLabel`;
            case ChatServiceMode.LECTURE:
                return `artemisApp.exerciseChatbot.goToRelatedEntityButton.lectureLabel`;
            default:
                return undefined;
        }
    }
}
