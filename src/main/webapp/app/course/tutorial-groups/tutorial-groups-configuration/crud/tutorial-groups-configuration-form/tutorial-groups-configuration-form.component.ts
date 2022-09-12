import { Component, EventEmitter, Input, OnChanges, OnInit, Output, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { faCalendarAlt } from '@fortawesome/free-solid-svg-icons';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { merge, Observable, OperatorFunction, Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, filter, map } from 'rxjs/operators';
import timezones from 'timezones-list';

interface TimeZone {
    label: string;
    tzCode: string;
    name: string;
    utc: string;
}

export interface ConfigurationFormData {
    period?: Date[];
    timeZone?: TimeZone;
}

@Component({
    selector: 'jhi-tutorial-groups-configuration-form',
    templateUrl: './tutorial-groups-configuration-form.component.html',
})
export class TutorialGroupsConfigurationFormComponent implements OnInit, OnChanges {
    @Input()
    formData: ConfigurationFormData = {
        period: undefined,
        timeZone: undefined,
    };
    @Input() isEditMode = false;
    @Output() formSubmitted: EventEmitter<ConfigurationFormData> = new EventEmitter<ConfigurationFormData>();

    originalTimeZone?: TimeZone;

    @ViewChild('timeZoneInput') tzTypeAhead: NgbTypeahead;
    tzFocus$ = new Subject<string>();
    tzClick$ = new Subject<string>();
    faCalendarAlt = faCalendarAlt;

    form: FormGroup;

    constructor(private fb: FormBuilder) {}

    get timeZoneControl() {
        return this.form.get('timeZone');
    }

    get periodControl() {
        return this.form.get('period');
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    get timeZoneChanged() {
        return this.isEditMode && this.originalTimeZone !== this.form.value.timeZone;
    }

    ngOnInit(): void {
        this.initializeForm();
    }
    ngOnChanges(): void {
        this.initializeForm();
        if (this.isEditMode && this.formData) {
            this.setFormValues(this.formData);
        }
    }

    tzResultFormatter = (timeZone: TimeZone) => timeZone.name;
    tzInputFormatter = (timeZone: TimeZone) => timeZone.tzCode;

    tzSearch: OperatorFunction<string, readonly TimeZone[]> = (text$: Observable<string>) => {
        const debouncedText$ = text$.pipe(debounceTime(200), distinctUntilChanged());
        const clicksWithClosedPopup$ = this.tzClick$.pipe(filter(() => !this.tzTypeAhead.isPopupOpen()));
        const inputFocus$ = this.tzFocus$;

        return merge(debouncedText$, inputFocus$, clicksWithClosedPopup$).pipe(
            map((term) => (term.length < 3 ? [] : timezones.filter((tz) => tz.name.toLowerCase().indexOf(term.toLowerCase()) > -1))),
        );
    };

    private setFormValues(formData: ConfigurationFormData) {
        this.originalTimeZone = formData.timeZone;
        this.form.patchValue(formData);
    }

    private initializeForm() {
        if (this.form) {
            return;
        }

        this.form = this.fb.group({
            timeZone: [
                {
                    label: 'Europe/Berlin (GMT+01:00)',
                    tzCode: 'Europe/Berlin',
                    name: '(GMT+01:00) Berlin, Hamburg, Munich, Köln, Frankfurt am Main',
                    utc: '+01:00',
                },
                [Validators.required],
            ],
            period: [undefined, Validators.required],
        });
    }

    submitForm() {
        this.formSubmitted.emit({ ...this.form.value });
    }
}
