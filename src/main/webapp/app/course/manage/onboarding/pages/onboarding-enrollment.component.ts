import { Component, inject, input, output, signal } from '@angular/core';
import { Course } from 'app/course/shared/entities/course.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumAetUiDatePickerComponent } from '@tumaet/ui-angular';
import { MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_LTI } from 'app/app.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faTimes, faUserPlus } from '@fortawesome/free-solid-svg-icons';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-onboarding-enrollment',
    templateUrl: './onboarding-enrollment.component.html',
    styleUrls: ['./_onboarding-pages.scss'],
    imports: [TranslateDirective, TumAetUiDatePickerComponent, MarkdownEditorMonacoComponent, FaIconComponent, DocumentationButtonComponent, NgClass],
})
export class OnboardingEnrollmentComponent {
    private profileService = inject(ProfileService);

    readonly course = input.required<Course>();
    readonly courseUpdated = output<Course>();
    /**
     * Emits whether the text in every date picker shown is a date. A picker keeps its last valid date while its text is not
     * one, so the wizard uses this to not save and advance with dates the fields no longer show.
     */
    readonly dateTextValidityChange = output<boolean>();

    /** The pickers keep their last valid value while the typed text is not a date, so the text validity is tracked separately. */
    private readonly startTextValid = signal(true);
    private readonly endTextValid = signal(true);
    private readonly unenrollmentEndTextValid = signal(true);

    readonly ltiEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_LTI);

    protected readonly faUserPlus = faUserPlus;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;

    protected setStartTextValid(valid: boolean) {
        this.startTextValid.set(valid);
        this.emitDateTextValidity();
    }

    protected setEndTextValid(valid: boolean) {
        this.endTextValid.set(valid);
        this.emitDateTextValidity();
    }

    protected setUnenrollmentEndTextValid(valid: boolean) {
        this.unenrollmentEndTextValid.set(valid);
        this.emitDateTextValidity();
    }

    /**
     * @param course the course the pickers are shown for; the input still holds the previous one while a toggle is processed
     */
    private emitDateTextValidity(course: Course = this.course()) {
        // The pickers only exist, and only count, while their feature is enabled.
        const enrollmentValid = !course.enrollmentEnabled || (this.startTextValid() && this.endTextValid());
        const unenrollmentValid = !course.unenrollmentEnabled || this.unenrollmentEndTextValid();
        this.dateTextValidityChange.emit(enrollmentValid && unenrollmentValid);
    }

    /** The pickers are removed or created again by a toggle, so none of them holds invalid text afterwards. */
    private resetDateTextValidity(course: Course) {
        this.startTextValid.set(true);
        this.endTextValid.set(true);
        this.unenrollmentEndTextValid.set(true);
        this.emitDateTextValidity(course);
    }

    updateField<K extends keyof Course>(field: K, value: Course[K]) {
        const current = Course.from(this.course());
        current[field] = value;
        this.courseUpdated.emit(current);
    }

    toggleEnrollment() {
        const current = Course.from(this.course());
        current.enrollmentEnabled = !current.enrollmentEnabled;
        if (current.enrollmentEnabled && current.onlineCourse) {
            current.onlineCourse = false;
        }
        if (!current.enrollmentEnabled) {
            current.enrollmentConfirmationMessage = undefined;
            current.unenrollmentEnabled = false;
        }
        this.resetDateTextValidity(current);
        this.courseUpdated.emit(current);
    }

    toggleUnenrollment() {
        const current = Course.from(this.course());
        current.unenrollmentEnabled = !current.unenrollmentEnabled;
        // Only the unenrollment picker is removed or created again.
        this.unenrollmentEndTextValid.set(true);
        this.emitDateTextValidity(current);
        this.courseUpdated.emit(current);
    }

    toggleOnlineCourse() {
        const current = Course.from(this.course());
        current.onlineCourse = !current.onlineCourse;
        if (current.onlineCourse) {
            current.enrollmentEnabled = false;
            current.enrollmentConfirmationMessage = undefined;
            current.unenrollmentEnabled = false;
        }
        this.resetDateTextValidity(current);
        this.courseUpdated.emit(current);
    }

    updateEnrollmentMessage(message: string) {
        const current = Course.from(this.course());
        current.enrollmentConfirmationMessage = message;
        this.courseUpdated.emit(current);
    }
}
