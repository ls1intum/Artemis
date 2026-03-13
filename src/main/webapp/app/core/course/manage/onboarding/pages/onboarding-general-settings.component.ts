import { Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Course, Language } from 'app/core/course/shared/entities/course.model';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { getSemesters } from 'app/shared/util/semester-utils';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { KeyValuePipe, NgStyle } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCog } from '@fortawesome/free-solid-svg-icons';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';

@Component({
    selector: 'jhi-onboarding-general-settings',
    templateUrl: './onboarding-general-settings.component.html',
    imports: [
        FormsModule,
        ColorSelectorComponent,
        FormDateTimePickerComponent,
        TranslateDirective,
        NgStyle,
        KeyValuePipe,
        ArtemisTranslatePipe,
        FaIconComponent,
        DocumentationButtonComponent,
    ],
})
export class OnboardingGeneralSettingsComponent {
    readonly course = input.required<Course>();
    readonly courseUpdated = output<Course>();

    protected readonly ProgrammingLanguage = ProgrammingLanguage;
    protected readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    readonly semesters = getSemesters();

    protected readonly languageOptions: { key: string; value: string }[] = [
        { key: Language.ENGLISH, value: 'English' },
        { key: Language.GERMAN, value: 'German' },
    ];

    protected readonly faCog = faCog;

    colorSelectorVisible = false;

    updateField(field: keyof Course, value: any) {
        const updated = { ...this.course(), [field]: value };
        this.courseUpdated.emit(updated);
    }

    openColorSelector(event: MouseEvent) {
        event.stopPropagation();
        this.colorSelectorVisible = !this.colorSelectorVisible;
    }

    onSelectedColor(color: string) {
        const updated = { ...this.course(), color };
        this.colorSelectorVisible = false;
        this.courseUpdated.emit(updated);
    }
}
