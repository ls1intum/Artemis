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
import { TranslateService } from '@ngx-translate/core';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { createStageRotation } from 'app/iris/overview/iris-stage-rotation.util';

@Component({
    selector: 'jhi-exercise-chatbot-button',
    templateUrl: './exercise-chatbot-button.component.html',
    styleUrls: ['./exercise-chatbot-button.component.scss'],
    imports: [FaIconComponent, IrisLogoComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisExerciseChatbotButtonComponent {
    protected readonly faCircle = faCircle;

    private readonly dialog = inject(MatDialog);
    private readonly overlay = inject(Overlay);
    private readonly chatService = inject(IrisChatService);
    private readonly route = inject(ActivatedRoute);
    private readonly destroyRef = inject(DestroyRef);
    private readonly translateService = inject(TranslateService);
    private readonly currentLocale = getCurrentLocaleSignal(this.translateService);

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
    private readonly visibleStages = computed(() => this.currentStages().filter((s) => !s.internal));

    // Active stage: first visible stage that is not completed (ERROR, NOT_STARTED, IN_PROGRESS are all "unfinished")
    readonly activeStage = computed(() => this.visibleStages().find((s) => s.state !== IrisStageStateDTO.DONE && s.state !== IrisStageStateDTO.SKIPPED));
    readonly isProcessing = computed(() => {
        const stage = this.activeStage();
        return stage?.state === IrisStageStateDTO.IN_PROGRESS || stage?.state === IrisStageStateDTO.NOT_STARTED;
    });
    private readonly stageRotation = createStageRotation(this.translateService, this.destroyRef);
    readonly displayName = this.stageRotation.displayName;
    readonly animToggle = this.stageRotation.animToggle;

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
        // Register cleanup for dialog and rotation interval
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
                const entityId = parseInt(rawId, 10);
                if (!Number.isNaN(entityId)) {
                    // Use untracked to avoid re-running this effect when chatService state changes
                    untracked(() => {
                        const courseId = this.chatService.getCourseId();
                        if (courseId) {
                            this.chatService.switchTo(mode, courseId, entityId);
                        }
                    });
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

        // Display name effect — show stage message, rotate labels during IN_PROGRESS
        effect(() => {
            const stage = this.activeStage();
            this.currentLocale();
            this.stageRotation.update(stage);
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
            this.dialogRef.close();
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
}
