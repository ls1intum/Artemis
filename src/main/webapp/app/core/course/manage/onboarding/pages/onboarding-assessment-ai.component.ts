import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS, MODULE_FEATURE_IRIS } from 'app/app.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChartLine, faCheck, faTimes } from '@fortawesome/free-solid-svg-icons';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { IrisCourseSettingsDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-onboarding-assessment-ai',
    templateUrl: './onboarding-assessment-ai.component.html',
    imports: [FormsModule, TranslateDirective, FaIconComponent, DocumentationButtonComponent, NgClass],
})
export class OnboardingAssessmentAiComponent implements OnInit {
    private profileService = inject(ProfileService);
    private irisSettingsService = inject(IrisSettingsService);

    readonly course = input.required<Course>();
    readonly courseUpdated = output<Course>();

    readonly irisEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);
    readonly atlasEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS);

    readonly irisSettings = signal<IrisCourseSettingsDTO | undefined>(undefined);
    readonly isIrisEnabled = computed(() => this.irisSettings()?.enabled ?? false);

    protected readonly faChartLine = faChartLine;
    protected readonly faCheck = faCheck;
    protected readonly faTimes = faTimes;

    get complaintsEnabled(): boolean {
        const c = this.course();
        return (c.maxComplaintTimeDays ?? 0) > 0;
    }

    get requestMoreFeedbackEnabled(): boolean {
        return (this.course().maxRequestMoreFeedbackTimeDays ?? 0) > 0;
    }

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

    toggleComplaints() {
        const updated = { ...this.course() };
        if (this.complaintsEnabled) {
            updated.maxComplaints = 0;
            updated.maxTeamComplaints = 0;
            updated.maxComplaintTimeDays = 0;
        } else {
            updated.maxComplaints = 3;
            updated.maxTeamComplaints = 3;
            updated.maxComplaintTimeDays = 7;
            updated.maxComplaintTextLimit = 2000;
            updated.maxComplaintResponseTextLimit = 2000;
        }
        this.courseUpdated.emit(updated);
    }

    toggleRequestMoreFeedback() {
        const updated = { ...this.course() };
        if (this.requestMoreFeedbackEnabled) {
            updated.maxRequestMoreFeedbackTimeDays = 0;
        } else {
            updated.maxRequestMoreFeedbackTimeDays = 7;
        }
        this.courseUpdated.emit(updated);
    }

    toggleLearningPaths() {
        const updated = { ...this.course(), learningPathsEnabled: !this.course().learningPathsEnabled };
        this.courseUpdated.emit(updated);
    }
}
