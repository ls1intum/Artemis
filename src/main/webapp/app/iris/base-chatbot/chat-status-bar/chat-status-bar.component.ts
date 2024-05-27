import { Component, Input, OnChanges } from '@angular/core';
import { faArrowsRotate, faCircleXmark } from '@fortawesome/free-solid-svg-icons';
import { IrisStageDTO, IrisStageStateDTO } from 'app/entities/iris/iris-stage-dto.model';

@Component({
    selector: 'jhi-chat-status-bar',
    templateUrl: './chat-status-bar.component.html',
    styleUrl: './chat-status-bar.component.scss',
})
export class ChatStatusBarComponent implements OnChanges {
    open = false;
    openTimeout: ReturnType<typeof setTimeout>;
    styleTimeout: ReturnType<typeof setTimeout>;
    activeStage?: IrisStageDTO;
    displayedText?: string;
    displayedSubText?: string;
    style?: string;

    @Input() stages?: IrisStageDTO[] = [];

    faArrowsRotate = faArrowsRotate;
    faCircleXmark = faCircleXmark;

    constructor() {}

    ngOnChanges() {
        // Lower case state for scss classes, avoid function calling in template
        this.stages?.forEach((stage) => (stage.lowerCaseState = stage.state?.toLowerCase()));
        const firstUnfinished = this.stages?.find((stage) => !this.isStageFinished(stage));
        if (firstUnfinished) {
            clearTimeout(this.openTimeout);
            clearTimeout(this.styleTimeout);
            this.open = true;
            if (firstUnfinished.name !== this.activeStage?.name) {
                this.style = undefined;
                this.styleTimeout = setTimeout(() => (this.style = 'transform: scaleX(0.9)'), 500);
            }
            this.activeStage = firstUnfinished;
            this.displayedText = firstUnfinished.name;
            this.displayedSubText = firstUnfinished.message || undefined;
        } else {
            this.activeStage = undefined;
            this.openTimeout = setTimeout(() => {
                this.open = false;
                this.displayedText = undefined;
                this.displayedSubText = undefined;
            }, 5000);
        }
    }

    isStageFinished(stage: IrisStageDTO) {
        return stage.state === 'DONE' || stage.state === 'SKIPPED';
    }

    protected readonly JSON = JSON;
    protected readonly IrisStageStateDTO = IrisStageStateDTO;
}
