import { ChangeDetectionStrategy, Component, effect, input, signal } from '@angular/core';
import { faArrowsRotate, faCircleXmark } from '@fortawesome/free-solid-svg-icons';
import { IrisStageDTO, IrisStageStateDTO } from 'app/entities/iris/iris-stage-dto.model';
import { CommonModule } from '@angular/common';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-chat-status-bar',
    templateUrl: './chat-status-bar.component.html',
    styleUrl: './chat-status-bar.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: true,
    imports: [CommonModule, FontAwesomeModule],
})
export class ChatStatusBarComponent {
    open = signal(false);
    openTimeout: ReturnType<typeof setTimeout>;
    styleTimeout: ReturnType<typeof setTimeout>;
    activeStage = signal<IrisStageDTO | undefined>(undefined);
    displayedText = signal<string | undefined>(undefined);
    displayedSubText = signal<string | undefined>(undefined);
    style = signal<string | undefined>(undefined);

    stages = input<IrisStageDTO[]>();

    faArrowsRotate = faArrowsRotate;
    faCircleXmark = faCircleXmark;

    protected readonly JSON = JSON;
    protected readonly IrisStageStateDTO = IrisStageStateDTO;

    constructor() {
        effect(
            () => {
                const stages = this.stages();
                if (!stages) return;

                stages.forEach((stage) => {
                    stage.lowerCaseState = stage.state?.toLowerCase();
                });

                const firstUnfinished = stages.find((stage) => !this.isStageFinished(stage));
                if (firstUnfinished) {
                    clearTimeout(this.openTimeout);
                    clearTimeout(this.styleTimeout);
                    this.open.set(true);
                    if (firstUnfinished.name !== this.activeStage()?.name) {
                        this.style.set(undefined);
                        this.styleTimeout = setTimeout(() => this.style.set('transform: scaleX(0.9)'), 500);
                    }
                    this.activeStage.set(firstUnfinished);
                    this.displayedText.set(firstUnfinished.name);
                    this.displayedSubText.set(firstUnfinished.message || undefined);
                } else {
                    this.activeStage.set(undefined);
                    if (this.open()) {
                        this.openTimeout = setTimeout(() => {
                            this.open.set(false);
                            this.displayedText.set(undefined);
                            this.displayedSubText.set(undefined);
                        }, 5000);
                    }
                }
            },
            { allowSignalWrites: true },
        );
    }

    isStageFinished(stage: IrisStageDTO) {
        return stage.state === 'DONE' || stage.state === 'SKIPPED';
    }
}
