import { Component, OnInit, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Course, Language } from 'app/course/shared/entities/course.model';
import { ColorSelectorComponent } from 'app/shared-ui/color-selector/color-selector.component';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { getSemesters } from 'app/foundation/util/semester-utils';
import { ARTEMIS_DEFAULT_COLOR, MODULE_FEATURE_ATHENA, MODULE_FEATURE_IRIS } from 'app/app.constants';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';
import { AlertService } from 'app/foundation/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/foundation/util/global.utils';
import { KeyValuePipe, NgClass, NgStyle } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faCog, faTimes } from '@fortawesome/free-solid-svg-icons';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { IrisCourseSettingsDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { AboutIrisModalComponent } from 'app/iris/overview/about-iris-modal/about-iris-modal.component';
import { AthenaCourseConfigDTO, AthenaCourseConfigService } from 'app/course/manage/services/athena-course-config.service';
import { AthenaFeature } from 'app/course/manage/control-center/athena-enabled/athena-enabled.component';

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
    private athenaCourseConfigService = inject(AthenaCourseConfigService);
    private alertService = inject(AlertService);
    private dialogService = inject(DialogService);
    private aboutIrisDialogRef: DynamicDialogRef<AboutIrisModalComponent> | undefined;

    readonly course = input.required<Course>();
    readonly courseUpdated = output<Course>();

    readonly irisEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);
    readonly irisSettings = signal<IrisCourseSettingsDTO | undefined>(undefined);
    readonly isIrisEnabled = computed(() => this.irisSettings()?.enabled ?? false);

    readonly athenaEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATHENA);
    readonly athenaConfig = signal<AthenaCourseConfigDTO | undefined>(undefined);
    readonly isAthenaFormativeEnabled = computed(() => this.athenaConfig()?.formativeFeedbackEnabled ?? false);
    readonly isAthenaGradingEnabled = computed(() => this.athenaConfig()?.gradingFeedbackEnabled ?? false);

    /** The two Athena toggle rows, rendered by one @for so the markup stays in a single place. */
    protected readonly athenaFeatures = [
        { key: 'formativeFeedbackEnabled' as const, testId: 'onboarding-athena-formative-feedback', enabled: this.isAthenaFormativeEnabled },
        { key: 'gradingFeedbackEnabled' as const, testId: 'onboarding-athena-grading-feedback', enabled: this.isAthenaGradingEnabled },
    ];

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
        const courseId = this.course()?.id;
        if (!courseId) {
            return;
        }
        if (this.irisEnabled) {
            this.irisSettingsService.getCourseSettingsWithRateLimit(courseId).subscribe({
                next: (response) => {
                    if (response) {
                        this.irisSettings.set(response.settings);
                    }
                },
            });
        }
        if (this.athenaEnabled) {
            this.athenaCourseConfigService.getCourseConfig(courseId).subscribe({
                next: (config) => this.athenaConfig.set(config),
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
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

    /**
     * Switch one of the two Athena feedback features and save it right away, like the Iris toggle above: the Athena
     * configuration is not part of the course DTO the wizard saves on step navigation.
     *
     * A course that has never been configured has no stored configuration, and a failed load leaves none either. Both
     * cases count as "both features off" rather than blocking the toggles.
     *
     * @param feature the feature to switch
     * @param enabled whether the feature should be enabled
     */
    setAthenaFeatureEnabled(feature: AthenaFeature, enabled: boolean) {
        const courseId = this.course()?.id;
        const currentConfig = this.athenaConfig() ?? { gradingFeedbackEnabled: false, formativeFeedbackEnabled: false };
        if (!courseId || currentConfig[feature] === enabled) {
            return;
        }
        const newConfig = cloneWith(currentConfig, { [feature]: enabled });
        this.athenaConfig.set(newConfig);
        this.athenaCourseConfigService.updateCourseConfig(courseId, newConfig).subscribe({
            next: (response) => {
                if (response.body) {
                    this.athenaConfig.set(response.body);
                }
            },
            error: (error: HttpErrorResponse) => {
                this.athenaConfig.set(currentConfig);
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
