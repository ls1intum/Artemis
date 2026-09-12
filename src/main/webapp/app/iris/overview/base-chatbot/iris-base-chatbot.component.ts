import {
    faAnglesRight,
    faArrowDown,
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
import { AlertService } from 'app/foundation/service/alert.service';
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
import { Clipboard } from '@angular/cdk/clipboard';
import { IrisAssistantMessage, IrisMessage, IrisSender } from 'app/iris/shared/entities/iris-message.model';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { IrisMessageContextDTO } from 'app/iris/shared/entities/iris-message-context-dto.model';
import { ButtonComponent, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { TranslateService } from '@ngx-translate/core';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
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
import { ChatServiceMode, IrisChatService, IrisLiveAssistantDraft } from 'app/iris/overview/services/iris-chat.service';
import { IrisChatHttpService } from 'app/iris/overview/services/iris-chat-http.service';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
import { IrisCitationTextComponent } from 'app/iris/overview/citation-text/iris-citation-text.component';
import { ActivatedRoute, Params, RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AsPipe } from 'app/foundation/pipes/as.pipe';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { ChatHistoryItemComponent } from './chat-history-item/chat-history-item.component';
import { formatDate } from '@angular/common';
import { MenuModule } from 'primeng/menu';
import { MenuItem } from 'primeng/api';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { SearchFilterComponent } from 'app/shared-ui/search-filter/search-filter.component';
import { CourseSidebarToggleButtonComponent } from 'app/course/shared/course-sidebar-toggle-button/course-sidebar-toggle-button.component';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { ChatStatusBarComponent } from 'app/iris/overview/base-chatbot/chat-status-bar/chat-status-bar.component';
import { IrisActivityFeedComponent } from 'app/iris/overview/base-chatbot/iris-activity-feed/iris-activity-feed.component';
import { AboutIrisModalComponent } from 'app/iris/overview/about-iris-modal/about-iris-modal.component';
import { IrisOnboardingService } from 'app/iris/overview/iris-onboarding-modal/iris-onboarding.service';
import { IrisChatMemoriesIndicatorComponent } from 'app/iris/overview/base-chatbot/memories-indicator/iris-chat-memories-indicator.component';
import { MemirisMemory } from 'app/iris/shared/entities/memiris.model';
import { EXERCISE_PLACEHOLDER_LABEL_KEYS, LECTURE_PLACEHOLDER_LABEL_KEYS } from './iris-chatbot-placeholder-labels';
import { createActiveSuggestionChips } from './iris-chatbot-suggestion-chips';
import { ContextSelectionComponent } from 'app/iris/overview/context-selection/context-selection.component';
import { IrisContextSwitchDividerComponent } from 'app/iris/overview/context-selection/iris-context-switch-divider.component';
import { routeForContext } from 'app/iris/overview/context-selection/iris-context.util';
import { IrisActivityItem, IrisActivityState, IrisRunState } from 'app/iris/shared/entities/iris-activity.model';
import { deepClone } from 'app/foundation/util/deep-clone.util';

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

// Interval (in ms) for client-side streamed draft reveal.
const LIVE_DRAFT_ANIMATION_TICK_MS = 50;
const LIVE_DRAFT_CATCH_UP_MS = 400;

// Number of decimals the activity trail duration is rendered with.
const ACTIVITY_DURATION_DECIMALS = 1;
// Shortest activity trail duration (in s) still worth showing: anything below this rounds away to "0.0s" at one decimal.
const MIN_DISPLAYED_ACTIVITY_DURATION_SECONDS = 0.5 * 10 ** -ACTIVITY_DURATION_DECIMALS;

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
        MarkdownDirective,
        ChatHistoryItemComponent,
        SearchFilterComponent,
        IrisCitationTextComponent,
        IrisMcqQuestionComponent,
        IrisMcqCarouselComponent,
        IrisChatMemoriesIndicatorComponent,
        IrisActivityFeedComponent,
        ConfirmDialogModule,
        MenuModule,
        ContextSelectionComponent,
        IrisContextSwitchDividerComponent,
        CourseSidebarToggleButtonComponent,
    ],
    providers: [ConfirmationService],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisBaseChatbotComponent implements AfterViewInit {
    protected accountService = inject(AccountService);
    protected translateService = inject(TranslateService);
    private readonly dialogService = inject(DialogService);
    private aboutIrisDialogRef: DynamicDialogRef<AboutIrisModalComponent> | undefined;
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
    private readonly onboardingService = inject(IrisOnboardingService);
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
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faCopy = faCopy;
    protected readonly faCheck = faCheck;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faAnglesRight = faAnglesRight;
    protected readonly faMagnifyingGlass = faMagnifyingGlass;

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

    private readonly currentChatMode = computed(() => this.chatService.displayContext()?.mode);
    readonly relatedEntityRoute = computed<string | undefined>(() =>
        this.computeRelatedEntityRoute(this.chatService.committedContext()?.mode, this.chatService.committedContext()?.entityId),
    );
    readonly relatedEntityLinkButtonLabel = computed<string | undefined>(() => this.computeRelatedEntityLinkButtonLabel(this.chatService.committedContext()?.mode));

    // Observable-derived signals (using toSignal for reactive state)

    readonly currentSessionId = toSignal(this.chatService.currentSessionId(), { initialValue: undefined });
    readonly chatSessions = toSignal(this.chatService.availableChatSessions(), { initialValue: [] as IrisSessionDTO[] });
    readonly runInfo = toSignal(this.chatService.currentRunInfo(), { initialValue: undefined });
    readonly activities = toSignal(this.chatService.currentActivities(), { initialValue: [] as IrisActivityItem[] });
    readonly suggestions = toSignal(this.chatService.currentSuggestions(), { initialValue: [] as string[] });
    readonly error = toSignal(this.chatService.currentError(), { initialValue: undefined });
    readonly numNewMessages = toSignal(this.chatService.currentNumNewMessages(), { initialValue: 0 });
    readonly liveAssistantDraft = toSignal(this.chatService.currentLiveAssistantDraft(), { initialValue: undefined });
    readonly rateLimitInfo = toSignal(this.statusService.currentRatelimitInfo(), { requireSync: true });
    readonly active = toSignal(this.statusService.getActiveStatus(), { initialValue: true });
    readonly citationInfo = toSignal(this.chatService.currentCitationInfo(), { initialValue: [] as IrisCitationMetaDTO[] });

    // Messages with processing
    private readonly rawMessages = toSignal(this.chatService.currentMessages(), { initialValue: [] as IrisMessage[] });
    readonly messages = computed(() => this.processMessages(this.rawMessages()));
    // Tracks whether the chat service has finished its first session-load attempt for the
    // current context. Prevents downstream gates (notably the onboarding tour) from acting
    // on the BehaviorSubject's empty initial value before the real messages arrive.
    private readonly initialLoadComplete = toSignal(this.chatService.initialLoadComplete$, { initialValue: false });

    // Computed state
    readonly runningActivity = computed(() => this.activities().find((activity) => activity.state === IrisActivityState.RUNNING));
    readonly awaitingAnswer = this.chatService.awaitingAnswer;
    readonly thinkingMessage = computed(() => this.translateService.instant('artemisApp.iris.thinking'));
    readonly shouldShowThinkingBubble = computed(() => this.awaitingAnswer() && !this.runningActivity() && !this.liveAssistantDraft());
    readonly shouldShowStatusBar = computed(() => this.runInfo()?.state === IrisRunState.FAILED);
    readonly isEmptyState = computed(
        () => !this.messages()?.length && !this.liveAssistantDraft() && !this.shouldShowThinkingBubble() && !this.activities().length && !this.isEmbeddedChat(),
    );
    readonly hasCurrentSessionContent = computed(
        () => (this.messages()?.length ?? 0) > 0 || !!this.liveAssistantDraft() || this.shouldShowThinkingBubble() || this.activities().length > 0,
    );
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
            !!(this.rateLimitInfo()?.rateLimit && this.rateLimitInfo().currentMessageCount === this.rateLimitInfo().rateLimit) ||
            this.awaitingAnswer(),
    );
    readonly isSendDisabled = computed(() => !this.newMessageTextContent().trim() || this.isInputDisabled());
    readonly canShowSuggestions = computed(
        () =>
            !!this.suggestions()?.length &&
            this.isAIEnabled() &&
            this.active() &&
            (!this.rateLimitInfo()?.rateLimit || this.rateLimitInfo().currentMessageCount !== this.rateLimitInfo().rateLimit) &&
            !this.awaitingAnswer(),
    );
    readonly isScrolledToBottom = signal(true);
    readonly exchangeSpacerPx = signal(0);
    private pendingAnchorScroll = false;
    private anchoredMessageId: number | undefined;
    private exchangeAnchorActive = false;
    // requestAnimationFrame id and remaining-frame counter for the initial-load settle scroll.
    private settleScrollRafId: number | undefined;
    private settleScrollFrames = 0;
    readonly displayedLiveAssistantDraftText = signal('');
    private liveAssistantDraftTargetText = '';
    private liveAssistantDraftRunId: string | undefined;
    private pendingFinalizedLiveAssistantDraftRunId: string | undefined;
    private liveDraftAnimationIntervalId: ReturnType<typeof setInterval> | undefined;
    private draftSwapScrollRafId: number | undefined;
    readonly resendAnimationActive = signal(false);
    readonly clickedSuggestion = signal<string | undefined>(undefined);
    private readonly isSuggestionAnimating = signal(false);

    // Animation state (internal tracking)
    private shouldAnimate = false;
    // Ensures the onboarding tour is offered at most once per mount even though
    // the triggering effect re-evaluates each time its gating signals change.
    private onboardingTriggerRequested = false;
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
    readonly aboutIrisDialogTransport = input<'automatic' | 'material' | 'dynamic'>('automatic');
    /**
     * Whether the user may change the chat topic from the input row. When false, the context selector
     * (the "+" dropdown and the chip showing the active context) is not rendered, so the session keeps
     * whatever context its host pinned. The CTXSWAP dividers in the message list stay visible either
     * way, so the student still sees which topic the chat is on.
     */
    readonly isContextSelectionAvailable = input<boolean>(true);
    /** Optional function provider that returns a list of context objects for the current message */
    readonly contextProvider = input<(() => IrisMessageContextDTO[]) | undefined>(undefined);
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
        if (!labels.length || !this.isExerciseOrLectureMode()) {
            return '';
        }
        // With an empty, focused textarea the rotating placeholder freezes on a single
        // example phrase; offer that whole phrase as the ghost suggestion so Tab completes it.
        const displayedPhrase = this.isFocused() && this.shouldUseRotatingPlaceholder() ? this.currentPlaceholder() : '';
        if (!input) {
            return displayedPhrase;
        }
        const inputLower = input.toLowerCase();
        // If the phrase currently shown as the suggestion still matches what the user typed,
        // keep completing that exact phrase. Otherwise the suggestion would jump to a different
        // label — several phrases can share the same starting word (e.g. multiple "W..." labels),
        // and labels is shuffled, so find() could land on a phrase other than the visible one.
        if (displayedPhrase && displayedPhrase.toLowerCase().startsWith(inputLower)) {
            return displayedPhrase.slice(input.length);
        }
        const match = labels.find((label) => label.toLowerCase().startsWith(inputLower));
        return match ? match.slice(input.length) : '';
    });

    readonly textareaPlaceholder = computed(() => {
        if (this.chipPreviewText()) return '';
        // When focused with empty input, the ghost overlay renders the example phrase (so Tab can
        // complete it); suppress the native placeholder to avoid drawing the same phrase twice.
        if (this.ghostText() && !this.newMessageTextContent()) return '';
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

    protected getActivities(message: IrisMessage): IrisActivityItem[] {
        return message.sender === IrisSender.LLM ? (message.activities ?? []) : [];
    }

    protected activityTrailSummary(activities: IrisActivityItem[]): string {
        const totalDurationMillis = activities.reduce((sum, activity) => sum + (activity.durationMillis ?? 0), 0);
        const totalDurationSeconds = totalDurationMillis / 1000;
        // A single activity reads as "1 tool used", more than one as "n tools used", so the plural form picks the key.
        const pluralSuffix = activities.length === 1 ? 'Singular' : 'Plural';
        // Durations that round away to "0.0s" add noise rather than information, so the time is omitted entirely.
        if (totalDurationSeconds < MIN_DISPLAYED_ACTIVITY_DURATION_SECONDS) {
            return this.translateService.instant(`artemisApp.iris.activities.trailSummaryWithoutDuration${pluralSuffix}`, {
                count: activities.length,
            });
        }
        return this.translateService.instant(`artemisApp.iris.activities.trailSummary${pluralSuffix}`, {
            count: activities.length,
            duration: totalDurationSeconds.toFixed(ACTIVITY_DURATION_DECIMALS),
        });
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
        // Cycling is not stopped on focus: as long as the textarea is empty the placeholder keeps
        // rotating (the cycling effect is the single source of truth). Typing stops it via the
        // non-empty guard, and clearing the text again lets it resume.
    }

    onTextareaBlur(): void {
        this.isFocused.set(false);
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
        this.route.queryParams?.pipe(takeUntilDestroyed()).subscribe((params: Params) => {
            if (params?.irisQuestion) {
                this.newMessageTextContent.set(params.irisQuestion);
            }
        });

        // Handle session changes - reset animations
        effect((onCleanup) => {
            const sessionId = this.currentSessionId();
            if (this.previousSessionId !== sessionId) {
                this.resetLiveAssistantDraftState();
                this.animatingMessageIds.set(new Set<number>());
                this.shouldAnimate = false;
                this.isScrolledToBottom.set(true);
                this.pendingAnchorScroll = false;
                this.anchoredMessageId = undefined;
                this.exchangeAnchorActive = false;
                this.exchangeSpacerPx.set(0);
                const timeoutId = setTimeout(() => (this.shouldAnimate = true));
                onCleanup(() => clearTimeout(timeoutId));
            }
            this.previousSessionId = sessionId;
        });

        // Handle message scroll on new messages
        effect((onCleanup) => {
            const rawMessages = this.rawMessages();
            // A count increase landing exactly as the streamed draft disappeared is the
            // draft's own finalization: the persisted message replacing the live bubble.
            const isDraftFinalization =
                rawMessages.length > this.previousMessageCount &&
                !this.liveAssistantDraft() &&
                (this.liveAssistantDraftRunId !== undefined || this.pendingFinalizedLiveAssistantDraftRunId !== undefined) &&
                !this.isIntermediateAssistantMessage(rawMessages.at(-1));
            if (rawMessages.length !== this.previousMessageCount) {
                // Initial history load (e.g. after a page refresh): the batch lands at once and
                // its content keeps growing the scroll height for several frames, so use the
                // settling scroll to land exactly at the bottom instead of a tiny bit short.
                const isInitialLoad = this.previousMessageCount === 0 && rawMessages.length > 0;
                if (isDraftFinalization) {
                    this.exchangeAnchorActive = false;
                    this.pendingFinalizedLiveAssistantDraftRunId = undefined;
                    this.liveAssistantDraftRunId = undefined;
                    this.preserveScrollAcrossDraftSwap();
                } else if (this.isIntermediateAssistantMessage(rawMessages.at(-1))) {
                    this.pendingFinalizedLiveAssistantDraftRunId = undefined;
                    this.liveAssistantDraftRunId = undefined;
                } else if (this.pendingAnchorScroll && rawMessages.length > this.previousMessageCount) {
                    const lastMessage = rawMessages.at(-1);
                    if (lastMessage?.sender === IrisSender.USER && lastMessage.id !== undefined) {
                        this.pendingAnchorScroll = false;
                        this.anchoredMessageId = lastMessage.id;
                        this.exchangeAnchorActive = true;
                        setTimeout(() => this.scrollAnchoredMessageToTop());
                    }
                } else if (
                    this.exchangeAnchorActive &&
                    rawMessages.length > this.previousMessageCount &&
                    !this.liveAssistantDraft() &&
                    rawMessages.at(-1)?.sender === IrisSender.LLM
                ) {
                    // A non-streamed answer (response streaming disabled, or an older Pyris that only
                    // sends the final MESSAGE) completes without a live draft ever existing, so the
                    // draft-finalization branch above never runs. Release the anchor here so the
                    // exchange spacer collapses instead of leaving blank space below the finished exchange.
                    this.exchangeAnchorActive = false;
                } else if (isInitialLoad && this.isScrolledToBottom()) {
                    this.scrollToBottomSettled();
                } else if (this.isScrolledToBottom() && !this.isSuggestionAnimating()) {
                    this.scrollToBottom('smooth');
                }
                const timeoutId = setTimeout(() => this.messageTextarea()?.nativeElement?.focus(), 10);
                onCleanup(() => clearTimeout(timeoutId));
            }
            // Track new messages for animation (compare against previous IDs, not current).
            // A message that finalizes a streamed draft must NOT get the entrance
            // animation: its content is already fully visible in the draft bubble, and
            // the animation renders it at height 0 (grid-template-rows: 0fr) growing to
            // full size — collapsing the scroll content and snapping the viewport.
            if (this.shouldAnimate && !isDraftFinalization) {
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

        // Scroll when the idle thinking bubble appears for flows that start without an explicit send.
        effect(() => {
            if (this.shouldShowThinkingBubble() && !this.pendingAnchorScroll && !this.exchangeAnchorActive && this.isScrolledToBottom()) {
                this.scrollToBottom('smooth');
            }
        });

        // Drive streamed assistant draft display.
        effect(() => {
            this.handleLiveAssistantDraftChange(this.liveAssistantDraft());
        });

        // Keep enough room below the anchored user message while streaming content changes height.
        effect((onCleanup) => {
            this.rawMessages();
            this.displayedLiveAssistantDraftText();
            this.shouldShowThinkingBubble();
            this.activities();
            this.canShowSuggestions();
            const rafId = window.requestAnimationFrame(() => this.updateExchangeSpacer());
            onCleanup(() => window.cancelAnimationFrame(rafId));
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
        this.destroyRef.onDestroy(() => this.resetLiveAssistantDraftState());
        this.destroyRef.onDestroy(() => {
            if (this.draftSwapScrollRafId !== undefined) {
                window.cancelAnimationFrame(this.draftSwapScrollRafId);
            }
        });
        this.destroyRef.onDestroy(() => {
            if (this.settleScrollRafId !== undefined) {
                window.cancelAnimationFrame(this.settleScrollRafId);
                this.settleScrollRafId = undefined;
            }
        });
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
        });

        // Placeholder cycling lifecycle
        effect((onCleanup) => {
            // Cycle whenever the textarea is empty (focused or not); typing halts it via the
            // non-empty guard, and clearing the text resumes it.
            const shouldCycle = this.shouldUseRotatingPlaceholder() && !this.newMessageTextContent() && !this.isInputDisabled();
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

        // Kick off the onboarding tour the moment all gating signals agree it's appropriate.
        // Brand-new users mount this component with isAIEnabled() === false (they have not
        // picked an LLM yet). A one-shot call from ngAfterViewInit would evaluate too early
        // and skip the tour forever. This effect re-fires after acceptPermission() flips
        // userAccepted, then guards against re-entry with onboardingTriggerRequested.
        //
        // initialLoadComplete() is the race fix: messages starts as [] (BehaviorSubject
        // initial value), so isEmptyState() returns true on every mount before the chat
        // service has loaded the actual session. Without this gate, returning users with
        // existing messages would briefly look like new users and the tour could fire
        // before the server-side message-count check finishes the round-trip.
        effect(() => {
            const ready = this.initialLoadComplete() && this.layout() === 'client' && this.isEmptyState() && !this.error() && this.active() && this.isAIEnabled();
            if (!ready || this.onboardingTriggerRequested) {
                return;
            }
            this.onboardingTriggerRequested = true;
            untracked(() => {
                const shouldShowOnboarding = () =>
                    this.initialLoadComplete() && this.layout() === 'client' && this.isEmptyState() && !this.error() && this.active() && this.isAIEnabled();
                void this.onboardingService.showOnboardingIfNeeded(shouldShowOnboarding).catch(() => undefined);
            });
        });
    }

    /**
     * Process messages for display (clone)
     */
    private processMessages(rawMessages: IrisMessage[]): IrisMessage[] {
        return deepClone(rawMessages);
    }

    private isIntermediateAssistantMessage(message: IrisMessage | undefined): boolean {
        return message?.sender === IrisSender.LLM && message.final === false;
    }

    ngAfterViewInit() {
        // Enable animations after initial messages have loaded
        // Delay ensures initial message batch doesn't trigger animations
        setTimeout(() => (this.shouldAnimate = true), 500);
    }

    onContextChangedDuringOnboarding(): void {
        if (this.onboardingService.currentStep() === 1) {
            this.onboardingService.onboardingEvent$.next({ type: 'contextChanged' });
        }
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
            const provider = this.contextProvider();
            const context = provider ? provider() : undefined;

            this.chatService
                .sendMessage(content, {}, context && context.length > 0 ? context : undefined)
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe({
                    next: () => {
                        this.isLoading.set(false);
                    },
                    error: () => {
                        this.isLoading.set(false);
                    },
                });
            this.newMessageTextContent.set('');
            // The echoed user message renders asynchronously; anchor it after the DOM exists.
            this.pendingAnchorScroll = true;
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
        if (!this.canRateMessage(message)) {
            return;
        }
        message.helpful = !!helpful;
        this.chatService.rateMessage(message, helpful).pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
    }

    protected canRateMessage(message: IrisMessage): message is IrisAssistantMessage {
        return message.sender === IrisSender.LLM && message.final !== false;
    }

    /** Index of the last assistant (LLM) message in the conversation, or -1 if there is none. */
    protected readonly lastAssistantMessageIndex = computed(() => {
        const messages = this.messages();
        for (let i = messages.length - 1; i >= 0; i--) {
            if (messages[i].sender === IrisSender.LLM) {
                return i;
            }
        }
        return -1;
    });

    /**
     * The copy/rate toolbox is only rendered under the final assistant message of the conversation,
     * and never while a new response is being generated (so it disappears between an answer and the
     * next incoming one, and reappears once that response has finished).
     */
    protected isLastAssistantMessage(index: number): boolean {
        if (index !== this.lastAssistantMessageIndex() || this.awaitingAnswer()) {
            return false;
        }
        // The newest assistant message may be an intermediate (final: false) one — e.g. a tool call whose
        // run then failed, or a persisted intermediate message reloaded without run info. Never expose the
        // toolbox for such a message, regardless of the current run state.
        const message = this.messages()[index];
        return message?.sender === IrisSender.LLM && message.final !== false;
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
     * Keeps the user's reading position stable while the ephemeral draft bubble is
     * replaced by the persisted final message. The swap spans two render frames
     * (message added, draft removed); restoring the captured scrollTop on the frames
     * after the swap prevents the browser from clamping/shifting the viewport.
     */
    private preserveScrollAcrossDraftSwap(): void {
        const container = this.messagesElement()?.nativeElement as HTMLElement | undefined;
        if (!container) {
            return;
        }
        const scrollTopBeforeSwap = container.scrollTop;
        let remainingFrames = 3;
        const restore = () => {
            container.scrollTop = scrollTopBeforeSwap;
            remainingFrames -= 1;
            if (remainingFrames > 0) {
                this.draftSwapScrollRafId = window.requestAnimationFrame(restore);
            } else {
                this.draftSwapScrollRafId = undefined;
            }
        };
        if (this.draftSwapScrollRafId !== undefined) {
            window.cancelAnimationFrame(this.draftSwapScrollRafId);
        }
        this.draftSwapScrollRafId = window.requestAnimationFrame(restore);
    }

    private handleLiveAssistantDraftChange(draft: IrisLiveAssistantDraft | undefined): void {
        if (!draft) {
            if (this.liveAssistantDraftRunId !== undefined) {
                this.pendingFinalizedLiveAssistantDraftRunId = this.liveAssistantDraftRunId;
            }
            this.clearLiveAssistantDraftAnimation();
            this.liveAssistantDraftRunId = undefined;
            return;
        }

        const isNewRun = draft.runId !== this.liveAssistantDraftRunId;
        if (isNewRun) {
            this.liveAssistantDraftRunId = draft.runId;
            this.pendingFinalizedLiveAssistantDraftRunId = undefined;
            this.liveAssistantDraftTargetText = '';
            this.displayedLiveAssistantDraftText.set('');
        }

        this.updateLiveAssistantDraftTargetText(draft.text);
    }

    private updateLiveAssistantDraftTargetText(text: string): void {
        const displayedText = this.displayedLiveAssistantDraftText();
        const displayedLength = Array.from(displayedText).length;
        const targetLength = Array.from(text).length;

        if (targetLength < displayedLength) {
            this.liveAssistantDraftTargetText = displayedText;
        } else {
            this.liveAssistantDraftTargetText = text;
            if (targetLength === displayedLength && text !== displayedText) {
                this.displayedLiveAssistantDraftText.set(text);
            }
        }

        if (Array.from(this.liveAssistantDraftTargetText).length > Array.from(this.displayedLiveAssistantDraftText()).length) {
            this.ensureLiveAssistantDraftAnimationLoop();
        } else {
            this.stopLiveAssistantDraftAnimationLoop();
        }
    }

    private ensureLiveAssistantDraftAnimationLoop(): void {
        if (this.liveDraftAnimationIntervalId !== undefined) {
            return;
        }
        this.liveDraftAnimationIntervalId = setInterval(() => this.advanceLiveAssistantDraftAnimation(), LIVE_DRAFT_ANIMATION_TICK_MS);
    }

    private advanceLiveAssistantDraftAnimation(): void {
        const displayedCodePoints = Array.from(this.displayedLiveAssistantDraftText());
        const targetCodePoints = Array.from(this.liveAssistantDraftTargetText);
        const remainingCharacters = targetCodePoints.length - displayedCodePoints.length;
        if (remainingCharacters <= 0) {
            this.stopLiveAssistantDraftAnimationLoop();
            return;
        }

        const ticksPerSnapshot = LIVE_DRAFT_CATCH_UP_MS / LIVE_DRAFT_ANIMATION_TICK_MS;
        const charactersToReveal = Math.max(1, Math.ceil(remainingCharacters / ticksPerSnapshot));
        const nextLength = Math.min(targetCodePoints.length, displayedCodePoints.length + charactersToReveal);
        this.displayedLiveAssistantDraftText.set(targetCodePoints.slice(0, nextLength).join(''));

        if (nextLength >= targetCodePoints.length) {
            this.stopLiveAssistantDraftAnimationLoop();
        }
    }

    private clearLiveAssistantDraftAnimation(): void {
        this.stopLiveAssistantDraftAnimationLoop();
        this.liveAssistantDraftTargetText = '';
        this.displayedLiveAssistantDraftText.set('');
    }

    private stopLiveAssistantDraftAnimationLoop(): void {
        if (this.liveDraftAnimationIntervalId !== undefined) {
            clearInterval(this.liveDraftAnimationIntervalId);
            this.liveDraftAnimationIntervalId = undefined;
        }
    }

    private resetLiveAssistantDraftState(): void {
        this.clearLiveAssistantDraftAnimation();
        this.liveAssistantDraftRunId = undefined;
        this.pendingFinalizedLiveAssistantDraftRunId = undefined;
    }

    private scrollAnchoredMessageToTop(): void {
        const container: HTMLElement | undefined = this.messagesElement()?.nativeElement;
        const anchorElement = container ? this.findAnchoredMessageElement(container) : undefined;
        if (!container || !anchorElement) {
            return;
        }
        this.updateExchangeSpacer();
        container.scrollTo({
            top: this.getAnchorTop(container, anchorElement),
            behavior: 'smooth',
        });
    }

    private updateExchangeSpacer(): void {
        const container: HTMLElement | undefined = this.messagesElement()?.nativeElement;
        const spacerElement = container?.querySelector<HTMLElement>('.stream-exchange-spacer');
        const anchorElement = container ? this.findAnchoredMessageElement(container) : undefined;
        if (!container || !spacerElement || !anchorElement) {
            return;
        }

        const contentSansSpacer = container.scrollHeight - spacerElement.offsetHeight;
        const needed = Math.max(0, this.getAnchorTop(container, anchorElement) + container.clientHeight - contentSansSpacer);
        const nextHeight = this.exchangeAnchorActive ? needed : Math.min(spacerElement.offsetHeight, needed);
        this.setExchangeSpacerHeight(spacerElement, nextHeight);
    }

    private findAnchoredMessageElement(container: HTMLElement): HTMLElement | undefined {
        if (this.anchoredMessageId === undefined) {
            return undefined;
        }
        return container.querySelector<HTMLElement>(`[data-message-id="${this.anchoredMessageId}"]`) ?? undefined;
    }

    private getAnchorTop(container: HTMLElement, anchorElement: HTMLElement): number {
        const containerRect = container.getBoundingClientRect();
        const anchorRect = anchorElement.getBoundingClientRect();
        return anchorRect.top - containerRect.top + container.scrollTop;
    }

    private setExchangeSpacerHeight(spacerElement: HTMLElement, height: number): void {
        const normalizedHeight = Math.max(0, height);
        this.exchangeSpacerPx.set(normalizedHeight);
        spacerElement.style.height = `${normalizedHeight}px`;
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
     * Scrolls to the true bottom and keeps re-applying it across a short settle window. Used on
     * the initial history load, where message content (markdown, code blocks, KaTeX, images)
     * keeps growing the scroll height for several frames after it first renders — a single
     * scroll lands a little short, leaving the user just above the bottom.
     */
    private scrollToBottomSettled() {
        // Restart the settle window if one is already running (e.g. suggestions appear shortly
        // after the initial message batch) so a single loop always covers the latest growth.
        this.settleScrollFrames = 0;
        if (this.settleScrollRafId !== undefined) return;
        const settle = () => {
            const messagesElement: HTMLElement | undefined = this.messagesElement()?.nativeElement;
            if (messagesElement) {
                messagesElement.scrollTop = messagesElement.scrollHeight;
                this.isScrolledToBottom.set(true);
            }
            // Keep correcting for late layout growth; ~20 frames (~330ms) covers async rendering.
            if (this.settleScrollFrames++ < 20) {
                this.settleScrollRafId = window.requestAnimationFrame(settle);
            } else {
                this.settleScrollRafId = undefined;
            }
        };
        this.settleScrollRafId = window.requestAnimationFrame(settle);
    }

    /**
     * Restarts the initial-history settle window when lazily rendered markdown changes the message height.
     * Do not move a user who has scrolled up or disturb the explicit exchange anchor used for new messages.
     */
    protected onMessageMarkdownRendered(): void {
        if (this.isScrolledToBottom() && !this.exchangeAnchorActive) {
            this.scrollToBottomSettled();
        }
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
            // Empty input: fall back to the CSS min-height, which reserves enough lines for the ghost
            // suggestion. Clearing the custom property lets the overlay's own fallback take over.
            textarea.style.height = '';
            textarea.parentElement?.style.removeProperty('--iris-textarea-height');
            return;
        }
        // Read the cap from the stylesheet rather than hardcoding it: max-height scales with the number
        // of lines the layout reserves for the ghost suggestion, so a literal here would disagree with
        // the CSS in the layouts that reserve more. Falls back to the previous value if unresolvable.
        const computedMaxHeight = Number.parseFloat(getComputedStyle(textarea).maxHeight);
        const maxHeight = Number.isFinite(computedMaxHeight) ? computedMaxHeight : 200;
        const newHeight = Math.min(textarea.scrollHeight, maxHeight);
        textarea.style.height = `${newHeight}px`;
        // Expose the grown height so the absolutely positioned ghost-text and chip-preview overlays can
        // clip themselves to the textarea rather than spilling onto the controls row below it.
        textarea.parentElement?.style.setProperty('--iris-textarea-height', `${newHeight}px`);
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

    applyChipText(starterKey: string, translationKey?: string): void {
        if (this.isInputDisabled()) return;
        const text = this.translateService.instant(starterKey);
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
        if (this.onboardingService.currentStep() === 2 && translationKey) {
            this.onboardingService.onboardingEvent$.next({ type: 'chipClicked', translationKey });
        }
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

        const currentEntityId = this.chatService.displayContext()?.entityId;
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
        // startFreshChat() re-applies the lecture/exercise page context itself once the new session
        // loads, so we no longer stage it here — see the JSDoc on IrisChatService.startFreshChat.
        this.chatService.startFreshChat();
    }

    openAboutIrisModal(): void {
        if (this.onboardingService.currentStep() === 3) {
            this.onboardingService.onboardingEvent$.next({ type: 'aboutIrisOpened' });
        }
        // The About dialog always uses PrimeNG's DynamicDialog. It is portaled to <body>, so it
        // escapes the stacking context of the floating chat widget (itself a DynamicDialog) and
        // renders above it regardless of the host layout (sidebar, lecture sidebar, or widget).
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
        return routeForContext(this.chatService.getCourseId(), currentChatMode, currentRelatedEntityId);
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
