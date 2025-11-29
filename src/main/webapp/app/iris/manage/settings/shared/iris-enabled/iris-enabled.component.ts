import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass } from '@angular/common';
import { RouterLink } from '@angular/router';
import { faArrowRight } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IrisCourseSettingsDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';

/**
 * Simple toggle component for enabling/disabling Iris at the course level.
 * Replaces the complex feature-specific toggle with a unified course-level control.
 */
@Component({
    selector: 'jhi-iris-enabled',
    templateUrl: './iris-enabled.component.html',
    imports: [TranslateDirective, NgClass, RouterLink, FaIconComponent],
    standalone: true,
})
export class IrisEnabledComponent implements OnInit {
    protected readonly faArrowRight = faArrowRight;
    private irisSettingsService = inject(IrisSettingsService);

    course = input.required<Course>();

    settings = signal<IrisCourseSettingsDTO | undefined>(undefined);

    readonly isEnabled = computed(() => this.settings()?.enabled ?? false);
    readonly isDisabled = computed(() => !this.isEnabled());

    ngOnInit(): void {
        const courseId = this.course()?.id;
        if (courseId) {
            this.irisSettingsService.getCourseSettings(courseId).subscribe({
                next: (response) => {
                    if (response) {
                        this.settings.set(response.settings);
                    }
                },
                error: () => {
                    // Silently fail - the component will show as disabled
                },
            });
        }
    }

    /**
     * Toggle the enabled state and save to backend
     */
    setEnabled(enabled: boolean) {
        const courseId = this.course()?.id;
        const currentSettings = this.settings();

        if (!courseId || !currentSettings) {
            return;
        }

        // Optimistic UI update
        const newSettings: IrisCourseSettingsDTO = {
            ...currentSettings,
            enabled,
        };
        this.settings.set(newSettings);

        // Save to backend
        this.irisSettingsService.updateCourseSettings(courseId, newSettings).subscribe({
            next: (response) => {
                if (response.body) {
                    this.settings.set(response.body.settings);
                }
            },
            error: () => {
                // Revert on error
                this.settings.set(currentSettings);
            },
        });
    }

    /**
     * Get the route to the settings page
     */
    getSettingsRoute(): string[] {
        const courseId = this.course()?.id;
        return ['/course-management', String(courseId), 'iris-settings'];
    }
}
