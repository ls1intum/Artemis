import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { faCode, faRotateRight } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';

@Component({
    selector: 'jhi-course-description',
    templateUrl: './course-description.component.html',
})
export class CourseDescriptionComponent implements OnInit {
    @Input() isLoading = false;
    @Output() formSubmitted: EventEmitter<string> = new EventEmitter<string>();

    form: FormGroup<{ courseDescription: FormControl<string | null> }>;
    hasBeenSubmitted = false;

    //icons
    protected readonly faRotateRight = faRotateRight;
    protected readonly faCode = faCode;

    //other constants
    protected readonly DESCRIPTION_MAX = 10000;
    protected readonly DESCRIPTION_MIN = 100;
    protected readonly ButtonType = ButtonType;

    constructor(private formBuilder: FormBuilder) {}

    ngOnInit(): void {
        this.form = this.formBuilder.group({
            courseDescription: ['', [Validators.required, Validators.minLength(this.DESCRIPTION_MIN), Validators.maxLength(this.DESCRIPTION_MAX)]],
        });
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
