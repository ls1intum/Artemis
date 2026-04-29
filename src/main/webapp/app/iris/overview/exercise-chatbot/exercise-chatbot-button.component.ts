import { ChangeDetectionStrategy, Component, DestroyRef, ViewContainerRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { ActivatedRoute, Params } from '@angular/router';
import { IrisChatbotWidgetComponent } from 'app/iris/overview/exercise-chatbot/widget/chatbot-widget.component';
import { EMPTY, filter, map, of, switchMap } from 'rxjs';
import { faCircle } from '@fortawesome/free-solid-svg-icons';
import { IrisLogoLookDirection, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-chat-mode.model';
import { IrisChatControllerService } from 'app/iris/overview/services/iris-chat-controller.service';
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
    providers: [IrisChatControllerService],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisExerciseChatbotButtonComponent {
    protected readonly faCircle = faCircle;

    private readonly dialog = inject(MatDialog);
    private readonly overlay = inject(Overlay);
    private readonly controller = inject(IrisChatControllerService);
    private readonly viewContainerRef = inject(ViewContainerRef);
    private readonly route = inject(ActivatedRoute);
    private readonly destroyRef = inject(DestroyRef);
    private readonly translateService = inject(TranslateService);
    private readonly currentLocale = getCurrentLocaleSignal(this.translateService);

    private readonly CHAT_BUBBLE_TIMEOUT = 10000;

    protected readonly IrisLogoLookDirection = IrisLogoLookDirection;
    protected readonly IrisLogoSize = IrisLogoSize;

    readonly mode = input.required<ChatServiceMode>();
    readonly courseId = input.required<number>();

    dialogRef: MatDialogRef<IrisChatbotWidgetComponent> | undefined = undefined;

    readonly chatOpen = signal(false);
    readonly newIrisMessage = signal<string | undefined>(undefined);

    private readonly numNewMessages = toSignal(this.controller.numNewMessages, { initialValue: 0 });
    readonly hasNewMessages = computed(() => this.numNewMessages() > 0);

    private readonly currentStages = toSignal(this.controller.stages, { initialValue: [] as IrisStageDTO[] });
    private readonly visibleStages = computed(() => this.currentStages().filter((s) => !s.internal));

    readonly activeStage = computed(() => this.visibleStages().find((s) => s.state !== IrisStageStateDTO.DONE && s.state !== IrisStageStateDTO.SKIPPED));
    readonly isProcessing = computed(() => {
        const stage = this.activeStage();
        return stage?.state === IrisStageStateDTO.IN_PROGRESS || stage?.state === IrisStageStateDTO.NOT_STARTED;
    });
    private readonly stageRotation = createStageRotation(this.translateService, this.destroyRef);
    readonly displayName = this.stageRotation.displayName;
    readonly animToggle = this.stageRotation.animToggle;

    private readonly latestIrisMessageContent = toSignal(
        this.controller.newIrisMessage.pipe(
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

    private readonly routeParams = toSignal(this.route.params, { initialValue: {} as Params });
    private readonly queryParams = toSignal(this.route.queryParams, { initialValue: {} as Params });

    private bubbleTimeoutId: ReturnType<typeof setTimeout> | undefined;

    private lastShownBubbleMessage: string | undefined;

    private readonly shouldReopenChat = toSignal(this.controller.shouldReopenChat$, { initialValue: false });

    constructor() {
        this.destroyRef.onDestroy(() => {
            if (this.dialogRef) {
                this.dialogRef.close();
            }
            if (this.bubbleTimeoutId) {
                clearTimeout(this.bubbleTimeoutId);
            }
        });

        effect(() => {
            const mode = this.mode();
            const courseId = this.courseId();
            const params = this.routeParams();
            const rawId = mode === ChatServiceMode.LECTURE ? params['lectureId'] : params['exerciseId'];
            if (rawId) {
                const id = parseInt(rawId, 10);
                if (!Number.isNaN(id)) {
                    untracked(() => this.controller.setContext(courseId, mode, id));
                }
            }
        });

        effect(() => {
            const params = this.queryParams();
            const isChatClosed = untracked(() => !this.chatOpen());
            if (params['irisQuestion'] && isChatClosed) {
                untracked(() => this.openChat());
            }
        });

        effect(() => {
            const message = this.latestIrisMessageContent();
            const isChatClosed = untracked(() => !this.chatOpen());

            if (message && isChatClosed && message !== this.lastShownBubbleMessage) {
                this.lastShownBubbleMessage = message;
                this.newIrisMessage.set(message);

                if (this.bubbleTimeoutId) {
                    clearTimeout(this.bubbleTimeoutId);
                }

                this.bubbleTimeoutId = setTimeout(() => {
                    this.newIrisMessage.set(undefined);
                }, this.CHAT_BUBBLE_TIMEOUT);
            }
        });

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
                    this.controller.setShouldReopenChat(false);
                });
            }
        });
    }

    public handleButtonClick() {
        if (this.chatOpen() && this.dialogRef) {
            this.dialogRef.close();
        } else {
            this.openChat();
            this.chatOpen.set(true);
        }
    }

    public openChat() {
        this.chatOpen.set(true);
        this.newIrisMessage.set(undefined);
        this.lastShownBubbleMessage = undefined;
        this.dialogRef = this.dialog.open(IrisChatbotWidgetComponent, {
            hasBackdrop: false,
            scrollStrategy: this.overlay.scrollStrategies.noop(),
            position: { bottom: '0px', right: '0px' },
            disableClose: true,
            // Propagate this component's injector to the dialog content so the widget resolves
            // the same controller instance that this button provides — required for Iris state
            // to round-trip between button and popup widget.
            viewContainerRef: this.viewContainerRef,
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
