import { Component, EventEmitter, Input, OnChanges, OnInit, Output, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { faCalendarAlt } from '@fortawesome/free-solid-svg-icons';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { merge, Observable, OperatorFunction, Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, filter, map } from 'rxjs/operators';

export interface TutorialGroupsConfigurationFormData {
    period?: Date[];
    timeZone?: string;
}

@Component({
    selector: 'jhi-tutorial-groups-configuration-form',
    templateUrl: './tutorial-groups-configuration-form.component.html',
})
export class TutorialGroupsConfigurationFormComponent implements OnInit, OnChanges {
    @Input()
    formData: TutorialGroupsConfigurationFormData = {
        period: undefined,
        timeZone: undefined,
    };
    @Input() isEditMode = false;
    @Output() formSubmitted: EventEmitter<TutorialGroupsConfigurationFormData> = new EventEmitter<TutorialGroupsConfigurationFormData>();

    originalTimeZone?: string;
    timeZones: string[] = [];

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
        this.timeZones = (Intl as any).supportedValuesOf('timeZone');
        this.initializeForm();
    }
    ngOnChanges(): void {
        this.initializeForm();
        if (this.isEditMode && this.formData) {
            this.setFormValues(this.formData);
        }
    }

    tzResultFormatter = (timeZone: string) => timeZone;
    tzInputFormatter = (timeZone: string) => timeZone;

    tzSearch: OperatorFunction<string, readonly string[]> = (text$: Observable<string>) => {
        const debouncedText$ = text$.pipe(debounceTime(200), distinctUntilChanged());
        const clicksWithClosedPopup$ = this.tzClick$.pipe(filter(() => !this.tzTypeAhead.isPopupOpen()));
        const inputFocus$ = this.tzFocus$;

        return merge(debouncedText$, inputFocus$, clicksWithClosedPopup$).pipe(
            map((term) => (term.length < 3 ? [] : this.timeZones.filter((tz) => tz.toLowerCase().indexOf(term.toLowerCase()) > -1))),
        );
    };

    private setFormValues(formData: TutorialGroupsConfigurationFormData) {
        this.originalTimeZone = formData.timeZone;
        this.form.patchValue(formData);
    }

    private initializeForm() {
        if (this.form) {
            return;
        }

        this.form = this.fb.group({
            timeZone: ['Europe/Berlin', [Validators.required]],
            period: [undefined, Validators.required],
        });
    }

    submitForm() {
        this.formSubmitted.emit({ ...this.form.value });
    }
}
