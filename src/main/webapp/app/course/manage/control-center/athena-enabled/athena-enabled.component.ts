import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { createAthenaCourseConfigState } from 'app/course/manage/services/athena-course-config.state';
import { EnabledToggleComponent } from 'app/shared-ui/enabled-toggle/enabled-toggle.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowRight } from '@fortawesome/free-solid-svg-icons';
import { SkeletonModule } from 'primeng/skeleton';

/**
 * The single course-level Athena toggle shown on the course overview, next to the Iris toggle. Each change is saved
 * immediately. Mirrors {@link IrisEnabledComponent}: one switch plus a link into the settings page for anything finer.
 */
@Component({
    selector: 'jhi-athena-enabled',
    templateUrl: './athena-enabled.component.html',
    imports: [EnabledToggleComponent, TranslateDirective, ArtemisTranslatePipe, RouterLink, FaIconComponent, SkeletonModule],
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

            .athena-configure-link {
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 0.4rem;
                padding: 0.5rem 1rem;
                border: 1px solid var(--p-content-border-color);
                border-radius: 0.625rem;
                background: var(--overview-card-nested-bg, var(--p-content-background));
                color: var(--p-text-color);
                font-weight: 500;
                font-size: 0.88rem;
                text-decoration: none;
                transition: all 0.2s ease;

                &:hover {
                    background: var(--p-content-hover-background);
                    border-color: var(--p-content-border-color);
                    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
                }
            }
        `,
    ],
})
export class AthenaEnabledComponent {
    protected readonly faArrowRight = faArrowRight;

    course = input.required<Course>();

    /**
     * Loading, switching and rolling back are the same here as on the settings page and in the onboarding wizard, so
     * all three share this state. It is replaced whenever the course is: the course detail page stays on screen when
     * only its course id changes.
     */
    private readonly state = createAthenaCourseConfigState(computed(() => this.course()?.id));

    readonly masterEnabled = computed(() => this.state()?.masterEnabled() ?? false);

    /** False while the first load for this course is still on its way; see {@link AthenaCourseConfigState#isLoaded}. */
    readonly isLoaded = computed(() => this.state()?.isLoaded() ?? false);

    /**
     * Route to the settings page. Computed rather than a method, because `[routerLink]="settingsRoute()"` is evaluated
     * on every change-detection pass and a fresh array each pass makes RouterLink re-process the link every time.
     */
    readonly settingsRoute = computed(() => ['/course-management', String(this.course()?.id), 'athena-settings']);

    /**
     * Switch the course-overview toggle and save it right away.
     *
     * @param enabled whether Athena should be on for the course
     */
    setEnabled(enabled: boolean) {
        this.state()?.setMasterEnabled(enabled);
    }
}
