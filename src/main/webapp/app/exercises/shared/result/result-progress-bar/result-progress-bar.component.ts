import { Component, OnDestroy, effect, input } from '@angular/core';
import { CommonModule } from '@angular/common';

import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-result-progress-bar',
    imports: [CommonModule, TranslateDirective, ArtemisTranslatePipe, ArtemisDurationFromSecondsPipe, FontAwesomeModule, NgbTooltipModule],
    templateUrl: './result-progress-bar.component.html',
    styleUrl: './result-progress-bar.component.scss',
})
export class ResultProgressBarComponent implements OnDestroy {
    estimatedRemaining = input<number>(0);
    estimatedDuration = input<number>(0);
    isBuilding = input.required<boolean>();
    isQueued = input.required<boolean>();
    showBorder = input<boolean>(false);

    isQueueProgressBarAnimated: boolean;
    queueProgressBarOpacity: number;
    queueProgressBarValue: number;

    isBuildProgressBarAnimated: boolean;
    buildProgressBarOpacity: number;
    buildProgressBarValue: number;

    estimatedDurationInterval: ReturnType<typeof setInterval> | undefined;

    protected readonly faCircleNotch = faCircleNotch;

    constructor() {
        effect(() => {
            const isBuildingOrQueued = this.cleanUpIfNotBuildingOrQueued();
            if (!isBuildingOrQueued) {
                return;
            }

            clearInterval(this.estimatedDurationInterval);
            this.updateProgressBarState();
        });
    }

    private updateProgressBarState() {
        if (this.estimatedDuration() && this.estimatedRemaining()) {
            if (this.isBuilding()) {
                this.setupQueueProgressBarForBuild();
                this.updateBuildProgressBar();
            } else if (this.isQueued()) {
                this.setupBuildProgressBarForQueued();
                this.updateQueueProgressBar();
            }
        } else {
            if (this.isBuilding()) {
                this.setupQueueProgressBarForBuild();
                this.isBuildProgressBarAnimated = false;
                this.buildProgressBarValue = 100;
            } else if (this.isQueued()) {
                this.setupBuildProgressBarForQueued();
                this.isQueueProgressBarAnimated = false;
                this.queueProgressBarValue = 100;
            }
            this.estimatedDurationInterval = setInterval(() => {
                this.alternateOpacity(this.isQueued());
            }, 1000); // 1 second
        }
    }

    private cleanUpIfNotBuildingOrQueued() {
        const isBuildingOrQueued = true;
        if (!this.isBuilding() && !this.isQueued()) {
            if (this.estimatedDurationInterval) {
                clearInterval(this.estimatedDurationInterval);
                this.estimatedDurationInterval = undefined;
            }
            this.isQueueProgressBarAnimated = false;
        }
        return isBuildingOrQueued;
    }

    ngOnDestroy() {
        if (this.estimatedDurationInterval) {
            clearInterval(this.estimatedDurationInterval);
        }
    }

    private setupBuildProgressBarForQueued() {
        this.isBuildProgressBarAnimated = true;
        this.buildProgressBarOpacity = 1;
        this.buildProgressBarValue = 0;
    }

    private setupQueueProgressBarForBuild() {
        this.isQueueProgressBarAnimated = true;
        this.queueProgressBarOpacity = 1;
        this.queueProgressBarValue = 100;
    }

    private updateQueueProgressBar() {
        this.isQueueProgressBarAnimated = true;
        this.queueProgressBarOpacity = 1;
        if (this.estimatedDuration() === 0) {
            this.queueProgressBarValue = 100;
            return;
        }
        this.queueProgressBarValue = Math.round((1 - this.estimatedRemaining() / this.estimatedDuration()) * 100);
    }

    private updateBuildProgressBar() {
        this.isBuildProgressBarAnimated = true;
        this.buildProgressBarOpacity = 1;
        if (this.estimatedDuration() === 0) {
            this.buildProgressBarValue = 100;
            return;
        }
        this.buildProgressBarValue = Math.round((1 - this.estimatedRemaining() / this.estimatedDuration()) * 100);
    }

    private alternateOpacity(isQueue?: boolean) {
        if (isQueue) {
            this.queueProgressBarOpacity = this.queueProgressBarOpacity === 1 ? 0 : 1;
        } else {
            this.buildProgressBarOpacity = this.buildProgressBarOpacity === 1 ? 0 : 1;
        }
    }
}
