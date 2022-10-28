import { Component, Input, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { faCalendarAlt } from '@fortawesome/free-solid-svg-icons';
import { NgbDateParserFormatter, NgbTimeAdapter } from '@ng-bootstrap/ng-bootstrap';
import { weekDays } from 'app/course/tutorial-groups/shared/weekdays';
import { Course } from 'app/entities/course.model';
import * as _ from 'lodash-es';
import dayjs from 'dayjs/esm';
import { dayOfWeekZeroSundayToZeroMonday } from 'app/utils/date.utils';
import { NgbTimeStringAdapter } from 'app/course/tutorial-groups/shared/ngbTimeStringAdapter';
import { validTimeRange } from 'app/course/tutorial-groups/shared/timeRangeValidator';

export interface ScheduleFormData {
    dayOfWeek?: number;
    startTime?: string;
    endTime?: string;
    repetitionFrequency?: number;
    period?: Date[];
    location?: string;
}

@Component({
    selector: 'jhi-schedule-form',
    templateUrl: './schedule-form.component.html',
    styleUrls: ['./schedule-form.component.scss'],
    providers: [{ provide: NgbTimeAdapter, useClass: NgbTimeStringAdapter }],
})
export class ScheduleFormComponent implements OnInit {
    @Input() course: Course;
    @Input() parentFormGroup: FormGroup;
    formGroup: FormGroup;

    defaultPeriod?: Date[] = undefined;

    weekDays = weekDays;
    faCalendarAlt = faCalendarAlt;

    get defaultPeriodChanged(): boolean {
        return !_.isEqual(this.defaultPeriod, this.formGroup.get('period')!.value);
    }

    get parentIsOnlineControl() {
        return this.parentFormGroup.get('isOnline');
    }

    get periodControl() {
        return this.formGroup.get('period');
    }

    get startTimeControl() {
        return this.formGroup.get('startTime');
    }

    get endTimeControl() {
        return this.formGroup.get('endTime');
    }

    get locationControl() {
        return this.formGroup.get('location');
    }

    get repetitionFrequencyControl() {
        return this.formGroup.get('repetitionFrequency');
    }

    get createdSessions() {
        const sessions: dayjs.Dayjs[] = [];

        if (this.formGroup.valid) {
            const { dayOfWeek, repetitionFrequency, period } = this.formGroup.value;
            let start = dayjs(period[0]);
            const end = dayjs(period[1]);

            // find the first day of the week
            while (dayOfWeekZeroSundayToZeroMonday(start.day()) + 1 !== dayOfWeek) {
                start = start.add(1, 'day');
            }

            // add sessions
            while (start.isBefore(end) || start.isSame(end)) {
                sessions.push(start);
                start = start.add(repetitionFrequency, 'week');
            }
        }

        return sessions;
    }

    constructor(private fb: FormBuilder, public formatter: NgbDateParserFormatter) {}

    ngOnInit(): void {
        if (this.course.tutorialGroupsConfiguration) {
            const { tutorialPeriodStartInclusive, tutorialPeriodEndInclusive } = this.course.tutorialGroupsConfiguration;
            this.defaultPeriod = [tutorialPeriodStartInclusive!.toDate(), tutorialPeriodEndInclusive!.toDate()];
        }

        this.formGroup = this.fb.group(
            {
                dayOfWeek: [1, Validators.required],
                startTime: ['13:00:00', [Validators.required]],
                endTime: ['14:00:00', [Validators.required]],
                repetitionFrequency: [1, [Validators.required, Validators.min(1), Validators.max(7)]],
                period: [this.defaultPeriod, [Validators.required]],
                location: [undefined, [Validators.required]],
            },
            { validators: validTimeRange },
        );

        this.parentFormGroup.addControl('schedule', this.formGroup);
    }
}
