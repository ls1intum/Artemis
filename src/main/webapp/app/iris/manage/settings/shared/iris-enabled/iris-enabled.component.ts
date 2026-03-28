import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/core/course/shared/entities/course.model';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { faArrowRight } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IrisCourseSettingsDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { deepClone } from 'app/shared/util/deep-clone.util';

/**
 * Simple toggle component for enabling/disabling Iris at the course level.
 * Replaces the complex feature-specific toggle with a unified course-level control.
 */
@Component({
    selector: 'jhi-iris-enabled',
    templateUrl: './iris-enabled.component.html',
    imports: [TranslateDirective, RouterLink, FaIconComponent],
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    styles: [
        `
            :host {
                display: block;
                width: 100%;
            }

            .iris-controls {
                display: flex;
                flex-direction: column;
                gap: 0.75rem;
            }

            .iris-toggle-group {
                display: flex;
                border: 1px solid var(--bs-border-color);
                border-radius: 0.625rem;
                overflow: hidden;
            }

            .iris-toggle-btn {
                flex: 1;
                padding: 0.5rem 1rem;
                border: none;
                background: var(--overview-card-nested-bg, var(--bs-body-bg));
                color: var(--bs-secondary-color);
                font-weight: 500;
                font-size: 0.88rem;
                cursor: pointer;
                transition: all 0.2s ease;

                &:first-child {
                    border-right: 1px solid var(--bs-border-color);
                }
            }

            .iris-toggle-btn--active-on {
                background: #10b981;
                color: white;
                font-weight: 600;
            }

            .iris-toggle-btn--active-off {
                background: #ef4444;
                color: white;
                font-weight: 600;
            }

            .iris-configure-link {
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 0.4rem;
                padding: 0.5rem 1rem;
                border: 1px solid var(--bs-border-color);
                border-radius: 0.625rem;
                background: var(--overview-card-nested-bg, var(--bs-body-bg));
                color: var(--bs-body-color);
                font-weight: 500;
                font-size: 0.88rem;
                text-decoration: none;
                transition: all 0.2s ease;

                &:hover {
                    background: var(--bs-tertiary-bg);
                    border-color: var(--bs-secondary-border-subtle);
                    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
                }
            }
        `,
    ],
})
export class IrisEnabledComponent implements OnInit {
    protected readonly faArrowRight = faArrowRight;
    private irisSettingsService = inject(IrisSettingsService);
    private alertService = inject(AlertService);

    course = input.required<Course>();

    settings = signal<IrisCourseSettingsDTO | undefined>(undefined);

    readonly isEnabled = computed(() => this.settings()?.enabled ?? false);
    readonly isDisabled = computed(() => !this.isEnabled());

    ngOnInit(): void {
        const courseId = this.course()?.id;
        if (courseId) {
            this.irisSettingsService.getCourseSettingsWithRateLimit(courseId).subscribe({
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
     * Toggle the enabled state and save to server
     */
    setEnabled(enabled: boolean) {
        const courseId = this.course()?.id;
        const currentSettings = this.settings();

        if (!courseId || !currentSettings) {
            return;
        }

        // Optimistic UI update — clone to avoid mutating the signal value in place
        const newSettings = deepClone(currentSettings);
        newSettings.enabled = enabled;
        this.settings.set(newSettings);

        // Save to server
        this.irisSettingsService.updateCourseSettings(courseId, newSettings).subscribe({
            next: (response) => {
                if (response.body) {
                    this.settings.set(response.body.settings);
                }
            },
            error: (error: HttpErrorResponse) => {
                this.settings.set(currentSettings);
                onError(this.alertService, error);
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
