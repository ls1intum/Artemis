import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { faCircleXmark } from '@fortawesome/free-solid-svg-icons';
import { IrisStageDTO, IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-chat-status-bar',
    templateUrl: './chat-status-bar.component.html',
    styleUrl: './chat-status-bar.component.scss',
    imports: [FaIconComponent, IrisLogoComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatStatusBarComponent {
    private readonly translateService = inject(TranslateService);
    readonly open = signal(false);
    readonly activeStage = signal<IrisStageDTO | undefined>(undefined);
    readonly stageMessage = signal<string | undefined>(undefined);

    readonly animToggle = signal(false);
    readonly displayName = signal('');

    readonly isError = computed(() => this.activeStage()?.state === IrisStageStateDTO.ERROR);

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

        // Display name effect — show the active stage name and toggle animation on change
        effect(() => {
            const stage = this.activeStage();
            const name = stage?.name ?? '';
            const translated = name ? this.translateLabel(name) : '';
            if (translated && translated !== untracked(() => this.displayName())) {
                this.displayName.set(translated);
                this.animToggle.update((v) => !v);
            } else if (!translated) {
                this.displayName.set('');
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
