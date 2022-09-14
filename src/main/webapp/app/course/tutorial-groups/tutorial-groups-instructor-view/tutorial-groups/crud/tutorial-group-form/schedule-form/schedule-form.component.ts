import { Component, Injectable, Input, OnInit } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { faCalendarAlt } from '@fortawesome/free-solid-svg-icons';
import { NgbDateParserFormatter, NgbTimeAdapter, NgbTimeStruct } from '@ng-bootstrap/ng-bootstrap';
import { weekDays } from 'app/course/tutorial-groups/shared/weekdays';
import { Course } from 'app/entities/course.model';
import _ from 'lodash';
import dayjs from 'dayjs/esm';
import { dayOfWeekZeroSundayToZeroMonday } from 'app/utils/date.utils';

export interface ScheduleFormData {
    dayOfWeek?: number;
    startTime?: string;
    endTime?: string;
    repetitionFrequency?: number;
    period?: Date[];
    location?: string;
}

@Injectable()
class NgbTimeStringAdapter extends NgbTimeAdapter<string> {
    fromModel(value: string | null): NgbTimeStruct | null {
        if (!value) {
            return null;
        }
        const split = value.split(':');
        return {
            hour: parseInt(split[0], 10),
            minute: parseInt(split[1], 10),
            second: parseInt(split[2], 10),
        };
    }

    toModel(time: NgbTimeStruct | null): string | null {
        return time !== null ? `${pad(time.hour)}:${pad(time.minute)}:${pad(time.second)}` : null;
    }
}

const validTimeRange = (control: AbstractControl): ValidationErrors | null => {
    if (!control.get('startTime')!.value || !control.get('endTime')!.value) {
        return null;
    }

    const startTime = control.get('startTime')!.value;
    const endTime = control.get('endTime')!.value;

    const startComparison = dayjs('1970-01-01 ' + startTime, 'YYYY-MM-DD HH:mm:ss');
    const endComparison = dayjs('1970-01-01 ' + endTime, 'YYYY-MM-DD HH:mm:ss');
    if (startComparison.isAfter(endComparison)) {
        return {
            invalidTimeRange: true,
        };
    } else {
        return null;
    }
};

const pad = (i: number): string => (i < 10 ? `0${i}` : `${i}`);

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
