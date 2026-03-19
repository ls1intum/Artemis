import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { faCircleXmark } from '@fortawesome/free-solid-svg-icons';
import { IrisStageDTO, IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { TranslateService } from '@ngx-translate/core';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-chat-status-bar',
    templateUrl: './chat-status-bar.component.html',
    styleUrl: './chat-status-bar.component.scss',
    imports: [FaIconComponent, IrisLogoComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatStatusBarComponent {
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly currentLocale = getCurrentLocaleSignal(this.translateService);
    readonly open = signal(false);
    readonly activeStage = signal<IrisStageDTO | undefined>(undefined);
    readonly stageMessage = signal<string | undefined>(undefined);

    readonly animToggle = signal(false);
    readonly displayName = signal('');

    readonly isError = computed(() => this.activeStage()?.state === IrisStageStateDTO.ERROR);

    stages = input<IrisStageDTO[]>([]);

    faCircleXmark = faCircleXmark;

    private readonly ROTATION_KEYS = [
        'artemisApp.iris.stages.thinking',
        'artemisApp.iris.stages.analyzing',
        'artemisApp.iris.stages.processing',
        'artemisApp.iris.stages.formulating',
    ];
    private rotationIntervalId: ReturnType<typeof setInterval> | undefined;
    private rotationIndex = 0;

    constructor() {
        // Stage detection effect
        effect(() => {
            const stages = this.stages();
            // Read locale to re-run translations on language change
            this.currentLocale();
            const firstUnfinished = stages.find((stage) => !this.isStageFinished(stage));
            if (firstUnfinished) {
                this.open.set(true);
                this.activeStage.set(firstUnfinished);
                const label = firstUnfinished.message || firstUnfinished.name;
                this.stageMessage.set(label ? this.translateLabel(label) : undefined);
            } else {
                this.activeStage.set(undefined);
                if (this.open()) {
                    this.open.set(false);
                    this.stageMessage.set(undefined);
                }
            }
        });

        // Display name effect — show the active stage name, rotate labels during IN_PROGRESS
        effect(() => {
            const stage = this.activeStage();
            // Read locale to re-run translations on language change
            this.currentLocale();
            const name = stage?.name ?? '';
            const translated = name ? this.translateLabel(name) : '';

            // Clear any existing rotation interval on every run
            if (this.rotationIntervalId) {
                clearInterval(this.rotationIntervalId);
                this.rotationIntervalId = undefined;
            }

            if (translated) {
                if (translated !== untracked(() => this.displayName())) {
                    this.displayName.set(translated);
                    this.animToggle.update((v) => !v);
                }

                // Start rotation if stage is IN_PROGRESS
                if (stage?.state === IrisStageStateDTO.IN_PROGRESS) {
                    this.rotationIndex = 0;
                    this.rotationIntervalId = setInterval(() => {
                        this.rotationIndex = (this.rotationIndex + 1) % this.ROTATION_KEYS.length;
                        const rotated = this.translateLabel(this.ROTATION_KEYS[this.rotationIndex]);
                        this.displayName.set(rotated);
                        this.animToggle.update((v) => !v);
                    }, 2500);
                }
            } else {
                this.displayName.set('');
            }
        });

        // Cleanup rotation interval on destroy
        this.destroyRef.onDestroy(() => {
            if (this.rotationIntervalId) {
                clearInterval(this.rotationIntervalId);
            }
        });
    }

    isStageFinished(stage: IrisStageDTO) {
        return stage.state === IrisStageStateDTO.DONE || stage.state === IrisStageStateDTO.SKIPPED;
    }

    private translateLabel(key: string): string {
        const translated = this.translateService.instant(key);
        return typeof translated === 'string' && translated.startsWith('translation-not-found[') ? key : translated;
    }
}
