import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';

@Component({
    selector: 'jhi-course-description-form',
    templateUrl: './course-description-form.component.html',
})
export class CourseDescriptionFormComponent implements OnInit {
    @Input() isLoading = false;
    @Input() placeholder = '';
    @Output() formSubmitted: EventEmitter<string> = new EventEmitter<string>();

    form: FormGroup<{ courseDescription: FormControl<string | null> }>;
    hasBeenSubmitted = false;

    //icons
    protected readonly faQuestionCircle = faQuestionCircle;

    //other constants
    protected readonly DESCRIPTION_MAX = 10000;
    protected readonly DESCRIPTION_MIN = 100;
    protected readonly ButtonType = ButtonType;

    constructor(private formBuilder: FormBuilder) {}

    ngOnInit(): void {
        this.form = this.formBuilder.group({
            courseDescription: [this.placeholder, [Validators.required, Validators.minLength(this.DESCRIPTION_MIN), Validators.maxLength(this.DESCRIPTION_MAX)]],
        });
    }

    setCourseDescription(description: string) {
        this.form.controls.courseDescription.setValue(description);
    }

    /**
     * Sends event to parent to handle submission
     */
    submitForm() {
        this.formSubmitted.emit(this.form.value.courseDescription ?? '');
        //save that form has been submitted to change look of the submit button
        this.hasBeenSubmitted = true;
    }

    /**
     * Only allows submitting if no form controls have validation errors
     */
    get isSubmitPossible() {
        return !this.form.invalid;
    }

    get courseDescriptionControl() {
        return this.form.controls.courseDescription;
    }
}
