import { Component, EventEmitter, HostBinding, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Observable, Subject, timer } from 'rxjs';
import { distinctUntilChanged, first, map, takeUntil } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { cloneDeep } from 'lodash-es';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { round } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-exam-timer',
    templateUrl: './exam-timer.component.html',
    styleUrls: ['./exam-timer.scss'],
})
export class ExamTimerComponent implements OnInit, OnDestroy {
    @HostBinding('class.row') readonly row = true;

    @Input()
    endDate: dayjs.Dayjs;

    @Input()
    criticalTime: plugin.Duration;

    @Input()
    isEndView = false;

    @Output()
    timerAboutToEnd: EventEmitter<void> = new EventEmitter<void>();

    isCriticalTime: boolean;

    destroy$: Subject<boolean> = new Subject<boolean>();
    private timer: Observable<plugin.Duration> = timer(0, 100).pipe(map(() => dayjs.duration(this.endDate.diff(this.serverDateService.now()))));

    displayTime$ = this.timer.pipe(
        map((timeLeft: plugin.Duration) => this.updateDisplayTime(timeLeft)),
        distinctUntilChanged(),
    );

    timePipe: ArtemisDurationFromSecondsPipe = new ArtemisDurationFromSecondsPipe();

    constructor(private serverDateService: ArtemisServerDateService) {
        this.timer
            .pipe(
                map((timeLeft: plugin.Duration) => timeLeft.asSeconds()),
                distinctUntilChanged(),
                first((durationInSeconds) => durationInSeconds <= 1),
                takeUntil(this.destroy$),
            )
            .subscribe(() => {
                this.timerAboutToEnd.emit();
                // if timer is displayed and duration is already over
                // -> display at least one display time, that's why we use setTimeout
                setTimeout(() => this.destroy$.next(true));
            });
    }

    ngOnInit(): void {
        const duration = dayjs.duration(this.endDate.diff(this.serverDateService.now()));
        this.setIsCriticalTime(duration);
    }

    ngOnDestroy(): void {
        this.destroy$.next(true);
    }

    updateDisplayTime(timeDiff: plugin.Duration) {
        // update isCriticalTime
        this.setIsCriticalTime(timeDiff);

        if (timeDiff.asMilliseconds() < 0) {
            return this.timePipe.transform(0, true);
        } else {
            return this.timePipe.transform(round(timeDiff.asSeconds()), true);
        }
    }

    setIsCriticalTime(timeDiff: plugin.Duration) {
        const clonedTimeDiff = cloneDeep(timeDiff);
        if (this.criticalTime && clonedTimeDiff.subtract(this.criticalTime).asMilliseconds() < 0) {
            this.isCriticalTime = true;
        }
    }
}
