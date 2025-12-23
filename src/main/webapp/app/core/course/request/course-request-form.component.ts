import { Component, input, output } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';

import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { SHORT_NAME_PATTERN } from 'app/shared/constants/input.constants';
import { generateCourseShortName } from 'app/shared/util/semester-utils';

@Component({
    selector: 'jhi-course-request-form',
    templateUrl: './course-request-form.component.html',
    imports: [ReactiveFormsModule, TranslateDirective, ArtemisTranslatePipe, FormDateTimePickerComponent],
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

    generateShortName(): void {
        const formGroup = this.form();
        const title = formGroup.get('title')?.value ?? '';
        const semester = formGroup.get('semester')?.value ?? '';
        const shortName = generateCourseShortName(title, semester);
        formGroup.patchValue({ shortName });
        this.formChange.emit();
    }
}
