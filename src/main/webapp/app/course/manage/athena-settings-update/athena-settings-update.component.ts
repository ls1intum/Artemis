import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { EMPTY } from 'rxjs';
import { map } from 'rxjs/operators';
import { FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { AthenaFeature, AthenaFeedbackStyleField, createAthenaCourseConfigState } from 'app/course/manage/services/athena-course-config.state';
import { CourseTitleBarTitleComponent } from 'app/course/shared/course-title-bar-title/course-title-bar-title.component';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumAetUiCardComponent, TumAetUiToggleSwitchComponent } from '@tumaet/ui-angular';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { TextResultComponent } from 'app/text/overview/text-result/text-result.component';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ATHENA_FEEDBACK_STYLE_EXAMPLES } from './athena-feedback-style-examples';

const MIN_LEVEL = 1;
const NEUTRAL_LEVEL = 2;
const MAX_LEVEL = 3;

/** The level each key moves a feedback style slider to, from the level it shows. */
const FEEDBACK_STYLE_KEY_STEPS: Readonly<Record<string, (level: number) => number>> = {
    ArrowLeft: (level) => level - 1,
    ArrowDown: (level) => level - 1,
    ArrowRight: (level) => level + 1,
    ArrowUp: (level) => level + 1,
    Home: () => MIN_LEVEL,
    End: () => MAX_LEVEL,
};

/** One clickable tick of a feedback style slider: its stored value (1-3) and the i18n key for its label. */
interface FeedbackStyleTick {
    value: number;
    labelKey: string;
}

/**
 * Dedicated course-level Athena settings page, reached from the course-management sidebar like Iris's settings page.
 *
 * Unlike Iris's page, nothing here is edited-then-saved: the switches share the same auto-saving state
 * ({@link createAthenaCourseConfigState}) as the course overview toggle, and the feedback style defaults save on
 * click as well, so there is no form and no `PendingChangesGuard` because nothing can ever be left in an unsaved state.
 */
@Component({
    selector: 'jhi-athena-settings-update',
    templateUrl: './athena-settings-update.component.html',
    styleUrl: './athena-settings-update.component.scss',
    host: { class: 'block' },
    imports: [
        CourseTitleBarTitleComponent,
        CourseTitleBarTitleDirective,
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumAetUiCardComponent,
        TumAetUiToggleSwitchComponent,
        TextResultComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AthenaSettingsUpdateComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly translateService = inject(TranslateService);

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
        { key: 'gradingFeedbackEnabled' as const, testId: 'athena-settings-grading-feedback', enabled: this.gradingEnabled },
        { key: 'formativeFeedbackEnabled' as const, testId: 'athena-settings-formative-feedback', enabled: this.formativeEnabled },
    ];

    /**
     * The course-level feedback style defaults, applied for a student who has not set their own feedback preference
     * (see the learner profile's feedback preferences page); 0 means no course default is set.
     */
    readonly defaultFeedbackDetail = computed(() => this.state()?.defaultFeedbackDetail() ?? 0);
    readonly defaultFeedbackFormality = computed(() => this.state()?.defaultFeedbackFormality() ?? 0);

    /**
     * The level each slider shows (1-3). A course without a default (0) shows as neutral, because that is what its
     * students get: the course default then falls back to a student's untouched learner profile, which is neutral too.
     * The stored 0 stays until the instructor picks a level, so such a course keeps following the built-in default.
     */
    readonly feedbackDetailLevel = computed(() => this.defaultFeedbackDetail() || NEUTRAL_LEVEL);
    readonly feedbackFormalityLevel = computed(() => this.defaultFeedbackFormality() || NEUTRAL_LEVEL);

    /** Ticks for the two feedback style sliders, styled after Iris's "Level of Instructional Support" slider. */
    protected readonly feedbackDetailTicks: readonly FeedbackStyleTick[] = [
        { value: 1, labelKey: 'artemisApp.course.athenaConfig.defaultFeedbackDetail.brief' },
        { value: 2, labelKey: 'artemisApp.course.athenaConfig.defaultFeedbackDetail.neutral' },
        { value: 3, labelKey: 'artemisApp.course.athenaConfig.defaultFeedbackDetail.detailed' },
    ];
    protected readonly feedbackFormalityTicks: readonly FeedbackStyleTick[] = [
        { value: 1, labelKey: 'artemisApp.course.athenaConfig.defaultFeedbackFormality.formal' },
        { value: 2, labelKey: 'artemisApp.course.athenaConfig.defaultFeedbackFormality.neutral' },
        { value: 3, labelKey: 'artemisApp.course.athenaConfig.defaultFeedbackFormality.friendly' },
    ];

    /** Changes whenever the language does, so the examples below are translated again. */
    private readonly languageChange = toSignal(this.translateService.onLangChange ?? EMPTY);

    /** The example for the level each slider shows. */
    readonly feedbackDetailExample = computed(() => this.buildExampleResult('defaultFeedbackDetail', this.feedbackDetailLevel()));
    readonly feedbackFormalityExample = computed(() => this.buildExampleResult('defaultFeedbackFormality', this.feedbackFormalityLevel()));

    /** The two sliders of the feedback style section, rendered by one @for so the markup stays in a single place. */
    protected readonly feedbackStyles = [
        {
            field: 'defaultFeedbackDetail' as const,
            testId: 'default-feedback-detail',
            level: this.feedbackDetailLevel,
            ticks: this.feedbackDetailTicks,
            example: this.feedbackDetailExample,
        },
        {
            field: 'defaultFeedbackFormality' as const,
            testId: 'default-feedback-formality',
            level: this.feedbackFormalityLevel,
            ticks: this.feedbackFormalityTicks,
            example: this.feedbackFormalityExample,
        },
    ];

    /**
     * The track position, in percent, of a level (1/2/3 -> 0/50/100), so the template can position ticks, the fill
     * and the handle without repeating the mapping.
     *
     * @param level the level to position
     */
    protected tickPercent(level: number): number {
        return (level - 1) * 50;
    }

    /**
     * Builds the example shown below a slider the way a student sees Athena's feedback on a text exercise: the sample
     * submission with the feedback item Athena returned for that level attached to its sentence (see
     * {@link ATHENA_FEEDBACK_STYLE_EXAMPLES}). The title and description go where a student's result puts Athena's,
     * the title as the feedback text and the description as its detail text (see `TextExerciseFeedbackService`).
     *
     * @param field the slider
     * @param level the level the slider shows
     */
    private buildExampleResult(field: AthenaFeedbackStyleField, level: number): Result {
        this.languageChange();
        const examples = ATHENA_FEEDBACK_STYLE_EXAMPLES[this.translateService.getCurrentLang() ?? 'en'] ?? ATHENA_FEEDBACK_STYLE_EXAMPLES['en'];
        const submission: TextSubmission = { text: examples.submission };
        const item = examples[field][level];
        return {
            submission,
            feedbacks: [
                {
                    text: item.title,
                    detailText: item.description,
                    reference: item.reference,
                    credits: item.credits,
                    type: FeedbackType.AUTOMATIC,
                },
            ],
        };
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
     * Selects a feedback style level and saves it right away. The level the slider already shows is left alone, so
     * a course without a default does not get one just because the neutral tick was clicked.
     *
     * @param field the slider
     * @param level the level to select (1-3)
     */
    selectFeedbackStyleLevel(field: AthenaFeedbackStyleField, level: number) {
        const shown = field === 'defaultFeedbackDetail' ? this.feedbackDetailLevel() : this.feedbackFormalityLevel();
        if (level !== shown) {
            this.state()?.setFeedbackStyleDefault(field, level);
        }
    }

    /**
     * Moves a slider with the keyboard, as the WAI-ARIA slider pattern describes: the arrow keys step one level, Home
     * and End jump to the ends. Other keys are left to the browser.
     *
     * @param field the slider
     * @param event the key press on the slider
     */
    onFeedbackStyleKeydown(field: AthenaFeedbackStyleField, event: KeyboardEvent) {
        const shown = field === 'defaultFeedbackDetail' ? this.feedbackDetailLevel() : this.feedbackFormalityLevel();
        const target = FEEDBACK_STYLE_KEY_STEPS[event.key]?.(shown);
        if (target === undefined) {
            return;
        }
        event.preventDefault();
        this.selectFeedbackStyleLevel(field, Math.min(MAX_LEVEL, Math.max(MIN_LEVEL, target)));
    }
}
