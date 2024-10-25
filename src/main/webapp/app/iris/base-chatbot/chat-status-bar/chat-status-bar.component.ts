import { ChangeDetectionStrategy, Component, OnChanges, input } from '@angular/core';
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
export class ChatStatusBarComponent implements OnChanges {
    open = false;
    openTimeout: ReturnType<typeof setTimeout>;
    styleTimeout: ReturnType<typeof setTimeout>;
    activeStage?: IrisStageDTO;
    displayedText?: string;
    displayedSubText?: string;
    style?: string;

    stages = input<IrisStageDTO[]>();

    faArrowsRotate = faArrowsRotate;
    faCircleXmark = faCircleXmark;

    protected readonly JSON = JSON;
    protected readonly IrisStageStateDTO = IrisStageStateDTO;

    constructor() {}

    ngOnChanges() {
        // Lower case state for scss classes, avoid function calling in template
        this.stages()?.forEach((stage) => (stage.lowerCaseState = stage.state?.toLowerCase()));
        const firstUnfinished = this.stages()?.find((stage) => !this.isStageFinished(stage));
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
    }

    isStageFinished(stage: IrisStageDTO) {
        return stage.state === 'DONE' || stage.state === 'SKIPPED';
    }
}
