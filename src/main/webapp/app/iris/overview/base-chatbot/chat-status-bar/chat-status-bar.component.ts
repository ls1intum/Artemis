import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
import { faCircleXmark } from '@fortawesome/free-solid-svg-icons';
import { IrisStageDTO, IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';

@Component({
    selector: 'jhi-chat-status-bar',
    templateUrl: './chat-status-bar.component.html',
    styleUrl: './chat-status-bar.component.scss',
    imports: [FaIconComponent, IrisLogoComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatStatusBarComponent {
    readonly open = signal(false);
    readonly activeStage = signal<IrisStageDTO | undefined>(undefined);
    readonly errorMessage = signal<string | undefined>(undefined);

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
                this.errorMessage.set(firstUnfinished.message || firstUnfinished.name);
            } else {
                this.activeStage.set(undefined);
                if (this.open()) {
                    this.open.set(false);
                    this.errorMessage.set(undefined);
                }
            }
        });

        // Display name effect — show the active stage name and toggle animation on change
        effect(() => {
            const stage = this.activeStage();
            const name = stage?.name ?? '';
            if (name && name !== this.displayName()) {
                this.displayName.set(name);
                this.animToggle.update((v) => !v);
            } else if (!name) {
                this.displayName.set('');
            }
        });
    }

    isStageFinished(stage: IrisStageDTO) {
        return stage.state === IrisStageStateDTO.DONE || stage.state === IrisStageStateDTO.SKIPPED;
    }
}
