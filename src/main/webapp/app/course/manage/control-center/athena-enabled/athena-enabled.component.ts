import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Course } from 'app/course/shared/entities/course.model';
import { AthenaFeature, createAthenaCourseConfigState } from 'app/course/manage/services/athena-course-config.state';
import { EnabledToggleComponent } from 'app/shared-ui/enabled-toggle/enabled-toggle.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiTooltipDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';

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
export class AthenaEnabledComponent {
    protected readonly faQuestionCircle = faQuestionCircle;

    course = input.required<Course>();

    /**
     * Loading, switching and rolling back are the same here as in the onboarding wizard, so both share this state. It
     * is replaced whenever the course is: the course detail page stays on screen when only its course id changes.
     */
    private readonly state = createAthenaCourseConfigState(computed(() => this.course()?.id));

    readonly config = computed(() => this.state()?.config());
    readonly formativeEnabled = computed(() => this.state()?.formativeFeedbackEnabled() ?? false);
    readonly gradingEnabled = computed(() => this.state()?.gradingFeedbackEnabled() ?? false);

    /** The two toggle rows, rendered by one @for so the markup stays in a single place. */
    protected readonly features = [
        { key: 'formativeFeedbackEnabled' as const, testId: 'athena-formative-feedback', enabled: this.formativeEnabled },
        { key: 'gradingFeedbackEnabled' as const, testId: 'athena-grading-feedback', enabled: this.gradingEnabled },
    ];

    /**
     * Switch one of the two Athena features and save it right away.
     *
     * @param feature the feature to switch
     * @param enabled whether the feature should be enabled
     */
    setEnabled(feature: AthenaFeature, enabled: boolean) {
        this.state()?.setEnabled(feature, enabled);
    }
}
