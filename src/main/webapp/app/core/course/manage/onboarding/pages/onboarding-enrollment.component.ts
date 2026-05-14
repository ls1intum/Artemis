import { Component, inject, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_LTI } from 'app/app.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faTimes, faUserPlus } from '@fortawesome/free-solid-svg-icons';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-onboarding-enrollment',
    templateUrl: './onboarding-enrollment.component.html',
    styleUrls: ['./_onboarding-pages.scss'],
    imports: [
        FormsModule,
        TranslateDirective,
        FormDateTimePickerComponent,
        MarkdownEditorMonacoComponent,
        ArtemisTranslatePipe,
        FaIconComponent,
        DocumentationButtonComponent,
        NgClass,
    ],
})
export class OnboardingEnrollmentComponent {
    private profileService = inject(ProfileService);

    readonly course = input.required<Course>();
    readonly courseUpdated = output<Course>();

    readonly ltiEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_LTI);

    protected readonly faUserPlus = faUserPlus;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;

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
        this.courseUpdated.emit(current);
    }

    toggleUnenrollment() {
        const current = Course.from(this.course());
        current.unenrollmentEnabled = !current.unenrollmentEnabled;
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
        this.courseUpdated.emit(current);
    }

    updateEnrollmentMessage(message: string) {
        const current = Course.from(this.course());
        current.enrollmentConfirmationMessage = message;
        this.courseUpdated.emit(current);
    }
}
