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
 * away, so they share this state instead of each keeping their own. It holds three things per feature: the state on
 * screen, the state the server has confirmed, if it ever has, and how far the switches of that feature have got.
 *
 * Together they keep a toggle from claiming a state that was never stored. Rolling a failed switch back to the state it
 * replaced restores whatever the switch before it put on screen, which a failure of that switch has meanwhile
 * invalidated: switching a feature on and off again with both requests failing used to leave it on. Rolling back to
 * what the server confirmed instead needs "confirmed disabled" and "never heard from the server" to be different
 * things, so a feature the server has said nothing about has no confirmed state at all and a load answering later
 * still counts as that first word. The switch count decides whose answer may be shown, so an answer that a newer
 * switch of the same feature has already replaced is dropped rather than put back on screen.
 */
export class AthenaCourseConfigState {
    /** The configuration on screen; undefined until it is either loaded or switched. */
    readonly config = signal<AthenaCourseConfigDTO | undefined>(undefined);

    readonly formativeFeedbackEnabled: Signal<boolean> = computed(() => this.config()?.formativeFeedbackEnabled ?? false);

    readonly gradingFeedbackEnabled: Signal<boolean> = computed(() => this.config()?.gradingFeedbackEnabled ?? false);

    /**
     * The state the server confirmed per feature; a failed switch rolls back to it. A feature the server has not
     * spoken about yet is missing here rather than stored as disabled, so that a load answering afterwards still
     * counts.
     */
    private readonly confirmed: Partial<Record<AthenaFeature, boolean>> = {};

    /** How often each feature has been switched, so that only its latest switch writes back an answer. */
    private readonly revisions: Record<AthenaFeature, number> = { formativeFeedbackEnabled: 0, gradingFeedbackEnabled: 0 };

    /** The latest switch of each feature that has answered, so a switch still in flight can be told from a past one. */
    private readonly settled: Record<AthenaFeature, number> = { formativeFeedbackEnabled: 0, gradingFeedbackEnabled: 0 };

    constructor(
        private readonly athenaCourseConfigService: AthenaCourseConfigService,
        private readonly alertService: AlertService,
    ) {}

    /**
     * Load the stored configuration of a course.
     *
     * The answer describes the course as it was before anything was switched, so it counts per feature and only for a
     * feature no save has answered for: a save knows the newer state. Where it counts it is shown, unless a switch of
     * that feature is still in flight, because what that switch put on screen is what the instructor last asked for.
     * It is recorded as the confirmed state either way, so a switch that then fails rolls back to what is stored
     * rather than to "disabled".
     *
     * @param courseId the id of the course
     */
    load(courseId: number): void {
        this.athenaCourseConfigService.getCourseConfig(courseId).subscribe({
            next: (loaded) => {
                for (const feature of ATHENA_FEATURES) {
                    if (this.confirmed[feature] !== undefined) {
                        continue;
                    }
                    this.confirmed[feature] = loaded[feature];
                    if (this.settled[feature] === this.revisions[feature]) {
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
     * feature on and find out from the alert if that could not be saved. A failure while the load is still on its way
     * falls back to "off" for the same reason; correcting that is what the load is still applied for afterwards.
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
                this.confirmed[feature] = stored;
                this.settle(feature, revision);
                this.applyIfLatest(feature, revision, stored);
            },
            error: (error: HttpErrorResponse) => {
                this.settle(feature, revision);
                this.applyIfLatest(feature, revision, this.confirmed[feature] ?? false);
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
     * Records that a switch has answered, so that a load arriving afterwards can tell whether the feature is still
     * being switched. An answer cannot lower this: an older switch answering after a newer one leaves it where it is.
     *
     * @param feature the feature the switch was about
     * @param revision the switch count that switch was started with
     */
    private settle(feature: AthenaFeature, revision: number): void {
        this.settled[feature] = Math.max(this.settled[feature], revision);
    }
}
