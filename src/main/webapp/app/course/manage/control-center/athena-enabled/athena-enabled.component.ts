import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/course/shared/entities/course.model';
import { AthenaCourseConfigDTO, AthenaCourseConfigService } from 'app/course/manage/services/athena-course-config.service';
import { EnabledToggleComponent } from 'app/shared-ui/enabled-toggle/enabled-toggle.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { TumUiTooltipDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

/** The two independently switchable Athena feedback features of a course. */
export type AthenaFeature = 'formativeFeedbackEnabled' | 'gradingFeedbackEnabled';

/** What a course whose Athena configuration has not loaded (yet) is treated as. */
const DISABLED_CONFIG: AthenaCourseConfigDTO = { gradingFeedbackEnabled: false, formativeFeedbackEnabled: false };

/**
 * Toggles for the course-level Athena feedback features, shown next to the Iris toggle on the course overview.
 * Each change is saved immediately; Athena is referred to by name only, it has no logo.
 */
@Component({
    selector: 'jhi-athena-enabled',
    templateUrl: './athena-enabled.component.html',
    imports: [EnabledToggleComponent, TranslateDirective, ArtemisTranslatePipe, TumUiTooltipDirective, FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    styles: [
        `
            :host {
                display: block;
                width: 100%;
            }

            .athena-controls {
                display: flex;
                flex-direction: column;
                gap: 0.75rem;
            }

            .athena-setting-label {
                display: flex;
                align-items: center;
                gap: 0.35rem;
                font-size: 0.82rem;
                color: var(--p-text-muted-color);
                margin-bottom: 0.25rem;
            }
        `,
    ],
})
export class AthenaEnabledComponent implements OnInit {
    protected readonly faQuestionCircle = faQuestionCircle;

    private athenaCourseConfigService = inject(AthenaCourseConfigService);
    private alertService = inject(AlertService);

    course = input.required<Course>();

    config = signal<AthenaCourseConfigDTO | undefined>(undefined);

    readonly formativeEnabled = computed(() => this.config()?.formativeFeedbackEnabled ?? false);
    readonly gradingEnabled = computed(() => this.config()?.gradingFeedbackEnabled ?? false);

    /** The two toggle rows, rendered by one @for so the markup stays in a single place. */
    protected readonly features = [
        { key: 'formativeFeedbackEnabled' as const, testId: 'athena-formative-feedback', enabled: this.formativeEnabled },
        { key: 'gradingFeedbackEnabled' as const, testId: 'athena-grading-feedback', enabled: this.gradingEnabled },
    ];

    ngOnInit(): void {
        const courseId = this.course()?.id;
        if (courseId) {
            this.athenaCourseConfigService.getCourseConfig(courseId).subscribe({
                next: (config) => this.config.set(config),
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        }
    }

    /**
     * Switch one of the two Athena features and save it. The new state is shown right away and rolled back if the
     * request fails, so the toggle never claims a setting that was not stored.
     *
     * A course that has never been configured has no stored configuration, and a failed load leaves none either. Both
     * cases are treated as "both features off" rather than blocking the toggles, so the instructor can always switch a
     * feature on and find out from the alert if that could not be saved.
     *
     * @param feature the feature to switch
     * @param enabled whether the feature should be enabled
     */
    setEnabled(feature: AthenaFeature, enabled: boolean) {
        const courseId = this.course()?.id;
        const currentConfig = this.config() ?? DISABLED_CONFIG;

        if (!courseId || currentConfig[feature] === enabled) {
            return;
        }

        const newConfig = cloneWith(currentConfig, { [feature]: enabled });
        this.config.set(newConfig);

        this.athenaCourseConfigService.updateCourseConfig(courseId, newConfig).subscribe({
            next: (response) => {
                if (response.body) {
                    this.config.set(response.body);
                }
            },
            error: (error: HttpErrorResponse) => {
                this.config.set(currentConfig);
                onError(this.alertService, error);
            },
        });
    }
}
