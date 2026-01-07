import { ChangeDetectionStrategy, Component, computed, effect, input } from '@angular/core';
import { faArrowsRotate, faCircleXmark } from '@fortawesome/free-solid-svg-icons';
import { IrisStageDTO, IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';

/**
 * Extended stage type with computed lowercase state for CSS class binding
 */
interface ProcessedStage extends IrisStageDTO {
    lowerCaseState?: string;
}

@Component({
    selector: 'jhi-chat-status-bar',
    templateUrl: './chat-status-bar.component.html',
    styleUrl: './chat-status-bar.component.scss',
    imports: [FaIconComponent, NgClass],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatStatusBarComponent {
    open = false;
    openTimeout: ReturnType<typeof setTimeout>;
    styleTimeout: ReturnType<typeof setTimeout>;
    activeStage?: IrisStageDTO;
    displayedText?: string;
    displayedSubText?: string;
    style?: string;

    readonly stages = input<IrisStageDTO[]>([]);

    // Computed signal that creates copies with lowerCaseState added (avoids mutating input)
    readonly processedStages = computed<ProcessedStage[]>(() => {
        return this.stages().map((stage) => ({
            ...stage,
            lowerCaseState: stage.state?.toLowerCase(),
        }));
    });

    faArrowsRotate = faArrowsRotate;
    faCircleXmark = faCircleXmark;

    protected readonly JSON = JSON;
    protected readonly IrisStageStateDTO = IrisStageStateDTO;

    constructor() {
        effect((onCleanup) => {
            const stages = this.stages();
            const firstUnfinished = stages.find((stage) => !this.isStageFinished(stage));
            if (firstUnfinished) {
                clearTimeout(this.openTimeout);
                clearTimeout(this.styleTimeout);
                this.open = true;
                // Only update style tag if the active stage changed; otherwise the animations are reset on each change
                if (firstUnfinished.name !== this.activeStage?.name) {
                    this.style = undefined;
                    // Use a timeout to let the bar of this stage autofill until 5% in 500ms (using scss)
                    // This makes it more clear that the stage has started
                    // After that, change it to 90% to let it slowly fill up using css transition
                    // Stopping at 90% makes it more clear that the stage is not yet finished
                    this.styleTimeout = setTimeout(() => (this.style = 'transform: scaleX(0.9)'), 500);
                }
                this.activeStage = firstUnfinished;
                this.displayedText = firstUnfinished.name;
                this.displayedSubText = firstUnfinished.message || undefined;
            } else {
                this.activeStage = undefined;
                if (this.open) {
                    this.openTimeout = setTimeout(() => {
                        this.open = false;
                        this.displayedText = undefined;
                        this.displayedSubText = undefined;
                    }, 5000);
                }
            }

            // Cleanup timeouts when effect re-runs or component destroys
            onCleanup(() => {
                clearTimeout(this.openTimeout);
                clearTimeout(this.styleTimeout);
            });
        });
    }

    isStageFinished(stage: IrisStageDTO) {
        return stage.state === 'DONE' || stage.state === 'SKIPPED';
    }
}
