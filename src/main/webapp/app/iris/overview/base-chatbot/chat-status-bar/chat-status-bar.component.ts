import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
import { faCircleXmark } from '@fortawesome/free-solid-svg-icons';
import { IrisStageDTO, IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-chat-status-bar',
    templateUrl: './chat-status-bar.component.html',
    styleUrl: './chat-status-bar.component.scss',
    imports: [FaIconComponent, IrisLogoComponent, TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatStatusBarComponent {
    private static readonly STATUS_KEYS = [
        'artemisApp.iris.chatStatus.thinkingHard',
        'artemisApp.iris.chatStatus.analyzingContext',
        'artemisApp.iris.chatStatus.craftingResponse',
        'artemisApp.iris.chatStatus.consultingSources',
        'artemisApp.iris.chatStatus.almostThere',
    ];
    private static readonly CYCLE_INTERVAL = 2500;

    readonly open = signal(false);
    readonly activeStage = signal<IrisStageDTO | undefined>(undefined);
    readonly errorMessage = signal<string | undefined>(undefined);

    readonly currentKeyIndex = signal(0);
    readonly animToggle = signal(false);
    readonly currentKey = computed(() => ChatStatusBarComponent.STATUS_KEYS[this.currentKeyIndex()]);

    readonly isError = computed(() => this.activeStage()?.state === IrisStageStateDTO.ERROR);

    private cycleIntervalId: ReturnType<typeof setInterval> | undefined;
    private shuffledOrder: number[] = [];
    private shuffledPosition = 0;

    stages = input<IrisStageDTO[]>([]);

    faCircleXmark = faCircleXmark;

    constructor() {
        // Stage detection effect
        effect(() => {
            const stages = this.stages();
            const firstUnfinished = stages.find((stage) => !this.isStageFinished(stage));
            if (firstUnfinished) {
                this.open.set(true);
                this.activeStage.set(firstUnfinished);
                this.errorMessage.set(firstUnfinished.message || firstUnfinished.name);
            } else {
                this.activeStage.set(undefined);
                if (this.open()) {
                    this.open.set(false);
                    this.errorMessage.set(undefined);
                }
            }
        });

        // Cycling message effect
        effect((onCleanup) => {
            const isOpen = this.open();
            const hasError = this.isError();

            if (isOpen && !hasError) {
                this.shuffleLabelOrder();
                this.shuffledPosition = 0;
                this.currentKeyIndex.set(this.shuffledOrder[0]);
                this.animToggle.set(true);

                this.cycleIntervalId = setInterval(() => {
                    this.shuffledPosition = (this.shuffledPosition + 1) % ChatStatusBarComponent.STATUS_KEYS.length;
                    if (this.shuffledPosition === 0) {
                        this.shuffleLabelOrder();
                    }
                    this.currentKeyIndex.set(this.shuffledOrder[this.shuffledPosition]);
                    this.animToggle.update((v) => !v);
                }, ChatStatusBarComponent.CYCLE_INTERVAL);
            } else {
                clearInterval(this.cycleIntervalId);
                this.cycleIntervalId = undefined;
            }

            onCleanup(() => {
                clearInterval(this.cycleIntervalId);
                this.cycleIntervalId = undefined;
            });
        });
    }

    isStageFinished(stage: IrisStageDTO) {
        return stage.state === IrisStageStateDTO.DONE || stage.state === IrisStageStateDTO.SKIPPED;
    }

    private shuffleLabelOrder(): void {
        const keys = ChatStatusBarComponent.STATUS_KEYS;
        const lastShown = this.shuffledOrder.length > 0 ? this.shuffledOrder[this.shuffledOrder.length - 1] : -1;

        this.shuffledOrder = keys.map((_, i) => i);
        // Fisher-Yates shuffle
        for (let i = this.shuffledOrder.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [this.shuffledOrder[i], this.shuffledOrder[j]] = [this.shuffledOrder[j], this.shuffledOrder[i]];
        }

        // Avoid repeating the last shown label at the start of new shuffle
        if (this.shuffledOrder[0] === lastShown && this.shuffledOrder.length > 1) {
            [this.shuffledOrder[0], this.shuffledOrder[1]] = [this.shuffledOrder[1], this.shuffledOrder[0]];
        }
    }
}
