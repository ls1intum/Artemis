import { Component, EventEmitter, Injectable, Input, OnChanges, OnInit, Output } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { NgbTimeAdapter, NgbTimeStruct } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';
import { Course } from 'app/entities/course.model';
import { faCalendarAlt } from '@fortawesome/free-solid-svg-icons';

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

export interface TutorialGroupSessionFormData {
    date?: Date;
    startTime?: string;
    endTime?: string;
    location?: string;
}

@Component({
    selector: 'jhi-tutorial-group-session-form',
    templateUrl: './tutorial-group-session-form.component.html',
    styleUrls: ['./tutorial-group-session-form.component.scss'],
    providers: [{ provide: NgbTimeAdapter, useClass: NgbTimeStringAdapter }],
})
export class TutorialGroupSessionFormComponent implements OnInit, OnChanges {
    @Input()
    formData: TutorialGroupSessionFormData = {
        date: undefined,
        startTime: undefined,
        endTime: undefined,
    };

    @Input() course?: Course;
    @Input() isEditMode = false;
    @Output() formSubmitted: EventEmitter<TutorialGroupSessionFormData> = new EventEmitter<TutorialGroupSessionFormData>();
    faCalendarAlt = faCalendarAlt;

    form: FormGroup;
    get dateControl() {
        return this.form.get('date');
    }

    get startTimeControl() {
        return this.form.get('startTime');
    }

    get endTimeControl() {
        return this.form.get('endTime');
    }

    get locationControl() {
        return this.form.get('location');
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    constructor(private fb: FormBuilder) {}

    ngOnInit(): void {
        this.initializeForm();
    }

    ngOnChanges(): void {
        this.initializeForm();
        if (this.isEditMode && this.formData) {
            this.setFormValues(this.formData);
        }
    }

    submitForm() {
        const tutorialGroupSessionFormData: TutorialGroupSessionFormData = { ...this.form.value };
        this.formSubmitted.emit(tutorialGroupSessionFormData);
    }

    private setFormValues(formData: TutorialGroupSessionFormData) {
        this.form.patchValue(formData);
    }

    private initializeForm() {
        if (this.form) {
            return;
        }
        this.form = this.fb.group(
            {
                startTime: ['13:00:00', [Validators.required]],
                endTime: ['14:00:00', [Validators.required]],
                date: [undefined, [Validators.required]],
                location: [undefined, [Validators.required]],
            },
            { validators: validTimeRange },
        );
    }
}
