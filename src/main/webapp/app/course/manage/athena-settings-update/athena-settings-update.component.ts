import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { map } from 'rxjs/operators';
import { AccountService } from 'app/core/auth/account.service';
import { AthenaFeature, AthenaFeedbackStyleField, createAthenaCourseConfigState } from 'app/course/manage/services/athena-course-config.state';
import { CourseTitleBarTitleComponent } from 'app/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCog, faShieldHalved } from '@fortawesome/free-solid-svg-icons';
import { TabsModule } from 'primeng/tabs';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';

/**
 * One clickable tick of a feedback style slider: its stored value (1-3), the i18n key for its label, and the i18n
 * key for the example feedback text shown - via {@link UnifiedFeedbackComponent}, the same widget a student sees
 * feedback in - so an instructor can see what that level actually reads like, the way Iris's support-level slider
 * shows an example conversation per level.
 */
interface FeedbackStyleTick {
    value: number;
    labelKey: string;
    exampleKey: string;
}

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
    styleUrl: './athena-settings-update.component.scss',
    imports: [
        CourseTitleBarTitleComponent,
        CourseTitleBarTitleDirective,
        TabsModule,
        MessageModule,
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        FaIconComponent,
        SkeletonModule,
        ToggleSwitchModule,
        UnifiedFeedbackComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AthenaSettingsUpdateComponent {
    protected readonly faCog = faCog;
    protected readonly faShieldHalved = faShieldHalved;

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

    /**
     * Whether either Athena feature is on. The feedback style cards only make sense once Athena actually sends
     * feedback to someone, so they stay hidden while both features are off.
     */
    readonly anyFeedbackEnabled = computed(() => this.formativeEnabled() || this.gradingEnabled());

    /** The two toggle rows, rendered by one @for so the markup stays in a single place. */
    readonly features = [
        { key: 'formativeFeedbackEnabled' as const, testId: 'athena-settings-formative-feedback', enabled: this.formativeEnabled },
        { key: 'gradingFeedbackEnabled' as const, testId: 'athena-settings-grading-feedback', enabled: this.gradingEnabled },
    ];

    /**
     * The course-level feedback style defaults, applied for a student who has not set their own feedback preference
     * (see the learner profile's feedback preferences page); 0 means no course default is set.
     */
    readonly defaultFeedbackDetail = computed(() => this.state()?.defaultFeedbackDetail() ?? 0);
    readonly defaultFeedbackFormality = computed(() => this.state()?.defaultFeedbackFormality() ?? 0);

    /**
     * Ticks for the two feedback style sliders, styled after Iris's "Level of Instructional Support" slider. Unlike
     * that slider, a value of 0 (no tick active) is a valid, meaningful state here - "no course default" - so ticks
     * are plain buttons on a custom track rather than a PrimeNG `p-slider`, which always shows its handle at some
     * position and has no way to display "unset".
     */
    protected readonly feedbackDetailTicks: readonly FeedbackStyleTick[] = [
        {
            value: 1,
            labelKey: 'artemisApp.course.athenaConfig.defaultFeedbackDetail.brief',
            exampleKey: 'artemisApp.course.athenaConfig.defaultFeedbackDetail.example.brief',
        },
        {
            value: 2,
            labelKey: 'artemisApp.course.athenaConfig.defaultFeedbackDetail.neutral',
            exampleKey: 'artemisApp.course.athenaConfig.defaultFeedbackDetail.example.neutral',
        },
        {
            value: 3,
            labelKey: 'artemisApp.course.athenaConfig.defaultFeedbackDetail.detailed',
            exampleKey: 'artemisApp.course.athenaConfig.defaultFeedbackDetail.example.detailed',
        },
    ];
    protected readonly feedbackFormalityTicks: readonly FeedbackStyleTick[] = [
        {
            value: 1,
            labelKey: 'artemisApp.course.athenaConfig.defaultFeedbackFormality.formal',
            exampleKey: 'artemisApp.course.athenaConfig.defaultFeedbackFormality.example.formal',
        },
        {
            value: 2,
            labelKey: 'artemisApp.course.athenaConfig.defaultFeedbackFormality.neutral',
            exampleKey: 'artemisApp.course.athenaConfig.defaultFeedbackFormality.example.neutral',
        },
        {
            value: 3,
            labelKey: 'artemisApp.course.athenaConfig.defaultFeedbackFormality.friendly',
            exampleKey: 'artemisApp.course.athenaConfig.defaultFeedbackFormality.example.friendly',
        },
    ];

    /**
     * The example feedback text for the currently selected tick of each slider, falling back to the neutral (2)
     * example while no course default is set (0) - purely for this preview, so the card is never empty; it does not
     * change what is actually stored.
     */
    readonly feedbackDetailExampleKey = computed(() => this.feedbackDetailTicks.find((tick) => tick.value === (this.defaultFeedbackDetail() || 2))?.exampleKey);
    readonly feedbackFormalityExampleKey = computed(() => this.feedbackFormalityTicks.find((tick) => tick.value === (this.defaultFeedbackFormality() || 2))?.exampleKey);

    /**
     * The track position, in percent, of a tick's value (1/2/3 -> 0/50/100), so the template can position ticks,
     * the fill and the handle without repeating the mapping.
     *
     * @param value the tick value (1-3) to position
     */
    protected tickPercent(value: number): number {
        return (value - 1) * 50;
    }

    /**
     * Switch one of the two Athena features and save it right away.
     *
     * @param feature the feature to switch
     * @param enabled whether the feature should be enabled
     */
    setEnabled(feature: AthenaFeature, enabled: boolean) {
        this.state()?.setEnabled(feature, enabled);
    }

    /**
     * Clicks a feedback style tick and saves it right away: clicking the value already shown clears it back to 0
     * ("no course default"), clicking any other tick sets that value - the same toggle-or-set behaviour the settings
     * page used with the segmented control this slider replaced, just driven by plain tick buttons instead.
     *
     * @param field the field the clicked tick belongs to
     * @param value the value of the clicked tick
     */
    onFeedbackStyleTickClick(field: AthenaFeedbackStyleField, value: number) {
        const current = field === 'defaultFeedbackDetail' ? this.defaultFeedbackDetail() : this.defaultFeedbackFormality();
        this.state()?.setFeedbackStyleDefault(field, current === value ? 0 : value);
    }

    setActiveTab(tab: string | number | undefined) {
        if (tab !== undefined) {
            this.activeTab.set(String(tab));
        }
    }
}
