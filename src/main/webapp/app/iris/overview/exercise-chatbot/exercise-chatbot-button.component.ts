import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { ActivatedRoute, Params } from '@angular/router';
import { IrisChatbotWidgetComponent } from 'app/iris/overview/exercise-chatbot/widget/chatbot-widget.component';
import { EMPTY, filter, map, of, switchMap } from 'rxjs';
import { faCircle } from '@fortawesome/free-solid-svg-icons';
import { IrisLogoLookDirection, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { isTextContent } from 'app/iris/shared/entities/iris-content-type.model';
import { removeCitationBlocks } from 'app/iris/overview/citation-text/iris-citation-text.util';
import { IrisStageDTO, IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-exercise-chatbot-button',
    templateUrl: './exercise-chatbot-button.component.html',
    styleUrls: ['./exercise-chatbot-button.component.scss'],
    imports: [FaIconComponent, IrisLogoComponent, HtmlForMarkdownPipe, TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisExerciseChatbotButtonComponent {
    protected readonly faCircle = faCircle;
    // Must match the number of keys in i18n exerciseChatbot.statusIndicator.labels
    private static readonly STATUS_LABEL_COUNT = 2;
    private static readonly STATUS_CYCLE_INTERVAL = 3000;
    private static readonly SLIDE_ANIMATION_DURATION = 300;

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
    readonly newIrisMessage = signal<string | undefined>(undefined);

    // Convert numNewMessages observable to signal
    private readonly numNewMessages = toSignal(this.chatService.numNewMessages, { initialValue: 0 });
    readonly hasNewMessages = computed(() => this.numNewMessages() > 0);

    // Convert stages observable to signal for processing indicator
    private readonly currentStages = toSignal(this.chatService.stages, { initialValue: [] as IrisStageDTO[] });
    readonly isProcessing = computed(() => this.currentStages().some((s) => s.state === IrisStageStateDTO.IN_PROGRESS || s.state === IrisStageStateDTO.NOT_STARTED));

    // Status label cycling
    readonly statusLabelIndex = signal(0);
    readonly statusLabelAnimState = signal<'slide-in' | 'slide-out'>('slide-in');
    readonly statusLabelKey = computed(() => 'artemisApp.exerciseChatbot.statusIndicator.labels.' + this.statusLabelIndex());
    private statusCycleIntervalId: ReturnType<typeof setInterval> | undefined;
    private slideTimeoutId: ReturnType<typeof setTimeout> | undefined;
    private shuffledLabelOrder: number[] = [];
    private shuffledPosition = 0;
    private wasProcessing = false;

    // Convert newIrisMessage observable to signal for tracking incoming messages
    private readonly latestIrisMessageContent = toSignal(
        this.chatService.newIrisMessage.pipe(
            filter((msg) => !!msg),
            switchMap((msg) => {
                if (msg!.content && msg!.content.length > 0 && isTextContent(msg!.content[0])) {
                    return of(msg!.content[0].textContent);
                }
                return EMPTY;
            }),
            map((textContent) => removeCitationBlocks(textContent)),
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

    private readonly shouldReopenChat = toSignal(this.chatService.shouldReopenChat$, { initialValue: false });

    constructor() {
        // Register cleanup for dialog
        this.destroyRef.onDestroy(() => {
            if (this.dialogRef) {
                this.dialogRef.close();
            }
            if (this.bubbleTimeoutId) {
                clearTimeout(this.bubbleTimeoutId);
            }
            if (this.statusCycleIntervalId) {
                clearInterval(this.statusCycleIntervalId);
            }
            if (this.slideTimeoutId) {
                clearTimeout(this.slideTimeoutId);
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
            if (message && isChatClosed && message !== this.lastShownBubbleMessage) {
                this.lastShownBubbleMessage = message;
                this.newIrisMessage.set(message);

                // Clear any existing timeout
                if (this.bubbleTimeoutId) {
                    clearTimeout(this.bubbleTimeoutId);
                }

                // Auto-hide the bubble after timeout
                this.bubbleTimeoutId = setTimeout(() => {
                    this.newIrisMessage.set(undefined);
                }, this.CHAT_BUBBLE_TIMEOUT);
            }
        });

        // Cycle status labels while processing (only react to transitions)
        effect(() => {
            const processing = this.isProcessing();

            if (processing && !this.wasProcessing) {
                // Transition: not processing → processing — start the cycle
                this.shuffleLabelOrder();
                this.shuffledPosition = 0;
                this.statusLabelIndex.set(this.shuffledLabelOrder[0]);
                this.statusLabelAnimState.set('slide-in');
                this.statusCycleIntervalId = setInterval(() => {
                    // Phase 1: slide out old text
                    this.statusLabelAnimState.set('slide-out');
                    this.slideTimeoutId = setTimeout(() => {
                        // Phase 2: swap text and slide in new text
                        this.shuffledPosition = (this.shuffledPosition + 1) % IrisExerciseChatbotButtonComponent.STATUS_LABEL_COUNT;
                        if (this.shuffledPosition === 0) {
                            this.shuffleLabelOrder();
                        }
                        this.statusLabelIndex.set(this.shuffledLabelOrder[this.shuffledPosition]);
                        this.statusLabelAnimState.set('slide-in');
                    }, IrisExerciseChatbotButtonComponent.SLIDE_ANIMATION_DURATION);
                }, IrisExerciseChatbotButtonComponent.STATUS_CYCLE_INTERVAL);
            } else if (!processing && this.wasProcessing) {
                // Transition: processing → not processing — stop the cycle
                if (this.statusCycleIntervalId) {
                    clearInterval(this.statusCycleIntervalId);
                    this.statusCycleIntervalId = undefined;
                }
                if (this.slideTimeoutId) {
                    clearTimeout(this.slideTimeoutId);
                    this.slideTimeoutId = undefined;
                }
            }

            this.wasProcessing = processing;
        });

        effect(() => {
            const shouldReopen = this.shouldReopenChat();
            if (shouldReopen && !untracked(() => this.chatOpen())) {
                untracked(() => {
                    this.openChat();
                    this.chatService.setShouldReopenChat(false);
                });
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
     * Opens the chat dialog using MatDialog.
     * Sets the configuration options for the dialog, including position, size, and data.
     */
    public openChat() {
        this.chatOpen.set(true);
        this.newIrisMessage.set(undefined);
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

    private shuffleLabelOrder(): void {
        const lastShown = this.shuffledLabelOrder.length > 0 ? this.shuffledLabelOrder[this.shuffledLabelOrder.length - 1] : -1;
        do {
            this.shuffledLabelOrder = Array.from({ length: IrisExerciseChatbotButtonComponent.STATUS_LABEL_COUNT }, (_, i) => i);
            for (let i = this.shuffledLabelOrder.length - 1; i > 0; i--) {
                const j = Math.floor(Math.random() * (i + 1));
                [this.shuffledLabelOrder[i], this.shuffledLabelOrder[j]] = [this.shuffledLabelOrder[j], this.shuffledLabelOrder[i]];
            }
        } while (this.shuffledLabelOrder[0] === lastShown && IrisExerciseChatbotButtonComponent.STATUS_LABEL_COUNT > 1);
    }
}
