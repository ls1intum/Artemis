import { Component, OnDestroy, effect, input, signal } from '@angular/core';

import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ProgressBarModule } from 'primeng/progressbar';
import { TooltipModule } from 'primeng/tooltip';

@Component({
    selector: 'jhi-result-progress-bar',
    imports: [TranslateDirective, ArtemisTranslatePipe, ArtemisDurationFromSecondsPipe, FontAwesomeModule, ProgressBarModule, TooltipModule],
    templateUrl: './result-progress-bar.component.html',
})
export class ResultProgressBarComponent implements OnDestroy {
    estimatedRemaining = input<number>(0);
    estimatedDuration = input<number>(0);
    isBuilding = input.required<boolean>();
    isQueued = input.required<boolean>();
    showBorder = input<boolean>(false);

    // These fields back template bindings and are mutated from the opacity-pulsing interval below, i.e. outside any
    // change-detection cycle. Signals are used so each write schedules change detection under zoneless.
    readonly isQueueProgressBarAnimated = signal(false);
    readonly queueProgressBarOpacity = signal(0);
    readonly queueProgressBarValue = signal(0);

    readonly isBuildProgressBarAnimated = signal(false);
    readonly buildProgressBarOpacity = signal(0);
    readonly buildProgressBarValue = signal(0);

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
                this.isBuildProgressBarAnimated.set(false);
                this.buildProgressBarValue.set(100);
            } else if (this.isQueued()) {
                this.setupBuildProgressBarForQueued();
                this.isQueueProgressBarAnimated.set(false);
                this.queueProgressBarValue.set(100);
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
            this.isQueueProgressBarAnimated.set(false);
        }
        return isBuildingOrQueued;
    }

    ngOnDestroy() {
        if (this.estimatedDurationInterval) {
            clearInterval(this.estimatedDurationInterval);
        }
    }

    private setupBuildProgressBarForQueued() {
        this.isBuildProgressBarAnimated.set(true);
        this.buildProgressBarOpacity.set(1);
        this.buildProgressBarValue.set(0);
    }

    private setupQueueProgressBarForBuild() {
        this.isQueueProgressBarAnimated.set(true);
        this.queueProgressBarOpacity.set(1);
        this.queueProgressBarValue.set(100);
    }

    private updateQueueProgressBar() {
        this.isQueueProgressBarAnimated.set(true);
        this.queueProgressBarOpacity.set(1);
        if (this.estimatedDuration() === 0) {
            this.queueProgressBarValue.set(100);
            return;
        }
        this.queueProgressBarValue.set(Math.round((1 - this.estimatedRemaining() / this.estimatedDuration()) * 100));
    }

    private updateBuildProgressBar() {
        this.isBuildProgressBarAnimated.set(true);
        this.buildProgressBarOpacity.set(1);
        if (this.estimatedDuration() === 0) {
            this.buildProgressBarValue.set(100);
            return;
        }
        this.buildProgressBarValue.set(Math.round((1 - this.estimatedRemaining() / this.estimatedDuration()) * 100));
    }

    private alternateOpacity(isQueue?: boolean) {
        if (isQueue) {
            this.queueProgressBarOpacity.update((opacity) => (opacity === 1 ? 0 : 1));
        } else {
            this.buildProgressBarOpacity.update((opacity) => (opacity === 1 ? 0 : 1));
        }
    }
}
