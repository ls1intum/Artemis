import { Component, OnDestroy, effect, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import dayjs from 'dayjs/esm';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-result-progress-bar',
    standalone: true,
    imports: [CommonModule, ArtemisSharedCommonModule],
    templateUrl: './result-progress-bar.component.html',
    styleUrl: './result-progress-bar.component.scss',
})
export class ResultProgressBarComponent implements OnDestroy {
    estimatedCompletionDate = input.required<dayjs.Dayjs>();
    buildStartDate = input.required<dayjs.Dayjs>();
    isBuilding = input.required<boolean>();
    isQueued = input.required<boolean>();

    isQueueProgressBarAnimated: boolean;
    queueProgressBarOpacity: number;
    queueProgressBarValue: number;

    isBuildProgressBarAnimated: boolean;
    buildProgressBarOpacity: number;
    buildProgressBarValue: number;

    estimatedRemaining: number;

    estimatedDurationInterval: ReturnType<typeof setInterval> | undefined;

    faCircleNotch = faCircleNotch;

    constructor() {
        effect(() => {
            if (!this.isBuilding() && !this.isQueued()) {
                if (this.estimatedDurationInterval) {
                    clearInterval(this.estimatedDurationInterval);
                    this.estimatedDurationInterval = undefined;
                }
                return;
            }

            clearInterval(this.estimatedDurationInterval);
            if (this.estimatedCompletionDate() && this.buildStartDate()) {
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
                    this.estimatedRemaining = 0;
                } else if (this.isQueued()) {
                    this.setupBuildProgressBarForQueued();
                    this.isQueueProgressBarAnimated = false;
                    this.queueProgressBarValue = 100;
                    this.estimatedRemaining = 0;
                }
                this.estimatedDurationInterval = setInterval(() => {
                    this.alternateOpacity(this.isQueued());
                }, 1000); // 1 second
            }
        });
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
        this.estimatedDurationInterval = setInterval(() => {
            this.queueProgressBarValue = this.calculateEstimatedRemaining();
            if (this.estimatedRemaining <= 0) {
                this.isQueueProgressBarAnimated = false;
                this.alternateOpacity(true);
            }
        }, 1000); // 1 second
    }

    private updateBuildProgressBar() {
        this.isBuildProgressBarAnimated = true;
        this.buildProgressBarOpacity = 1;
        this.estimatedDurationInterval = setInterval(() => {
            this.buildProgressBarValue = this.calculateEstimatedRemaining();
            if (this.estimatedRemaining <= 0) {
                this.isBuildProgressBarAnimated = false;
                this.alternateOpacity(false);
            }
        }, 1000); // 1 second
    }

    private calculateEstimatedRemaining() {
        this.estimatedRemaining = Math.max(0, dayjs(this.estimatedCompletionDate()).diff(dayjs(), 'seconds'));
        const estimatedDuration = dayjs(this.estimatedCompletionDate()).diff(dayjs(this.buildStartDate()), 'seconds');
        return Math.round((1 - this.estimatedRemaining / estimatedDuration) * 100);
    }

    private alternateOpacity(isQueue?: boolean) {
        if (isQueue) {
            this.queueProgressBarOpacity = this.queueProgressBarOpacity === 1 ? 0 : 1;
        } else {
            this.buildProgressBarOpacity = this.buildProgressBarOpacity === 1 ? 0 : 1;
        }
    }
}
