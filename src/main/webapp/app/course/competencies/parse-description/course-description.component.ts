import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { faCode, faRotateRight } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';

@Component({
    selector: 'jhi-course-description',
    templateUrl: './course-description.component.html',
})
export class CourseDescriptionComponent implements OnInit {
    @Input() isLoading = false;
    @Output()
    formSubmitted: EventEmitter<string> = new EventEmitter<string>();

    form: FormGroup;
    hasBeenSubmitted = false;

    //icons
    protected readonly faRotateRight = faRotateRight;
    protected readonly faCode = faCode;

    constructor(private formBuilder: FormBuilder) {}

    ngOnInit(): void {
        this.form = this.formBuilder.group({
            courseDescription: [undefined as string | undefined, [Validators.required, Validators.minLength(this.DESCRIPTION_MIN), Validators.maxLength(this.DESCRIPTION_MAX)]],
        });
    }

    submitForm() {
        this.formSubmitted.emit(this.form.value.courseDescription);
        this.hasBeenSubmitted = true;
    }

    get courseDescriptionControl() {
        return this.form.get('courseDescription');
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    protected readonly DESCRIPTION_MAX = 10000;
    protected readonly DESCRIPTION_MIN = 100;
    protected readonly ButtonType = ButtonType;
}
