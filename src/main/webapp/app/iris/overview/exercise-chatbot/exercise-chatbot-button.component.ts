import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { ActivatedRoute, Params } from '@angular/router';
import { IrisChatbotWidgetComponent } from 'app/iris/overview/exercise-chatbot/widget/chatbot-widget.component';
import { EMPTY, filter, of, switchMap } from 'rxjs';
import { faAngleDoubleDown, faCircle } from '@fortawesome/free-solid-svg-icons';
import { IrisLogoLookDirection, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { isTextContent } from 'app/iris/shared/entities/iris-content-type.model';
import { IrisCitationMetaDTO } from 'app/iris/shared/entities/iris-citation-meta-dto.model';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';

const CITATION_REGEX = /\[cite:[^\]]+\]/g;

@Component({
    selector: 'jhi-exercise-chatbot-button',
    templateUrl: './exercise-chatbot-button.component.html',
    styleUrls: ['./exercise-chatbot-button.component.scss'],
    imports: [NgClass, TranslateDirective, FaIconComponent, IrisLogoComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisExerciseChatbotButtonComponent {
    protected readonly faCircle = faCircle;
    protected readonly faAngleDoubleDown = faAngleDoubleDown;

    private readonly dialog = inject(MatDialog);
    private readonly overlay = inject(Overlay);
    private readonly chatService = inject(IrisChatService);
    private readonly route = inject(ActivatedRoute);
    private readonly destroyRef = inject(DestroyRef);

    private readonly CHAT_BUBBLE_TIMEOUT = 10000;

    protected readonly IrisLogoLookDirection = IrisLogoLookDirection;
    protected readonly IrisLogoSize = IrisLogoSize;

    readonly mode = input.required<ChatServiceMode>();

    dialogRef: MatDialogRef<IrisChatbotWidgetComponent> | undefined = undefined;

    // UI state as signals for OnPush change detection
    readonly chatOpen = signal(false);
    readonly isOverflowing = signal(false);
    readonly newIrisMessage = signal<ChatBubbleMessage | undefined>(undefined);

    // Convert numNewMessages observable to signal
    private readonly numNewMessages = toSignal(this.chatService.numNewMessages, { initialValue: 0 });
    readonly hasNewMessages = computed(() => this.numNewMessages() > 0);
    private readonly citationInfo = toSignal(this.chatService.currentCitationInfo(), { initialValue: [] as IrisCitationMetaDTO[] });

    // Convert newIrisMessage observable to signal for tracking incoming messages
    private readonly latestIrisMessageContent = toSignal(
        this.chatService.newIrisMessage.pipe(
            filter((msg) => !!msg),
            switchMap((msg) => {
                if (msg!.content && msg!.content.length > 0 && isTextContent(msg!.content[0])) {
                    return of({ text: msg!.content[0].textContent });
                }
                return EMPTY;
            }),
        ),
        { initialValue: undefined },
    );

    // Convert route params to signals for proper reactive handling
    // (route.params emits synchronously on subscription, before inputs are set in constructor)
    private readonly routeParams = toSignal(this.route.params, { initialValue: {} as Params });
    private readonly queryParams = toSignal(this.route.queryParams, { initialValue: {} as Params });

    private bubbleTimeoutId: ReturnType<typeof setTimeout> | undefined;

    // Track the last message shown in bubble to prevent re-showing when chatOpen changes
    // Not a signal because it's internal bookkeeping read only with untracked() - no reactivity needed
    private lastShownBubbleMessage: string | undefined;

    readonly chatBubble = viewChild<ElementRef>('chatBubble');

    constructor() {
        // Register cleanup for dialog
        this.destroyRef.onDestroy(() => {
            if (this.dialogRef) {
                this.dialogRef.close();
            }
            if (this.bubbleTimeoutId) {
                clearTimeout(this.bubbleTimeoutId);
            }
        });

        // Effect to switch chat context when mode or route params change
        // Using effect instead of subscription ensures inputs are set before first run
        effect(() => {
            const mode = this.mode();
            const params = this.routeParams();
            const rawId = mode === ChatServiceMode.LECTURE ? params['lectureId'] : params['exerciseId'];
            if (rawId) {
                const id = parseInt(rawId, 10);
                if (!Number.isNaN(id)) {
                    // Use untracked to avoid re-running this effect when chatService state changes
                    untracked(() => this.chatService.switchTo(mode, id));
                }
            }
        });

        // Effect to auto-open chat when irisQuestion query param is present
        // Use untracked for chatOpen to prevent re-running when chat state changes
        effect(() => {
            const params = this.queryParams();
            const isChatClosed = untracked(() => !this.chatOpen());
            if (params['irisQuestion'] && isChatClosed) {
                untracked(() => this.openChat());
            }
        });

        // Handle new iris messages with bubble display and timeout
        // Only show bubble for genuinely NEW messages to prevent re-showing when chatOpen changes
        effect(() => {
            const message = this.latestIrisMessageContent();

            // Use untracked for chatOpen so effect only triggers on new messages, not chat state changes
            const isChatClosed = untracked(() => !this.chatOpen());

            // Only show if: message exists, chat is closed, AND it's a new message we haven't shown
            if (message?.text && isChatClosed && message.text !== this.lastShownBubbleMessage) {
                this.lastShownBubbleMessage = message.text;
                this.newIrisMessage.set(message);
                setTimeout(() => this.checkOverflow(), 0);

                // Clear any existing timeout
                if (this.bubbleTimeoutId) {
                    clearTimeout(this.bubbleTimeoutId);
                }

                // Auto-hide the bubble after timeout
                this.bubbleTimeoutId = setTimeout(() => {
                    this.newIrisMessage.set(undefined);
                    this.isOverflowing.set(false);
                }, this.CHAT_BUBBLE_TIMEOUT);
            }
        });
    }

    /**
     * Handles the click event of the button.
     * If the chat is open, it resets the number of new messages, closes the dialog, and sets chatOpen to false.
     * If the chat is closed, it opens the chat dialog and sets chatOpen to true.
     */
    public handleButtonClick() {
        if (this.chatOpen() && this.dialogRef) {
            this.dialog.closeAll();
            this.chatOpen.set(false);
        } else {
            this.openChat();
            this.chatOpen.set(true);
        }
    }

    /**
     * Checks if the chat bubble is overflowing and sets isOverflowing to true if it is.
     */
    public checkOverflow() {
        const element = this.chatBubble()?.nativeElement;
        this.isOverflowing.set(!!element && element.scrollHeight > element.clientHeight);
    }

    /**
     * Opens the chat dialog using MatDialog.
     * Sets the configuration options for the dialog, including position, size, and data.
     */
    public openChat() {
        this.chatOpen.set(true);
        this.newIrisMessage.set(undefined);
        this.isOverflowing.set(false);
        this.lastShownBubbleMessage = undefined;
        this.dialogRef = this.dialog.open(IrisChatbotWidgetComponent, {
            hasBackdrop: false,
            scrollStrategy: this.overlay.scrollStrategies.noop(),
            position: { bottom: '0px', right: '0px' },
            disableClose: true,
        });
        this.dialogRef
            .afterClosed()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.handleDialogClose());
    }

    private handleDialogClose() {
        this.chatOpen.set(false);
        this.newIrisMessage.set(undefined);
    }

    renderChatBubbleMessage(message?: ChatBubbleMessage): string {
        if (!message?.text) {
            return '';
        }
        const withCitations = this.replaceCitationsPreview(message.text, this.citationInfo());
        return htmlForMarkdown(withCitations);
    }

    private replaceCitationsPreview(text: string, citationInfo: IrisCitationMetaDTO[]): string {
        if (!text || !text.includes('[cite:')) {
            return text;
        }
        const citationMap = new Map<number, IrisCitationMetaDTO>(citationInfo.map((citation) => [citation.entityId, citation]));
        const tokens: Array<{ type: 'text'; value: string } | { type: 'citation'; value: string }> = [];
        let lastIndex = 0;
        for (const match of text.matchAll(CITATION_REGEX)) {
            const index = match.index ?? 0;
            if (index > lastIndex) {
                tokens.push({ type: 'text', value: text.slice(lastIndex, index) });
            }
            tokens.push({ type: 'citation', value: match[0] });
            lastIndex = index + match[0].length;
        }
        if (lastIndex < text.length) {
            tokens.push({ type: 'text', value: text.slice(lastIndex) });
        }

        const rendered: string[] = [];
        let i = 0;
        while (i < tokens.length) {
            const token = tokens[i];
            if (token.type === 'text') {
                rendered.push(token.value);
                i += 1;
                continue;
            }
            const citationGroup: string[] = [token.value];
            let j = i + 1;
            let pendingWhitespace = '';
            while (j < tokens.length) {
                const nextToken = tokens[j];
                if (nextToken.type === 'text' && /^\s*$/.test(nextToken.value)) {
                    pendingWhitespace += nextToken.value;
                    j += 1;
                    continue;
                }
                if (nextToken.type === 'citation') {
                    citationGroup.push(nextToken.value);
                    pendingWhitespace = '';
                    j += 1;
                    continue;
                }
                break;
            }

            if (citationGroup.length > 1) {
                const parsed = citationGroup.map((raw) => this.parseCitation(raw)).filter((value): value is IrisCitationParsed => Boolean(value));
                if (parsed.length === 0) {
                    rendered.push(citationGroup.join(''));
                } else {
                    const metas = parsed.map((entry) => (entry.type === 'L' ? citationMap.get(entry.entityId) : undefined));
                    rendered.push(this.renderCitationGroupPreviewHtml(parsed, metas));
                }
                if (pendingWhitespace) {
                    rendered.push(pendingWhitespace);
                }
                i = j;
                continue;
            }

            const parsed = this.parseCitation(token.value);
            if (!parsed) {
                rendered.push(token.value);
                i = j;
                continue;
            }
            const meta = parsed.type === 'L' ? citationMap.get(parsed.entityId) : undefined;
            rendered.push(this.renderCitationPreviewHtml(parsed, meta));
            if (pendingWhitespace) {
                rendered.push(pendingWhitespace);
            }
            i = j;
        }

        return rendered.join('');
    }

    private parseCitation(raw: string): IrisCitationParsed | undefined {
        const content = raw.slice(6, -1); // strip "[cite:" and trailing "]"
        const parts = content.split(':');
        if (parts.length < 2) {
            return undefined;
        }

        const type = parts[0];
        if (!this.isCitationType(type)) {
            return undefined;
        }

        const entityId = Number(parts[1]);
        if (!Number.isFinite(entityId)) {
            return undefined;
        }

        const page = parts[2] ?? '';
        const start = parts[3] ?? '';
        const end = parts[4] ?? '';
        const keyword = parts[5] ?? '';

        return { type, entityId, page, start, end, keyword };
    }

    private isCitationType(value?: string): value is 'L' | 'F' {
        return value === 'L' || value === 'F';
    }

    private renderCitationPreviewHtml(parsed: IrisCitationParsed, meta?: IrisCitationMetaDTO): string {
        const label = this.formatCitationLabel(parsed, meta);
        const typeClass = this.resolveCitationTypeClass(parsed);
        const classes = ['iris-citation', typeClass].filter(Boolean).join(' ');
        return `<span class="${classes}"><span class="iris-citation__icon"></span><span class="iris-citation__text">${label}</span></span>`;
    }

    private renderCitationGroupPreviewHtml(parsed: IrisCitationParsed[], metas: Array<IrisCitationMetaDTO | undefined>): string {
        const first = parsed[0];
        const firstMeta = metas[0];
        const label = this.formatCitationLabel(first, firstMeta);
        const typeClass = this.resolveCitationTypeClass(first);
        const classes = ['iris-citation-group', typeClass].filter(Boolean).join(' ');
        const count = parsed.length - 1;
        return `<span class="${classes}"><span class="iris-citation ${typeClass}"><span class="iris-citation__icon"></span><span class="iris-citation__text">${label}</span></span><span class="iris-citation__count">+${count}</span></span>`;
    }

    private formatCitationLabel(parsed: IrisCitationParsed, meta?: IrisCitationMetaDTO): string {
        const keyword = parsed.keyword?.trim();
        const fallback = parsed.type === 'F' ? 'FAQ' : 'Source';
        const label = keyword || fallback;
        return this.escapeHtml(label);
    }

    private resolveCitationTypeClass(parsed: IrisCitationParsed): string {
        if (parsed.type === 'F') {
            return 'iris-citation--faq';
        }
        if (parsed.start || parsed.end) {
            return 'iris-citation--video';
        }
        if (parsed.page) {
            return 'iris-citation--slide';
        }
        return 'iris-citation--source';
    }

    private escapeHtml(text: string): string {
        return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }
}

type ChatBubbleMessage = {
    text: string;
};

type IrisCitationParsed = {
    type: 'L' | 'F';
    entityId: number;
    page: string;
    start: string;
    end: string;
    keyword: string;
};
