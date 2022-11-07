import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { faCalendarAlt } from '@fortawesome/free-solid-svg-icons';

export interface TutorialGroupsConfigurationFormData {
    period?: Date[];
}

@Component({
    selector: 'jhi-tutorial-groups-configuration-form',
    templateUrl: './tutorial-groups-configuration-form.component.html',
})
export class TutorialGroupsConfigurationFormComponent implements OnInit, OnChanges {
    @Input()
    formData: TutorialGroupsConfigurationFormData = {
        period: undefined,
    };
    @Input() isEditMode = false;
    @Output() formSubmitted: EventEmitter<TutorialGroupsConfigurationFormData> = new EventEmitter<TutorialGroupsConfigurationFormData>();

    faCalendarAlt = faCalendarAlt;

    form: FormGroup;

    constructor(private fb: FormBuilder) {}

    get periodControl() {
        return this.form.get('period');
    }

    get isSubmitPossible() {
        return !this.form.invalid;
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
    private setFormValues(formData: TutorialGroupsConfigurationFormData) {
        this.form.patchValue(formData);
    }

    private initializeForm() {
        if (this.form) {
            return;
        }

        this.form = this.fb.group({
            period: [undefined, Validators.required],
        });
    }

    submitForm() {
        this.formSubmitted.emit({ ...this.form.value });
    }

    get isPeriodInvalid() {
        if (this.periodControl) {
            return this.periodControl.invalid && (this.periodControl.touched || this.periodControl.dirty);
        } else {
            return false;
        }
    }

    markPeriodAsTouched() {
        if (this.periodControl) {
            this.periodControl.markAsTouched();
        }
    }
}
