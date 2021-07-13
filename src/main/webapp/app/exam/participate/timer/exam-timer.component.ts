import { Component, EventEmitter, HostBinding, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Observable, Subject, timer } from 'rxjs';
import { distinctUntilChanged, first, map, takeUntil } from 'rxjs/operators';
import * as moment from 'moment';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { cloneDeep } from 'lodash';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';

@Component({
    selector: 'jhi-exam-timer',
    templateUrl: './exam-timer.component.html',
    styleUrls: ['./exam-timer.scss'],
})
export class ExamTimerComponent implements OnInit, OnDestroy {
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

    destroy$: Subject<boolean> = new Subject<boolean>();
    private timer$: Observable<moment.Duration> = timer(0, 100).pipe(map(() => moment.duration(this.endDate.diff(this.serverDateService.now()))));

    displayTime$ = this.timer$.pipe(
        map((timeLeft: moment.Duration) => this.updateDisplayTime(timeLeft)),
        distinctUntilChanged(),
        takeUntil(this.destroy$),
    );

    constructor(private serverDateService: ArtemisServerDateService, private timePipe: ArtemisDurationFromSecondsPipe) {
        this.timer$
            .pipe(
                map((timeLeft: moment.Duration) => timeLeft.asSeconds()),
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
        const duration: moment.Duration = moment.duration(this.endDate.diff(this.serverDateService.now()));
        this.setIsCriticalTime(duration);
    }

    ngOnDestroy(): void {
        this.destroy$.next(true);
    }

    updateDisplayTime(timeDiff: moment.Duration) {
        // update isCriticalTime
        this.setIsCriticalTime(timeDiff);
        if (timeDiff.asMilliseconds() < 0) {
            return '00 : 00';
        } else {
            return this.timePipe.transform(timeDiff.asSeconds());
        }
    }

    setIsCriticalTime(timeDiff: moment.Duration) {
        const clonedTimeDiff = cloneDeep(timeDiff);
        if (this.criticalTime && clonedTimeDiff.subtract(this.criticalTime).asMilliseconds() < 0) {
            this.isCriticalTime = true;
        }
    }
}
