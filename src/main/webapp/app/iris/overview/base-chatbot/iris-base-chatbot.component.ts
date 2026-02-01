import {
    faArrowDown,
    faChevronRight,
    faCircle,
    faCircleInfo,
    faCircleNotch,
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
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AfterViewInit, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { DomSanitizer, type SafeHtml } from '@angular/platform-browser';
import { IrisAssistantMessage, IrisMessage, IrisSender } from 'app/iris/shared/entities/iris-message.model';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { TranslateService } from '@ngx-translate/core';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { IrisStageDTO, IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { IrisMessageContentType, IrisTextMessageContent, isTextContent } from 'app/iris/shared/entities/iris-content-type.model';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
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
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { ChatHistoryItemComponent } from './chat-history-item/chat-history-item.component';
import { NgClass } from '@angular/common';
import { facSidebar } from 'app/shared/icons/icons';
import { IrisSessionDTO } from 'app/iris/shared/entities/iris-session-dto.model';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { IrisCitationParsed, escapeHtml, formatCitationLabel, replaceCitationBlocks, resolveCitationTypeClass } from 'app/iris/overview/shared/iris-citation.util';
import { map } from 'rxjs/operators';

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
        ChatHistoryItemComponent,
        NgClass,
        SearchFilterComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisBaseChatbotComponent implements AfterViewInit {
    protected accountService = inject(AccountService);
    protected translateService = inject(TranslateService);
    protected statusService = inject(IrisStatusService);
    protected chatService = inject(IrisChatService);
    protected route = inject(ActivatedRoute);
    private readonly sanitizer = inject(DomSanitizer);
    private readonly destroyRef = inject(DestroyRef);

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
    readonly citationInfo = toSignal(this.chatService.currentCitationInfo(), { initialValue: [] as IrisCitationMetaDTO[] });
    readonly currentLanguage = toSignal(this.translateService.onLangChange.pipe(map((event) => event.lang)), {
        initialValue: this.translateService.getCurrentLang(),
    });

    // Messages with processing
    private readonly rawMessages = toSignal(this.chatService.currentMessages(), { initialValue: [] as IrisMessage[] });
    readonly messages = computed(() => {
        const citationInfo = this.citationInfo();
        this.currentLanguage();
        return this.processMessages(this.rawMessages(), citationInfo);
    });

    // Computed state
    readonly hasActiveStage = computed(() => this.stages()?.some((stage) => [IrisStageStateDTO.IN_PROGRESS, IrisStageStateDTO.NOT_STARTED].includes(stage.state)) ?? false);

    // UI state signals
    readonly newMessageTextContent = signal('');
    readonly isLoading = signal(false);
    readonly isChatHistoryOpen = signal(true);
    readonly searchValue = signal('');
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
    private hoverMessagesElement?: HTMLElement;
    /**
     * Keeps citation summaries within the visible message boundary on hover.
     * @param event The mouseover event.
     */
    private readonly handleCitationMouseOver = (event: MouseEvent) => {
        const messagesElement = this.hoverMessagesElement;
        if (!messagesElement) {
            return;
        }
        const target = event.target as HTMLElement | null;
        const citation = target?.closest('.iris-citation--has-summary, .iris-citation-group--has-summary') as HTMLElement | null;
        if (!citation || !messagesElement.contains(citation)) {
            return;
        }
        const summary = citation.querySelector('.iris-citation__summary') as HTMLElement | null;
        if (!summary) {
            return;
        }
        const bubble = citation.closest('.bubble-left') as HTMLElement | null;
        const boundary = bubble ?? messagesElement;
        citation.style.setProperty('--iris-citation-shift', '0px');
        const boundaryRect = boundary.getBoundingClientRect();
        const summaryRect = summary.getBoundingClientRect();
        const padding = 8;
        let shift = 0;
        if (summaryRect.left < boundaryRect.left + padding) {
            shift = boundaryRect.left + padding - summaryRect.left;
        } else if (summaryRect.right > boundaryRect.right - padding) {
            shift = boundaryRect.right - summaryRect.right;
        }
        if (shift !== 0) {
            citation.style.setProperty('--iris-citation-shift', `${shift}px`);
        }
    };
    /**
     * Resets citation summary state when the pointer leaves a citation element.
     * @param event The mouseout event.
     */
    private readonly handleCitationMouseOut = (event: MouseEvent) => {
        const messagesElement = this.hoverMessagesElement;
        if (!messagesElement) {
            return;
        }
        const target = event.target as HTMLElement | null;
        const citation = target?.closest('.iris-citation--has-summary, .iris-citation-group--has-summary') as HTMLElement | null;
        if (!citation) {
            return;
        }
        const relatedTarget = event.relatedTarget as HTMLElement | null;
        if (relatedTarget && citation.contains(relatedTarget)) {
            return;
        }
        citation.style.setProperty('--iris-citation-shift', '0px');
        this.resetCitationGroupSummary(citation);
        this.clearCitationFocus(citation);
    };
    /**
     * Handles clicks on citation group navigation buttons.
     * @param event The click event.
     */
    private readonly handleCitationNavigationClick = (event: MouseEvent) => {
        const navButton = this.getCitationNavButton(event.target);
        if (!navButton) {
            return;
        }
        event.preventDefault();
        event.stopPropagation();
        this.navigateCitationGroup(navButton);
    };
    public ButtonType = ButtonType;

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

        effect(() => {
            const messagesElement = this.messagesElement()?.nativeElement as HTMLElement | undefined;
            if (!messagesElement || messagesElement === this.hoverMessagesElement) {
                return;
            }
            if (this.hoverMessagesElement) {
                this.hoverMessagesElement.removeEventListener('mouseover', this.handleCitationMouseOver);
                this.hoverMessagesElement.removeEventListener('mouseout', this.handleCitationMouseOut);
                this.hoverMessagesElement.removeEventListener('click', this.handleCitationNavigationClick);
            }
            this.hoverMessagesElement = messagesElement;
            messagesElement.addEventListener('mouseover', this.handleCitationMouseOver);
            messagesElement.addEventListener('mouseout', this.handleCitationMouseOut);
            messagesElement.addEventListener('click', this.handleCitationNavigationClick);
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
    }

    /**
     * Process messages for display (clone, reverse, format)
     */
    private processMessages(rawMessages: IrisMessage[], citationInfo: IrisCitationMetaDTO[]): IrisMessage[] {
        const processed = _.cloneDeep(rawMessages).reverse();
        processed.forEach((message) => {
            message.content?.forEach((content) => {
                if (!isTextContent(content)) {
                    return;
                }
                content.textContent = content.textContent.replace(/\n\n/g, '\n\u00A0\n');
                content.textContent = content.textContent.replace(/\n/g, '\n\n');
                if (message.sender === IrisSender.LLM) {
                    content.renderedContent = this.renderMessageContent(content, citationInfo);
                }
            });
        });
        return processed;
    }

    /**
     * Renders a message as sanitized HTML and marks it safe for binding.
     * @param content The text content to render.
     * @param citationInfo Metadata used to enrich citation rendering.
     * @returns The rendered HTML marked as SafeHtml for display.
     */
    private renderMessageContent(content: IrisTextMessageContent, citationInfo: IrisCitationMetaDTO[]): SafeHtml {
        const withCitations = this.replaceCitations(content.textContent ?? '', citationInfo);
        const renderedHtml = htmlForMarkdown(withCitations);
        return this.sanitizer.bypassSecurityTrustHtml(renderedHtml);
    }

    /**
     * Replaces citation blocks in text with rendered citation HTML.
     * @param text The raw message text.
     * @param citationInfo Metadata used to enrich citation rendering.
     * @returns The text with citation blocks replaced by HTML.
     */
    private replaceCitations(text: string, citationInfo: IrisCitationMetaDTO[]): string {
        return replaceCitationBlocks(text, citationInfo, {
            renderSingle: (parsed, meta) => this.renderCitationHtml(parsed, meta),
            renderGroup: (parsed, metas) => this.renderCitationGroupHtml(parsed, metas),
        });
    }

    /**
     * Builds the HTML for a single citation, including its summary bubble.
     * @param parsed The parsed citation token.
     * @param meta Optional metadata for lecture citations.
     * @returns The rendered citation HTML.
     */
    private renderCitationHtml(parsed: IrisCitationParsed, meta?: IrisCitationMetaDTO): string {
        const summaryText = this.formatSummary(parsed, meta);
        const label = formatCitationLabel(parsed);
        const typeClass = resolveCitationTypeClass(parsed);
        const hasSummary = summaryText.length > 0;
        const classes = ['iris-citation', typeClass, hasSummary ? 'iris-citation--has-summary' : undefined].filter(Boolean).join(' ');

        const summaryHtml = hasSummary
            ? `<span class="iris-citation__summary"><span class="iris-citation__summary-content"><span class="iris-citation__summary-item is-active">${summaryText}</span></span><span class="iris-citation__summary-icon iris-citation__icon" aria-hidden="true"></span></span>`
            : '';

        return `<span class="${classes}"><span class="iris-citation__icon"></span><span class="iris-citation__text">${label}</span>${summaryHtml}</span>`;
    }

    /**
     * Builds the HTML for a group of citations with navigation controls.
     * @param parsed The parsed citations within the group.
     * @param metas Metadata entries mapped to the parsed citations.
     * @returns The rendered citation group HTML.
     */
    private renderCitationGroupHtml(parsed: IrisCitationParsed[], metas: Array<IrisCitationMetaDTO | undefined>): string {
        const first = parsed[0];
        const label = formatCitationLabel(first);
        const typeClass = resolveCitationTypeClass(first);
        const summaryItems = parsed
            .map((entry, index) => {
                const summaryText = this.formatGroupSummaryItem(entry, metas[index]);
                if (!summaryText) {
                    return '';
                }
                const activeClass = index === 0 ? ' is-active' : '';
                return `<span class="iris-citation__summary-item${activeClass}">${summaryText}</span>`;
            })
            .filter((value) => value.length > 0);
        const hasSummary = summaryItems.length > 0;
        const classes = ['iris-citation-group', typeClass, hasSummary ? 'iris-citation-group--has-summary' : undefined].filter(Boolean).join(' ');
        const count = parsed.length - 1;
        const navHtml = summaryItems.length > 1 ? this.renderCitationNavHtml(summaryItems.length) : '';
        const summaryHtml = hasSummary
            ? `<span class="iris-citation__summary"><span class="iris-citation__summary-content">${summaryItems.join(
                  '',
              )}</span>${navHtml}<span class="iris-citation__summary-icon iris-citation__icon" aria-hidden="true"></span></span>`
            : '';

        return `<span class="${classes}"><span class="iris-citation ${typeClass}"><span class="iris-citation__icon"></span><span class="iris-citation__text">${label}</span></span><span class="iris-citation__count">+${count}</span>${summaryHtml}</span>`;
    }

    /**
     * Generates the navigation markup for cycling through citation summaries.
     * @param count The number of summary items in the group.
     * @returns The rendered navigation HTML.
     */
    private renderCitationNavHtml(count: number): string {
        return `<span class="iris-citation__nav">
            <span class="iris-citation__nav-button iris-citation__nav-button--prev" role="button" tabindex="0" aria-label="Previous citation">
                <span class="iris-citation__nav-icon iris-citation__nav-icon--prev" aria-hidden="true"></span>
            </span>
            <span class="iris-citation__nav-count">1/${count}</span>
            <span class="iris-citation__nav-button iris-citation__nav-button--next" role="button" tabindex="0" aria-label="Next citation">
                <span class="iris-citation__nav-icon iris-citation__nav-icon--next" aria-hidden="true"></span>
            </span>
        </span>`;
    }

    /**
     * Resolves the navigation button element from an event target.
     * @param target The event target to inspect.
     * @returns The matching nav button or null when not found.
     */
    private getCitationNavButton(target: EventTarget | null): HTMLElement | null {
        const rawTarget = target as Node | null;
        const element = rawTarget instanceof HTMLElement ? rawTarget : rawTarget?.parentElement;
        return element?.closest('.iris-citation__nav-button') as HTMLElement | null;
    }

    /**
     * Moves the active summary within a citation group based on nav button direction.
     * @param navButton The navigation button that was activated.
     */
    private navigateCitationGroup(navButton: HTMLElement) {
        const citationGroup = navButton.closest('.iris-citation-group') as HTMLElement | null;
        if (!citationGroup) {
            return;
        }
        const summaryItems = Array.from(citationGroup.querySelectorAll<HTMLElement>('.iris-citation__summary-item'));
        if (summaryItems.length === 0) {
            return;
        }
        const currentIndex = summaryItems.findIndex((item) => item.classList.contains('is-active'));
        const normalizedIndex = currentIndex < 0 ? 0 : currentIndex;
        const isPrev = navButton.classList.contains('iris-citation__nav-button--prev');
        const delta = isPrev ? -1 : 1;
        const nextIndex = (normalizedIndex + delta + summaryItems.length) % summaryItems.length;
        this.updateCitationGroupSummary(citationGroup, nextIndex);
    }

    /**
     * Formats a summary line for a grouped citation, falling back to the label.
     * @param parsed The parsed citation entry.
     * @param meta Optional metadata for lecture citations.
     * @returns The formatted summary or label.
     */
    private formatGroupSummaryItem(parsed: IrisCitationParsed, meta?: IrisCitationMetaDTO): string {
        const summaryText = this.formatSummary(parsed, meta);
        if (summaryText) {
            return summaryText;
        }
        return formatCitationLabel(parsed);
    }

    /**
     * Formats the summary HTML for a citation, including lecture metadata.
     * @param parsed The parsed citation entry.
     * @param meta Optional metadata used for lecture summaries.
     * @returns The formatted summary HTML.
     */
    private formatSummary(parsed: IrisCitationParsed, meta?: IrisCitationMetaDTO): string {
        const fallbackKeyword = parsed.type === 'F' ? 'FAQ' : 'Source';
        const keywordValue = escapeHtml(parsed.keyword?.trim() || fallbackKeyword);
        const summaryValue = parsed.summary.trim() ? escapeHtml(parsed.summary.trim()) : '';
        const lines: string[] = [keywordValue];

        if (summaryValue) {
            lines.push(summaryValue);
        }

        const filtered = lines.filter(Boolean);
        if (filtered.length === 0) {
            return '';
        }

        const [keywordLine, ...restLines] = filtered;
        const rest = restLines.length > 0 ? `<br />${restLines.join('<br />')}` : '';
        const metaLines: string[] = [];
        if (parsed.type === 'L') {
            const lectureTitle = meta?.lectureTitle?.trim();
            if (lectureTitle) {
                const lectureLabel = this.translateService.instant('artemisApp.iris.citations.lectureLabel');
                metaLines.push(`<span class="iris-citation__summary-meta">${lectureLabel}: "${escapeHtml(lectureTitle)}"</span>`);
            }
            const unitTitle = meta?.lectureUnitTitle?.trim();
            if (unitTitle) {
                const lectureUnitLabel = this.translateService.instant('artemisApp.iris.citations.lectureUnitLabel');
                metaLines.push(`<span class="iris-citation__summary-meta">${lectureUnitLabel}: "${escapeHtml(unitTitle)}"</span>`);
            }
        }
        const metaHtml = metaLines.length > 0 ? `<br />${metaLines.join('<br />')}` : '';

        return `<span class="iris-citation__summary-keyword">${keywordLine}</span>${rest}${metaHtml}`;
    }

    /**
     * Updates which summary item is active and syncs the nav counter.
     * @param citationGroup The citation group container element.
     * @param nextIndex The index of the summary item to activate.
     */
    private updateCitationGroupSummary(citationGroup: HTMLElement, nextIndex: number) {
        const summaryItems = Array.from(citationGroup.querySelectorAll<HTMLElement>('.iris-citation__summary-item'));
        if (summaryItems.length === 0) {
            return;
        }
        summaryItems.forEach((item, index) => {
            item.classList.toggle('is-active', index === nextIndex);
        });
        const navCount = citationGroup.querySelector<HTMLElement>('.iris-citation__nav-count');
        if (navCount) {
            navCount.textContent = `${nextIndex + 1}/${summaryItems.length}`;
        }
    }

    /**
     * Resets the citation group summary to the first item.
     * @param citationElement The citation element that triggered the reset.
     */
    private resetCitationGroupSummary(citationElement: HTMLElement) {
        const citationGroup = citationElement.classList.contains('iris-citation-group') ? citationElement : citationElement.closest('.iris-citation-group');
        if (!(citationGroup instanceof HTMLElement)) {
            return;
        }
        this.updateCitationGroupSummary(citationGroup, 0);
    }

    /**
     * Clears focus from citation navigation to prevent sticky summaries after mouseout.
     * @param citationElement The citation element that is losing hover state.
     */
    private clearCitationFocus(citationElement: HTMLElement) {
        const activeElement = document.activeElement;
        if (activeElement instanceof HTMLElement && citationElement.contains(activeElement)) {
            activeElement.blur();
        }
    }

    /**
     * Enables animations after view init and cleans up citation listeners on destroy.
     */
    ngAfterViewInit() {
        setTimeout(() => (this.shouldAnimate = true), 500);
        this.destroyRef.onDestroy(() => {
            if (!this.hoverMessagesElement) {
                return;
            }
            this.hoverMessagesElement.removeEventListener('mouseover', this.handleCitationMouseOver);
            this.hoverMessagesElement.removeEventListener('mouseout', this.handleCitationMouseOut);
            this.hoverMessagesElement.removeEventListener('click', this.handleCitationNavigationClick);
        });
    }

    /**
     * Handles the send button click event and sends the user's message.
     */
    onSend(): void {
        this.chatService.messagesRead();
        const content = this.newMessageTextContent();
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
        const bufferForSpaceBetweenLines = 4;
        const lineHeight = parseInt(getComputedStyle(textarea).lineHeight, 10) + bufferForSpaceBetweenLines;
        const maxRows = 3;
        const maxHeight = lineHeight * maxRows;

        textarea.style.height = `${Math.min(textarea.scrollHeight, maxHeight)}px`;

        this.adjustScrollButtonPosition(Math.min(textarea.scrollHeight, maxHeight) / lineHeight);
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
        this.chatService.switchToSession(session);
    }

    setChatHistoryVisibility(isOpen: boolean) {
        this.isChatHistoryOpen.set(isOpen);
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
        this.searchValue.set(searchValue.trim().toLowerCase());
    }

    private getFilteredSessions(): IrisSessionDTO[] {
        const search = this.searchValue();
        if (!search) {
            return this.chatSessions();
        }
        return this.chatSessions().filter((s) => (s.title ?? '').toLowerCase().includes(search));
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
