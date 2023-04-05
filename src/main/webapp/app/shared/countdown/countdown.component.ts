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
    @Output() onReachedZero = new EventEmitter<void>();

    timeUntilTarget = '0';
    interval: number;

    constructor(private serverDateService: ArtemisServerDateService, private translateService: TranslateService) {}

    ngOnInit(): void {
        this.interval = window.setInterval(() => {
            this.timeUntilTarget = this.relativeTimeText(this.targetDate.diff(this.serverDateService.now(), 'seconds'));
        }, UI_RELOAD_TIME);
    }

    ngOnDestroy(): void {
        clearInterval(this.interval);
    }

    /**
     * Whether the countdown has reached zero
     */
    hasReachedZero(): boolean {
        return this.targetDate.diff(dayjs(), 'seconds') <= 0;
    }

    /**
     * Updates all displayed (relative) times in the UI
     */
    updateDisplayedTimes() {
        const translationBasePath = 'artemisApp.showStatistic.';
        // update time until start
        if (this.targetDate) {
            if (this.hasReachedZero()) {
                this.timeUntilTarget = this.translateService.instant(translationBasePath + 'now');
                this.onReachedZero.emit();
            } else {
                this.timeUntilTarget = this.relativeTimeText(this.targetDate.diff(this.serverDateService.now(), 'seconds'));
            }
        } else {
            this.timeUntilTarget = '';
        }
    }

    /**
     * Express the given timespan as humanized text
     *
     * @param remainingTimeSeconds {number} the amount of seconds to display
     * @return {string} humanized text for the given amount of seconds
     */
    relativeTimeText(remainingTimeSeconds: number): string {
        if (remainingTimeSeconds > 210) {
            return Math.ceil(remainingTimeSeconds / 60) + ' min';
        } else if (remainingTimeSeconds > 59) {
            return Math.floor(remainingTimeSeconds / 60) + ' min ' + (remainingTimeSeconds % 60) + ' s';
        } else {
            return remainingTimeSeconds + ' s';
        }
    }
}
