import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Course, Language } from 'app/core/course/shared/entities/course.model';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { getSemesters } from 'app/shared/util/semester-utils';
import { ARTEMIS_DEFAULT_COLOR, MODULE_FEATURE_IRIS } from 'app/app.constants';
import { KeyValuePipe, NgClass, NgStyle } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faCog, faTimes } from '@fortawesome/free-solid-svg-icons';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { IrisCourseSettingsDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';

@Component({
    selector: 'jhi-onboarding-general-settings',
    templateUrl: './onboarding-general-settings.component.html',
    imports: [
        FormsModule,
        ColorSelectorComponent,
        FormDateTimePickerComponent,
        TranslateDirective,
        NgClass,
        NgStyle,
        KeyValuePipe,
        ArtemisTranslatePipe,
        FaIconComponent,
        DocumentationButtonComponent,
    ],
})
export class OnboardingGeneralSettingsComponent implements OnInit {
    private profileService = inject(ProfileService);
    private irisSettingsService = inject(IrisSettingsService);

    readonly course = input.required<Course>();
    readonly courseUpdated = output<Course>();

    readonly irisEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);
    readonly irisSettings = signal<IrisCourseSettingsDTO | undefined>(undefined);
    readonly isIrisEnabled = computed(() => this.irisSettings()?.enabled ?? false);

    protected readonly ProgrammingLanguage = ProgrammingLanguage;
    protected readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    readonly semesters = getSemesters();

    protected readonly languageOptions: { key: string; value: string }[] = [
        { key: Language.ENGLISH, value: 'English' },
        { key: Language.GERMAN, value: 'German' },
    ];

    protected readonly faCog = faCog;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;

    colorSelectorVisible = false;

    ngOnInit(): void {
        if (this.irisEnabled) {
            const courseId = this.course()?.id;
            if (courseId) {
                this.irisSettingsService.getCourseSettingsWithRateLimit(courseId).subscribe({
                    next: (response) => {
                        if (response) {
                            this.irisSettings.set(response.settings);
                        }
                    },
                });
            }
        }
    }

    setIrisEnabled(enabled: boolean) {
        const courseId = this.course()?.id;
        const currentSettings = this.irisSettings();
        if (!courseId || !currentSettings) {
            return;
        }
        const newSettings: IrisCourseSettingsDTO = { ...currentSettings, enabled };
        this.irisSettings.set(newSettings);
        this.irisSettingsService.updateCourseSettings(courseId, newSettings).subscribe({
            next: (response) => {
                if (response.body) {
                    this.irisSettings.set(response.body.settings);
                }
            },
            error: () => {
                this.irisSettings.set(currentSettings);
            },
        });
    }

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
