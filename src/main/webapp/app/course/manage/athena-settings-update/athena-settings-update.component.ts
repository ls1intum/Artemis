import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { map } from 'rxjs/operators';
import { AthenaFeature, createAthenaCourseConfigState } from 'app/course/manage/services/athena-course-config.state';
import { CourseTitleBarTitleComponent } from 'app/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumAetUiToggleSwitchComponent } from '@tumaet/ui-angular';

/**
 * Dedicated course-level Athena settings page, reached from the course-management sidebar like Iris's settings page.
 *
 * Unlike Iris's page, nothing here is edited-then-saved: the switches share the same auto-saving state
 * ({@link createAthenaCourseConfigState}) as the course overview toggle, so there is no form and no
 * `PendingChangesGuard` because nothing can ever be left in an unsaved state.
 */
@Component({
    selector: 'jhi-athena-settings-update',
    templateUrl: './athena-settings-update.component.html',
    host: { class: 'block' },
    imports: [CourseTitleBarTitleComponent, CourseTitleBarTitleDirective, FormsModule, TranslateDirective, ArtemisTranslatePipe, TumAetUiToggleSwitchComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AthenaSettingsUpdateComponent {
    private readonly route = inject(ActivatedRoute);

    private readonly courseId = toSignal(this.route.params.pipe(map((params) => Number(params['courseId']))), { requireSync: true });

    /**
     * Loading, switching and rolling back are the same here as on the course overview toggle and the onboarding
     * wizard, so all three share this state.
     */
    private readonly state = createAthenaCourseConfigState(this.courseId);

    readonly formativeEnabled = computed(() => this.state()?.formativeFeedbackEnabled() ?? false);
    readonly gradingEnabled = computed(() => this.state()?.gradingFeedbackEnabled() ?? false);

    /** The two toggle rows, rendered by one @for so the markup stays in a single place. */
    readonly features = [
        { key: 'formativeFeedbackEnabled' as const, testId: 'athena-settings-formative-feedback', enabled: this.formativeEnabled },
        { key: 'gradingFeedbackEnabled' as const, testId: 'athena-settings-grading-feedback', enabled: this.gradingEnabled },
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
