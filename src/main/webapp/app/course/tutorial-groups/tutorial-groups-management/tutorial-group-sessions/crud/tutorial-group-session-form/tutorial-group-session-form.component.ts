import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { NgbTimeAdapter } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { faCalendarAlt } from '@fortawesome/free-solid-svg-icons';
import { NgbTimeStringAdapter } from 'app/course/tutorial-groups/shared/ngbTimeStringAdapter';
import { validTimeRange } from 'app/course/tutorial-groups/shared/timeRangeValidator';

export interface TutorialGroupSessionFormData {
    date?: Date;
    startTime?: string;
    endTime?: string;
    location?: string;
}

@Component({
    selector: 'jhi-tutorial-group-session-form',
    templateUrl: './tutorial-group-session-form.component.html',
    providers: [{ provide: NgbTimeAdapter, useClass: NgbTimeStringAdapter }],
})
export class TutorialGroupSessionFormComponent implements OnInit, OnChanges {
    @Input()
    formData: TutorialGroupSessionFormData = {
        date: undefined,
        startTime: undefined,
        endTime: undefined,
        location: undefined,
    };

    @Input() timeZone: string;
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
