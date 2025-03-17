import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { IrisLogoButtonComponent } from 'app/iris/iris-logo-button/iris-logo-button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-course-description-form',
    templateUrl: './course-description-form.component.html',
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, FaIconComponent, NgbTooltip, IrisLogoButtonComponent, ArtemisTranslatePipe],
})
export class CourseDescriptionFormComponent implements OnInit {
    private formBuilder = inject(FormBuilder);

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
