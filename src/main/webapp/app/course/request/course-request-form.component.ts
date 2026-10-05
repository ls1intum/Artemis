import { Component, effect, input, output, signal } from '@angular/core';
import { AbstractControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { startWith } from 'rxjs';
import { TumAetUiCheckboxComponent } from '@tumaet/ui-angular';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { SHORT_NAME_PATTERN } from 'app/foundation/constants/input.constants';
import { generateCourseShortName } from 'app/foundation/util/semester-utils';

@Component({
    selector: 'jhi-course-request-form',
    templateUrl: './course-request-form.component.html',
    imports: [
        ReactiveFormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        FormDateTimePickerComponent,
        ButtonModule,
        InputTextModule,
        TextareaModule,
        SelectModule,
        CheckboxModule,
        TumAetUiCheckboxComponent,
    ],
})
export class CourseRequestFormComponent {
    /** The form group containing the course request fields */
    form = input.required<FormGroup>();

    /** List of available semesters */
    semesters = input.required<string[]>();

    /** Whether the date range is invalid (start >= end) */
    dateRangeInvalid = input<boolean>(false);

    /** Prefix for element IDs to ensure uniqueness when used multiple times */
    idPrefix = input<string>('');

    /** Whether to show the reason placeholder text */
    showReasonPlaceholder = input<boolean>(true);

    /** Emitted when the form values change */
    formChange = output<void>();

    protected readonly SHORT_NAME_PATTERN = SHORT_NAME_PATTERN;

    /** Whether the course is currently marked as a test course; drives the hint below the grade relevant control. */
    protected readonly isTestCourse = signal(false);

    constructor() {
        // A test course is never grade relevant, so the grade relevant control follows the test course control.
        effect((onCleanup) => {
            const testCourse = this.form().get('testCourse');
            const gradeRelevant = this.form().get('gradeRelevant');
            if (!testCourse || !gradeRelevant) {
                return;
            }
            const subscription = testCourse.valueChanges.pipe(startWith(testCourse.value)).subscribe((isTestCourse) => {
                this.isTestCourse.set(!!isTestCourse);
                this.syncGradeRelevant(gradeRelevant, !!isTestCourse);
            });
            onCleanup(() => subscription.unsubscribe());
        });
    }

    /**
     * Forces the grade relevant control off and disables it for a test course. When the course is no longer a test course, the control
     * is enabled again and returns to its default (grade relevant).
     *
     * @param gradeRelevant the grade relevant control
     * @param isTestCourse whether the course is currently marked as a test course
     */
    private syncGradeRelevant(gradeRelevant: AbstractControl, isTestCourse: boolean): void {
        if (isTestCourse) {
            if (gradeRelevant.enabled || gradeRelevant.value) {
                gradeRelevant.setValue(false, { emitEvent: false });
                gradeRelevant.disable({ emitEvent: false });
            }
        } else if (gradeRelevant.disabled) {
            gradeRelevant.enable({ emitEvent: false });
            gradeRelevant.setValue(true, { emitEvent: false });
        }
    }

    generateShortName(): void {
        const formGroup = this.form();
        const title = formGroup.get('title')?.value ?? '';
        const semester = formGroup.get('semester')?.value ?? '';
        const shortName = generateCourseShortName(title, semester);
        formGroup.patchValue({ shortName });
        this.formChange.emit();
    }
}
