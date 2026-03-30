import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { faCircleXmark } from '@fortawesome/free-solid-svg-icons';
import { IrisStageDTO, IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { TranslateService } from '@ngx-translate/core';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { createStageRotation, translateLabel } from 'app/iris/overview/iris-stage-rotation.util';

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

    private readonly stageRotation = createStageRotation(this.translateService, this.destroyRef);
    readonly animToggle = this.stageRotation.animToggle;
    readonly displayName = this.stageRotation.displayName;

    readonly isError = computed(() => this.activeStage()?.state === IrisStageStateDTO.ERROR);

    stages = input<IrisStageDTO[]>([]);

    faCircleXmark = faCircleXmark;

    constructor() {
        // Stage detection effect
        effect(() => {
            const stages = this.stages();
            // Read locale to re-run translations on language change
            this.currentLocale();
            const visibleStages = stages.filter((stage) => !stage.internal);
            const firstUnfinished = visibleStages.find((stage) => !this.isStageFinished(stage));
            const current = untracked(() => this.activeStage());
            if (firstUnfinished) {
                if (current?.name !== firstUnfinished.name || current?.state !== firstUnfinished.state) {
                    this.open.set(true);
                    this.activeStage.set(firstUnfinished);
                }
                const label = firstUnfinished.message || '';
                this.stageMessage.set(label ? translateLabel(this.translateService, label) : undefined);
            } else if (current !== undefined) {
                this.activeStage.set(undefined);
                this.open.set(false);
                this.stageMessage.set(undefined);
            }
        });

        // Display name effect — show the active stage name, rotate labels during IN_PROGRESS
        effect(() => {
            const stage = this.activeStage();
            this.currentLocale();
            this.stageRotation.update(stage);
        });
    }

    isStageFinished(stage: IrisStageDTO) {
        return stage.state === IrisStageStateDTO.DONE || stage.state === IrisStageStateDTO.SKIPPED;
    }
}
