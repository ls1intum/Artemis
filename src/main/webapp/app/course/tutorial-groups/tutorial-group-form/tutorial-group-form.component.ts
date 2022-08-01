import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

export interface TutorialGroupFormData {
    title?: string;
}

const nonWhitespaceRegExp: RegExp = new RegExp('\\S');

@Component({
    selector: 'jhi-tutorial-group-form',
    templateUrl: './tutorial-group-form.component.html',
})
export class TutorialGroupFormComponent implements OnInit, OnChanges {
    @Input()
    formData: TutorialGroupFormData = {
        title: undefined,
    };

    @Input() isEditMode = false;
    @Output() formSubmitted: EventEmitter<TutorialGroupFormData> = new EventEmitter<TutorialGroupFormData>();
    form: FormGroup;

    constructor(private fb: FormBuilder) {}

    get titleControl() {
        return this.form.get('title');
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

    private initializeForm() {
        if (this.form) {
            return;
        }

        this.form = this.fb.group({
            title: [undefined, [Validators.required, Validators.maxLength(255), Validators.pattern(nonWhitespaceRegExp)]],
        });
    }

    private setFormValues(formData: TutorialGroupFormData) {
        this.form.patchValue(formData);
    }

    submitForm() {
        const tutorialGroupFormData: TutorialGroupFormData = { ...this.form.value };
        this.formSubmitted.emit(tutorialGroupFormData);
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }
}
