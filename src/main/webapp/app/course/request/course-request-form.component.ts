import { Component, computed, effect, input, output, signal } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { startWith } from 'rxjs';
import { TumAetUiCheckboxComponent } from '@tumaet/ui-angular';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';

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

    /** Whether the course is currently marked as a test course; drives the grade-relevant checkbox and its hint. */
    protected readonly isTestCourse = signal(false);

    /** The value the grade-relevant control holds, i.e. the choice for a regular course. It is kept while the course is marked as a test course. */
    private readonly gradeRelevantChoice = signal(true);

    /**
     * What the grade-relevant checkbox shows. A test course is never grade-relevant, so it shows unchecked (and disabled). The control itself keeps the choice
     * for a regular course, so unchecking "test course" brings it back, whatever the order in which a form reset writes the two controls.
     */
    protected readonly gradeRelevantChecked = computed(() => !this.isTestCourse() && this.gradeRelevantChoice());

    constructor() {
        effect((onCleanup) => {
            const testCourse = this.form().get('testCourse');
            const gradeRelevant = this.form().get('gradeRelevant');
            const subscriptions = [
                testCourse?.valueChanges.pipe(startWith(testCourse.value)).subscribe((value) => this.isTestCourse.set(!!value)),
                gradeRelevant?.valueChanges.pipe(startWith(gradeRelevant.value)).subscribe((value) => this.gradeRelevantChoice.set(value ?? true)),
            ];
            onCleanup(() => subscriptions.forEach((subscription) => subscription?.unsubscribe()));
        });
    }

    /**
     * Stores the choice made on the grade-relevant checkbox in the form.
     *
     * @param checked whether the user checked the box
     */
    protected onGradeRelevantChange(checked: boolean): void {
        const gradeRelevant = this.form().get('gradeRelevant');
        gradeRelevant?.setValue(checked);
        gradeRelevant?.markAsDirty();
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
