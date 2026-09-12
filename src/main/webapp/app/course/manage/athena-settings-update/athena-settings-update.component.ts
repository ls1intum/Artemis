import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { AthenaFeature, createAthenaCourseConfigState } from 'app/course/manage/services/athena-course-config.state';
import { CourseTitleBarTitleComponent } from 'app/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { EnabledToggleComponent } from 'app/shared-ui/enabled-toggle/enabled-toggle.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiTooltipDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCog, faQuestionCircle, faShieldHalved } from '@fortawesome/free-solid-svg-icons';
import { TabsModule } from 'primeng/tabs';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';

/**
 * Dedicated course-level Athena settings page, reached from the course-management sidebar like Iris's settings page.
 *
 * Unlike Iris's page, nothing here is edited-then-saved: the General tab shares the same auto-saving state
 * ({@link createAthenaCourseConfigState}) as the course overview toggle, so switching a feature here also updates
 * the toggle shown there and vice versa. The Admin tab shows a single read-only value; there is no form and no
 * `PendingChangesGuard` because nothing can ever be left in an unsaved state.
 */
@Component({
    selector: 'jhi-athena-settings-update',
    templateUrl: './athena-settings-update.component.html',
    imports: [
        CourseTitleBarTitleComponent,
        CourseTitleBarTitleDirective,
        TabsModule,
        MessageModule,
        EnabledToggleComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumUiTooltipDirective,
        FaIconComponent,
        SkeletonModule,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
    styles: [
        `
            :host {
                display: block;
            }

            .athena-settings-content {
                max-width: 40rem;
                margin: 0 auto;
            }

            .athena-general-panel {
                display: flex;
                flex-direction: column;
                gap: 1.25rem;
                padding-top: 1rem;
            }

            .athena-setting-label {
                display: flex;
                align-items: center;
                gap: 0.35rem;
                font-size: 0.82rem;
                color: var(--p-text-muted-color);
                margin-bottom: 0.25rem;
            }

            .athena-setting-hint {
                display: block;
                margin-top: 0.35rem;
            }

            .athena-admin-panel {
                display: flex;
                flex-direction: column;
                gap: 0.75rem;
                padding-top: 1rem;
            }
        `,
    ],
})
export class AthenaSettingsUpdateComponent {
    protected readonly faCog = faCog;
    protected readonly faShieldHalved = faShieldHalved;
    protected readonly faQuestionCircle = faQuestionCircle;

    private readonly accountService = inject(AccountService);
    private readonly route = inject(ActivatedRoute);

    readonly isAdmin = signal(this.accountService.isAdmin());
    readonly activeTab = signal('general');

    private readonly courseId = toSignal(this.route.params.pipe(map((params) => Number(params['courseId']))), { requireSync: true });

    /**
     * Loading, switching and rolling back are the same here as on the course overview toggle and the onboarding
     * wizard, so all three share this state.
     */
    private readonly state = createAthenaCourseConfigState(this.courseId);

    readonly config = computed(() => this.state()?.config());
    /** False while the first load for this course is still on its way; see {@link AthenaCourseConfigState#isLoaded}. */
    readonly isLoaded = computed(() => this.state()?.isLoaded() ?? false);
    readonly formativeEnabled = computed(() => this.state()?.formativeFeedbackEnabled() ?? false);
    readonly gradingEnabled = computed(() => this.state()?.gradingFeedbackEnabled() ?? false);
    readonly allowedFeedbackRequests = computed(() => this.state()?.allowedFeedbackRequests());

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

    setActiveTab(tab: string | number | undefined) {
        if (tab !== undefined) {
            this.activeTab.set(String(tab));
        }
    }
}
