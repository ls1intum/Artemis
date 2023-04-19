import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import dayjs from 'dayjs/esm';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-countdown',
    templateUrl: './countdown.component.html',
    providers: [ArtemisDurationFromSecondsPipe, ArtemisTranslatePipe],
})
export class CountdownComponent implements OnInit, OnDestroy {
    @Input() targetDate: dayjs.Dayjs;
    @Output() onFinish = new EventEmitter<void>();
    reachedZeroEmitted = false;

    remainingTimeSeconds: number | undefined;
    interval: number;

    constructor(private serverDateService: ArtemisServerDateService) {}

    ngOnInit(): void {
        this.update();
        this.interval = window.setInterval(() => {
            this.update();
        }, UI_RELOAD_TIME);
    }

    ngOnDestroy(): void {
        clearInterval(this.interval);
    }

    /**
     * Returns the remaining time in seconds
     */
    calculateRemainingTimeSeconds(): number | undefined {
        if (this.targetDate == undefined) {
            return undefined;
        }
        const remaining = this.targetDate.diff(this.serverDateService.now(), 'seconds');
        return Math.max(remaining, 0);
    }

    /**
     * Whether the countdown has reached zero
     */
    hasReachedZero(): boolean {
        if (this.remainingTimeSeconds == undefined) {
            return false;
        }
        return this.remainingTimeSeconds <= 0;
    }

    /**
     * Emits an event when the countdown has reached zero and updates the remaining time in seconds
     */
    update(): void {
        this.remainingTimeSeconds = this.calculateRemainingTimeSeconds();
        if (this.hasReachedZero()) {
            if (!this.reachedZeroEmitted) {
                this.onFinish.emit();
                this.reachedZeroEmitted = true;
            }
        } else {
            this.reachedZeroEmitted = false; // reset the flag
        }
    }
}
