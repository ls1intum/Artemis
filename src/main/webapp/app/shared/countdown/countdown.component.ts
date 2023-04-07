import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import dayjs from 'dayjs/esm';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisServerDateService } from 'app/shared/server-date.service';

@Component({
    selector: 'jhi-countdown',
    templateUrl: './countdown.component.html',
})
export class CountdownComponent implements OnInit, OnDestroy {
    @Input() targetDate: dayjs.Dayjs;
    @Input() waitingText: string;
    @Output() reachedZero = new EventEmitter<void>();
    reachedZeroEmitted = false;

    timeUntilTarget = '0';
    interval: number;

    constructor(private serverDateService: ArtemisServerDateService, private translateService: TranslateService) {}

    ngOnInit(): void {
        this.updateDisplayedTimes();
        this.interval = window.setInterval(() => {
            this.updateDisplayedTimes();
        }, UI_RELOAD_TIME);
    }

    ngOnDestroy(): void {
        clearInterval(this.interval);
    }

    /**
     * Returns the remaining time in seconds
     */
    remainingTimeSeconds(): number | undefined {
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
        const remaining = this.remainingTimeSeconds();
        if (remaining == undefined) {
            return false;
        }
        return remaining <= 0;
    }

    /**
     * Updates all displayed (relative) times in the UI
     */
    updateDisplayedTimes() {
        // update time until start
        if (this.hasReachedZero()) {
            this.timeUntilTarget = this.translateService.instant('artemisApp.showStatistic.now');
            if (!this.reachedZeroEmitted) {
                this.reachedZero.emit();
                this.reachedZeroEmitted = true;
            }
        } else {
            this.timeUntilTarget = this.relativeTimeText(this.remainingTimeSeconds());
            this.reachedZeroEmitted = false; // reset the flag
        }
    }

    /**
     * Express the given timespan as humanized text
     *
     * @param remainingTimeSeconds {number?} the amount of seconds to display, or the empty string if no time is given
     * @return {string} humanized text for the given amount of seconds
     */
    relativeTimeText(remainingTimeSeconds: number | undefined): string {
        if (remainingTimeSeconds == undefined) {
            return '';
        }
        if (remainingTimeSeconds > 210) {
            return Math.ceil(remainingTimeSeconds / 60) + ' min';
        } else if (remainingTimeSeconds > 59) {
            return Math.floor(remainingTimeSeconds / 60) + ' min ' + (remainingTimeSeconds % 60) + ' s';
        } else {
            return remainingTimeSeconds + ' s';
        }
    }
}
