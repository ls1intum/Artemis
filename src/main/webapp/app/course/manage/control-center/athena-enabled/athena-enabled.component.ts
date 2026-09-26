import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Course } from 'app/course/shared/entities/course.model';
import { createAthenaCourseConfigState } from 'app/course/manage/services/athena-course-config.state';
import { EnabledToggleComponent } from 'app/shared-ui/enabled-toggle/enabled-toggle.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { faArrowRight } from '@fortawesome/free-solid-svg-icons';

/**
 * The single course-level Athena toggle shown on the course overview, next to the Iris toggle. Each change is saved
 * immediately. Mirrors {@link IrisEnabledComponent}: one switch plus a link into the settings page for anything finer.
 */
@Component({
    selector: 'jhi-athena-enabled',
    templateUrl: './athena-enabled.component.html',
    imports: [EnabledToggleComponent, TranslateDirective, ArtemisTranslatePipe, RouterLink, FaIconComponent, TumAetUiButtonDirective],
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
