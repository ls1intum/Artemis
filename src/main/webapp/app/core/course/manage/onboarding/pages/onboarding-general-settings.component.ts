import { Component, OnInit, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Course, Language } from 'app/core/course/shared/entities/course.model';
import { ColorSelectorComponent } from 'app/shared/color-selector/color-selector.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { getSemesters } from 'app/shared/util/semester-utils';
import { ARTEMIS_DEFAULT_COLOR, MODULE_FEATURE_IRIS } from 'app/app.constants';
import { deepClone } from 'app/shared/util/deep-clone.util';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { KeyValuePipe, NgClass, NgStyle } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faCog, faTimes } from '@fortawesome/free-solid-svg-icons';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { IrisCourseSettingsDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { AboutIrisModalComponent } from 'app/iris/overview/about-iris-modal/about-iris-modal.component';

@Component({
    selector: 'jhi-onboarding-general-settings',
    templateUrl: './onboarding-general-settings.component.html',
    styleUrls: ['./_onboarding-pages.scss'],
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
        IrisLogoComponent,
    ],
})
export class OnboardingGeneralSettingsComponent implements OnInit {
    protected readonly IrisLogoSize = IrisLogoSize;
    private profileService = inject(ProfileService);
    private irisSettingsService = inject(IrisSettingsService);
    private alertService = inject(AlertService);
    private dialogService = inject(DialogService);
    private aboutIrisDialogRef: DynamicDialogRef<AboutIrisModalComponent> | undefined;

    readonly course = input.required<Course>();
    readonly courseUpdated = output<Course>();

    readonly irisEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);
    readonly irisSettings = signal<IrisCourseSettingsDTO | undefined>(undefined);
    readonly isIrisEnabled = computed(() => this.irisSettings()?.enabled ?? false);

    protected readonly ProgrammingLanguage = ProgrammingLanguage;
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    readonly semesters = getSemesters();

    readonly languageOptions: { key: string; value: string }[] = [
        { key: Language.ENGLISH, value: 'English' },
        { key: Language.GERMAN, value: 'German' },
    ];

    protected readonly faCog = faCog;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;

    readonly colorSelector = viewChild(ColorSelectorComponent);

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
        const newSettings = deepClone(currentSettings);
        newSettings.enabled = enabled;
        this.irisSettings.set(newSettings);
        this.irisSettingsService.updateCourseSettings(courseId, newSettings).subscribe({
            next: (response) => {
                if (response.body) {
                    this.irisSettings.set(response.body.settings);
                }
            },
            error: (error: HttpErrorResponse) => {
                this.irisSettings.set(currentSettings);
                onError(this.alertService, error);
            },
        });
    }

    updateField<K extends keyof Course>(field: K, value: Course[K]) {
        const current = Course.from(this.course());
        current[field] = value;
        this.courseUpdated.emit(current);
    }

    openColorSelector(event: MouseEvent) {
        this.colorSelector()?.openColorSelector(event);
    }

    onSelectedColor(color: string) {
        const current = Course.from(this.course());
        current.color = color;
        this.courseUpdated.emit(current);
    }

    openAboutIrisModal(): void {
        this.aboutIrisDialogRef?.close();
        this.aboutIrisDialogRef =
            this.dialogService.open(AboutIrisModalComponent, {
                modal: true,
                closable: false,
                dismissableMask: true,
                showHeader: false,
                styleClass: 'about-iris-dialog',
                maskStyleClass: 'about-iris-dialog',
                width: '40rem',
                breakpoints: { '640px': '95vw' },
                data: { hideTryButton: true },
            }) ?? undefined;
    }
}
