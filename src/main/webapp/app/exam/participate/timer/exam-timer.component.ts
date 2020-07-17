import { Component, OnInit, Input, EventEmitter, Output, HostBinding } from '@angular/core';
import { timer } from 'rxjs';
import { map, distinctUntilChanged, first } from 'rxjs/operators';
import * as moment from 'moment';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { cloneDeep } from 'lodash';

@Component({
    selector: 'jhi-exam-timer',
    templateUrl: './exam-timer.component.html',
    styleUrls: ['./exam-timer.scss'],
})
export class ExamTimerComponent implements OnInit {
    @HostBinding('class.row') readonly row = true;

    @Input()
    endDate: moment.Moment;

    @Input()
    criticalTime: moment.Duration;

    @Input()
    isEndView = false;

    @Output()
    timerAboutToEnd: EventEmitter<void> = new EventEmitter<void>();

    isCriticalTime: boolean;

    private timer$ = timer(0, 100).pipe(map(() => moment.duration(this.endDate.diff(this.serverDateService.now()))));

    displayTime$ = this.timer$.pipe(
        map((timeLeft: moment.Duration) => this.updateDisplayTime(timeLeft)),
        distinctUntilChanged(),
    );

    constructor(private serverDateService: ArtemisServerDateService) {
        this.timer$.pipe(first((duration: moment.Duration) => duration.asSeconds() <= 1)).subscribe(() => this.timerAboutToEnd.emit());
    }

    ngOnInit(): void {
        const duration: moment.Duration = moment.duration(this.endDate.diff(this.serverDateService.now()));
        this.setIsCriticalTime(duration);
    }

    updateDisplayTime(timeDiff: moment.Duration) {
        // update isCriticalTime
        this.setIsCriticalTime(timeDiff);
        if (timeDiff.milliseconds() < 0) {
            return '00 : 00';
        } else {
            return timeDiff.asMinutes() > 10
                ? Math.round(timeDiff.asMinutes()) + ' min'
                : timeDiff.minutes().toString().padStart(2, '0') + ' : ' + timeDiff.seconds().toString().padStart(2, '0') + ' min';
        }
    }

    setIsCriticalTime(timeDiff: moment.Duration) {
        const clonedTimeDiff = cloneDeep(timeDiff);
        if (this.criticalTime && clonedTimeDiff.subtract(this.criticalTime).milliseconds() < 0) {
            this.isCriticalTime = true;
        }
    }
}
