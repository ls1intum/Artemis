import { Signal, computed, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AthenaCourseConfigDTO, AthenaCourseConfigService } from 'app/course/manage/services/athena-course-config.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

/** The two independently switchable Athena feedback features of a course. */
export type AthenaFeature = 'formativeFeedbackEnabled' | 'gradingFeedbackEnabled';

/** Both features, for the places that handle each of them on its own. */
export const ATHENA_FEATURES: readonly AthenaFeature[] = ['formativeFeedbackEnabled', 'gradingFeedbackEnabled'];

/** What a course whose Athena configuration has not loaded (yet) is treated as. */
const DISABLED_CONFIG: AthenaCourseConfigDTO = { gradingFeedbackEnabled: false, formativeFeedbackEnabled: false };

/**
 * The Athena feedback configuration of one course, as the toggles that switch it show it.
 *
 * The course overview and the onboarding wizard both offer the same two switches and both save every switch right
 * away, so they share this state instead of each keeping their own. It holds three things: the configuration on
 * screen, the configuration the server last confirmed, and how often each feature has been switched.
 *
 * The last two are what keep a toggle from claiming a state that was never stored. Rolling a failed switch back to the
 * value it replaced restores whatever the previous switch put on screen, which a failure of that switch has meanwhile
 * invalidated: switching a feature on and off again with both requests failing used to leave it on. The switch count
 * decides whose answer may be shown at all, so an answer that a newer switch of the same feature has already replaced
 * is dropped rather than put back on screen.
 */
export class AthenaCourseConfigState {
    /** The configuration on screen; undefined until it is either loaded or switched. */
    readonly config = signal<AthenaCourseConfigDTO | undefined>(undefined);

    readonly formativeFeedbackEnabled: Signal<boolean> = computed(() => this.config()?.formativeFeedbackEnabled ?? false);

    readonly gradingFeedbackEnabled: Signal<boolean> = computed(() => this.config()?.gradingFeedbackEnabled ?? false);

    /** The configuration the server last confirmed; a failed switch rolls back to it. */
    private confirmed = DISABLED_CONFIG;

    /** How often each feature has been switched, so that only its latest switch writes back an answer. */
    private readonly revisions: Record<AthenaFeature, number> = { formativeFeedbackEnabled: 0, gradingFeedbackEnabled: 0 };

    constructor(
        private readonly athenaCourseConfigService: AthenaCourseConfigService,
        private readonly alertService: AlertService,
    ) {}

    /**
     * Load the stored configuration of a course.
     *
     * A feature already switched by the time this answers keeps the state that switch put on screen: the answer
     * describes the course as it was before, so applying it would undo the switch. The other feature is applied, as
     * nothing newer is known about it.
     *
     * @param courseId the id of the course
     */
    load(courseId: number): void {
        this.athenaCourseConfigService.getCourseConfig(courseId).subscribe({
            next: (loaded) => {
                for (const feature of ATHENA_FEATURES) {
                    if (this.revisions[feature] === 0) {
                        this.confirm(feature, loaded[feature]);
                        this.apply(feature, loaded[feature]);
                    }
                }
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * Switch one of the two features and save it. The new state is shown right away and rolled back to the last state
     * the server confirmed if the request fails, so the toggle never claims a setting that was not stored.
     *
     * A course that has never been configured has no stored configuration, and a failed load leaves none either. Both
     * cases count as "both features off" rather than blocking the toggles, so the instructor can always switch a
     * feature on and find out from the alert if that could not be saved.
     *
     * @param courseId the id of the course
     * @param feature the feature to switch
     * @param enabled whether the feature should be enabled
     */
    setEnabled(courseId: number, feature: AthenaFeature, enabled: boolean): void {
        if ((this.config() ?? DISABLED_CONFIG)[feature] === enabled) {
            return;
        }

        const revision = ++this.revisions[feature];
        this.apply(feature, enabled);

        // Only the switched feature is sent: restating the other one would write back whatever this client last read
        // for it, undoing a change made elsewhere in the meantime.
        this.athenaCourseConfigService.updateCourseConfig(courseId, { [feature]: enabled }).subscribe({
            next: (response) => {
                const stored = response.body?.[feature] ?? enabled;
                this.confirm(feature, stored);
                this.applyIfLatest(feature, revision, stored);
            },
            error: (error: HttpErrorResponse) => {
                this.applyIfLatest(feature, revision, this.confirmed[feature]);
                onError(this.alertService, error);
            },
        });
    }

    /**
     * Shows one feature as enabled or disabled, leaving the other one at whatever it currently is. Writing back the
     * one feature the request was about, rather than a whole snapshot, is what keeps it from undoing a switch of the
     * other feature made while it was in flight.
     *
     * @param feature the feature to show
     * @param enabled the state to show it in
     */
    private apply(feature: AthenaFeature, enabled: boolean): void {
        this.config.update((current) => cloneWith(current ?? DISABLED_CONFIG, { [feature]: enabled }));
    }

    /**
     * Shows the outcome of a switch, unless the same feature has been switched again since. That newer switch is what
     * the instructor last asked for and has already put its own state on screen, so this answer is only history.
     *
     * @param feature the feature the switch was about
     * @param revision the switch count this switch was started with
     * @param enabled the state the switch ended in
     */
    private applyIfLatest(feature: AthenaFeature, revision: number, enabled: boolean): void {
        if (this.revisions[feature] === revision) {
            this.apply(feature, enabled);
        }
    }

    /**
     * Records the state the server holds for one feature, which every failing switch of it falls back to.
     *
     * @param feature the feature the server confirmed
     * @param enabled the state it confirmed
     */
    private confirm(feature: AthenaFeature, enabled: boolean): void {
        this.confirmed = cloneWith(this.confirmed, { [feature]: enabled });
    }
}
