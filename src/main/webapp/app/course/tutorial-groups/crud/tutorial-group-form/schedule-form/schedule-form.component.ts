import { Component, Injectable, Input, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { faCalendarAlt } from '@fortawesome/free-solid-svg-icons';
import { NgbCalendar, NgbDate, NgbDateParserFormatter, NgbTimeAdapter, NgbTimeStruct, NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { merge, Observable, OperatorFunction, Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, filter, map } from 'rxjs/operators';
import timezones from 'timezones-list';

export interface ScheduleFormData {
    dayOfWeek?: number;
    startTime?: string;
    endTime?: string;
    repetitionFrequency?: number;
    timeZone?: TimeZone;
    period?: Date[];
}

interface TimeZone {
    label: string;
    tzCode: string;
    name: string;
    utc: string;
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

const pad = (i: number): string => (i < 10 ? `0${i}` : `${i}`);

@Component({
    selector: 'jhi-schedule-form',
    templateUrl: './schedule-form.component.html',
    styleUrls: ['./schedule-form.component.scss'],
    providers: [{ provide: NgbTimeAdapter, useClass: NgbTimeStringAdapter }],
})
export class ScheduleFormComponent implements OnInit {
    @Input() parentFormGroup: FormGroup;
    formGroup: FormGroup;

    @Input() hoveredDate: NgbDate | null;
    @Input() fromDate: NgbDate | null;
    @Input() toDate: NgbDate | null;

    @ViewChild('timeZoneInput') tzTypeAhead: NgbTypeahead;
    tzFocus$ = new Subject<string>();
    tzClick$ = new Subject<string>();

    weekDays = [
        {
            id: 'monday',
            translationKey: 'monday',
            value: 1,
        },
        {
            id: 'tuesday',
            translationKey: 'tuesday',
            value: 2,
        },
        {
            id: 'wednesday',
            translationKey: 'wednesday',
            value: 3,
        },
        {
            id: 'thursday',
            translationKey: 'thursday',
            value: 4,
        },
        {
            id: 'friday',
            translationKey: 'friday',
            value: 5,
        },
        {
            id: 'saturday',
            translationKey: 'saturday',
            value: 6,
        },
        {
            id: 'sunday',
            translationKey: 'sunday',
            value: 7,
        },
    ];

    faCalendarAlt = faCalendarAlt;
    tzResultFormatter = (timeZone: TimeZone) => timeZone.name;
    tzInputFormatter = (timeZone: TimeZone) => timeZone.tzCode;

    get timeZoneControl() {
        return this.formGroup.get('timeZone');
    }

    tzSearch: OperatorFunction<string, readonly TimeZone[]> = (text$: Observable<string>) => {
        const debouncedText$ = text$.pipe(debounceTime(200), distinctUntilChanged());
        const clicksWithClosedPopup$ = this.tzClick$.pipe(filter(() => !this.tzTypeAhead.isPopupOpen()));
        const inputFocus$ = this.tzFocus$;

        return merge(debouncedText$, inputFocus$, clicksWithClosedPopup$).pipe(
            map((term) => (term.length < 3 ? [] : timezones.filter((tz) => tz.name.toLowerCase().indexOf(term.toLowerCase()) > -1))),
        );
    };
    constructor(private fb: FormBuilder, private calendar: NgbCalendar, public formatter: NgbDateParserFormatter) {
        this.fromDate = calendar.getToday();
        this.toDate = calendar.getNext(calendar.getToday(), 'd', 10);
    }

    ngOnInit(): void {
        this.formGroup = this.fb.group({
            dayOfWeek: [1, Validators.required],
            startTime: ['13:00:00', [Validators.required]],
            endTime: ['14:00:00', [Validators.required]],
            repetitionFrequency: [1, [Validators.required]],
            period: [undefined, [Validators.required]],
            timeZone: [
                {
                    label: 'Europe/Berlin (GMT+01:00)',
                    tzCode: 'Europe/Berlin',
                    name: '(GMT+01:00) Berlin, Hamburg, Munich, Köln, Frankfurt am Main',
                    utc: '+01:00',
                },
                [Validators.required],
            ],
        });

        this.parentFormGroup.addControl('schedule', this.formGroup);
    }
}
