import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { ActivatedRoute } from '@angular/router';
import { IrisChatbotWidgetComponent } from 'app/iris/overview/exercise-chatbot/widget/chatbot-widget.component';
import { EMPTY, filter, of, switchMap } from 'rxjs';
import { faAngleDoubleDown, faChevronDown, faCircle } from '@fortawesome/free-solid-svg-icons';
import { IrisLogoLookDirection, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisTextMessageContent } from 'app/iris/shared/entities/iris-content-type.model';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@Component({
    selector: 'jhi-exercise-chatbot-button',
    templateUrl: './exercise-chatbot-button.component.html',
    styleUrls: ['./exercise-chatbot-button.component.scss'],
    imports: [NgClass, TranslateDirective, FaIconComponent, IrisLogoComponent, HtmlForMarkdownPipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisExerciseChatbotButtonComponent {
    protected readonly faCircle = faCircle;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faAngleDoubleDown = faAngleDoubleDown;

    private readonly dialog = inject(MatDialog);
    private readonly overlay = inject(Overlay);
    private readonly chatService = inject(IrisChatService);
    private readonly route = inject(ActivatedRoute);
    private readonly destroyRef = inject(DestroyRef);

    private readonly CHAT_BUBBLE_TIMEOUT = 10000;

    protected readonly IrisLogoLookDirection = IrisLogoLookDirection;
    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly IrisTextMessageContent = IrisTextMessageContent;

    readonly mode = input.required<ChatServiceMode>();

    dialogRef: MatDialogRef<IrisChatbotWidgetComponent> | undefined = undefined;

    // UI state as signals for OnPush change detection
    readonly chatOpen = signal(false);
    readonly isOverflowing = signal(false);
    readonly newIrisMessage = signal<string | undefined>(undefined);

    // Convert numNewMessages observable to signal
    private readonly numNewMessages = toSignal(this.chatService.numNewMessages, { initialValue: 0 });
    readonly hasNewMessages = computed(() => this.numNewMessages() > 0);

    // Convert newIrisMessage observable to signal for tracking incoming messages
    private readonly latestIrisMessageContent = toSignal(
        this.chatService.newIrisMessage.pipe(
            filter((msg) => !!msg),
            switchMap((msg) => {
                if (msg!.content && msg!.content.length > 0) {
                    return of((msg!.content[0] as IrisTextMessageContent).textContent);
                }
                return EMPTY;
            }),
        ),
        { initialValue: undefined },
    );

    private bubbleTimeoutId: ReturnType<typeof setTimeout> | undefined;

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

        // Subscribe to route params and switch chat context
        this.route.params.pipe(takeUntilDestroyed()).subscribe((params) => {
            const rawId = this.mode() == ChatServiceMode.LECTURE ? params['lectureId'] : params['exerciseId'];
            const id = parseInt(rawId, 10);
            this.chatService.switchTo(this.mode(), id);
        });

        // Subscribe to query params to auto-open chat
        this.route.queryParams?.pipe(takeUntilDestroyed()).subscribe((params: any) => {
            if (params.irisQuestion) {
                this.openChat();
            }
        });

        // Handle new iris messages with bubble display and timeout
        effect(() => {
            const message = this.latestIrisMessageContent();
            if (message && !this.chatOpen()) {
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
