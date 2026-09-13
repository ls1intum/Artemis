import { Injectable, Signal, computed, effect, inject, signal, untracked } from '@angular/core';
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
 *
 * An instance belongs to one course for its whole life. Use {@link createAthenaCourseConfigState} to follow a course
 * that can change while the toggles stay on screen.
 */
export class AthenaCourseConfigState {
    /** The configuration on screen; undefined until it is either loaded or switched. */
    readonly config = signal<AthenaCourseConfigDTO | undefined>(undefined);

    readonly formativeFeedbackEnabled: Signal<boolean> = computed(() => this.config()?.formativeFeedbackEnabled ?? false);

    readonly gradingFeedbackEnabled: Signal<boolean> = computed(() => this.config()?.gradingFeedbackEnabled ?? false);

    /**
     * Whether either feature is on, for the single course-overview toggle. There is no separate stored "master" flag:
     * this is derived from the two features so that a course configured before this toggle existed still shows the
     * right state.
     */
    readonly masterEnabled: Signal<boolean> = computed(() => this.formativeFeedbackEnabled() || this.gradingFeedbackEnabled());

    /**
     * The instance-wide allowed-feedback-requests cap the last load reported; undefined until a load has answered.
     * Read-only and never switched, so unlike the two features above it is set straight from the load response
     * rather than routed through {@link apply}, which only ever touches the one feature a switch was about.
     */
    readonly allowedFeedbackRequests = signal<number | undefined>(undefined);

    /**
     * Whether the first load has answered, successfully or not. False only for the brief window before the very
     * first response — including for a course a caller already has this state cached for, where it flips to true the
     * moment the state is created and never goes back. A caller shows a loading placeholder instead of the toggle
     * while this is false, rather than the "both off" {@link config} defaults to before its first answer, so a
     * course that turns out to have Athena enabled never flashes as disabled first.
     */
    readonly isLoaded = signal(false);

    /** Whether {@link load} has been started, so {@link ensureLoaded} fires it at most once per instance. */
    private loadStarted = false;

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
        private readonly courseId: number,
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
     */
    load(): void {
        this.athenaCourseConfigService.getCourseConfig(this.courseId).subscribe({
            next: (loaded) => {
                this.allowedFeedbackRequests.set(loaded.allowedFeedbackRequests);
                for (const feature of ATHENA_FEATURES) {
                    if (this.confirmed[feature] !== undefined) {
                        continue;
                    }
                    this.confirmed[feature] = loaded[feature];
                    if (this.settled[feature] === this.revisions[feature]) {
                        this.apply(feature, loaded[feature]);
                    }
                }
                this.isLoaded.set(true);
            },
            error: (error: HttpErrorResponse) => {
                // The load has still answered, with "both off" per the fallback documented on setEnabled — a caller
                // waiting on isLoaded must not wait forever just because this attempt failed.
                this.isLoaded.set(true);
                onError(this.alertService, error);
            },
        });
    }

    /**
     * Starts {@link load} the first time it is called on this instance and does nothing after that, so a state shared
     * by several callers (see {@link AthenaCourseConfigStore}) is only ever fetched once no matter how many of them
     * ask for it.
     */
    ensureLoaded(): void {
        if (this.loadStarted) {
            return;
        }
        this.loadStarted = true;
        this.load();
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
     * @param feature the feature to switch
     * @param enabled whether the feature should be enabled
     */
    setEnabled(feature: AthenaFeature, enabled: boolean): void {
        if ((this.config() ?? DISABLED_CONFIG)[feature] === enabled) {
            return;
        }

        const revision = ++this.revisions[feature];
        this.apply(feature, enabled);

        // Only the switched feature is sent: restating the other one would write back whatever this client last read
        // for it, undoing a change made elsewhere in the meantime.
        this.athenaCourseConfigService.updateCourseConfig(this.courseId, { [feature]: enabled }).subscribe({
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
     * Switches the single course-overview toggle. Since there is no stored master flag, switching it on and off is
     * defined in terms of the two features it derives from: on turns both on, off turns both off. Reading it back is
     * still the OR of the two (see {@link masterEnabled}), so a course set up from the settings page to run only one
     * of them keeps showing as enabled here — switching this toggle off then on again does turn both on, though,
     * rather than restoring that finer configuration.
     *
     * @param enabled whether Athena should be on for the course
     */
    setMasterEnabled(enabled: boolean): void {
        this.setEnabled('gradingFeedbackEnabled', enabled);
        this.setEnabled('formativeFeedbackEnabled', enabled);
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

/**
 * One {@link AthenaCourseConfigState} per course, shared by every caller of {@link createAthenaCourseConfigState}
 * for the lifetime of the app.
 *
 * The course-overview card and the settings page each mount their own instance of the component that follows a
 * course's Athena configuration, and an instructor commonly moves from one to the other for the same course. Without
 * this store each mount would start from scratch — an unanswered load, "both off" on screen — and repeat the fetch
 * the other page just made, flashing disabled before the answer no one needed to ask for again arrived. Handing out
 * the same instance for a courseId already seen makes the second page show the first page's answer immediately, with
 * no request and no flash.
 *
 * A course is never evicted once loaded: the entries are small, and the alternative — a course whose toggles were
 * open a minute ago quietly re-fetching and flashing disabled again the next time it is opened — is worse than
 * holding a few extra booleans for the rest of the session.
 */
@Injectable({ providedIn: 'root' })
export class AthenaCourseConfigStore {
    private readonly athenaCourseConfigService = inject(AthenaCourseConfigService);
    private readonly alertService = inject(AlertService);

    private readonly states = new Map<number, AthenaCourseConfigState>();

    /**
     * The Athena configuration state of the given course, creating it if this is the first time it is asked for.
     * Pure with respect to an already-cached course: called from a `computed`, it must not itself start the load
     * (see {@link createAthenaCourseConfigState}), only hand back the (possibly brand new, not-yet-loading) instance.
     *
     * @param courseId the id of the course to look up or create the state for
     * @return that course's state, the same instance every time it is asked for again
     */
    getOrCreate(courseId: number): AthenaCourseConfigState {
        let state = this.states.get(courseId);
        if (!state) {
            state = new AthenaCourseConfigState(courseId, this.athenaCourseConfigService, this.alertService);
            this.states.set(courseId, state);
        }
        return state;
    }
}

/**
 * The Athena configuration state of whichever course `courseId` currently names, shared with every other caller
 * following the same course (see {@link AthenaCourseConfigStore}) and loaded the first time any of them asks for it.
 *
 * Angular reuses a route whose only change is its `:courseId`, so the toggles can stay on screen while their course is
 * replaced by another one. Each course has a state of its own rather than one state being reset: answers still on
 * their way for the previous course land in the state that was left behind, so none of them can show up for the new
 * course, and a switch of the new course never starts from what was known about the previous one. A course replaced by
 * a copy with the same id, as the onboarding wizard does on every change, keeps its state, provided `courseId` is a
 * computed signal that only notifies when the id itself changes.
 *
 * Must be called in an injection context, such as a component field initializer.
 *
 * @param courseId the id of the course to show; undefined shows no configuration and loads nothing
 */
export function createAthenaCourseConfigState(courseId: Signal<number | undefined>): Signal<AthenaCourseConfigState | undefined> {
    const store = inject(AthenaCourseConfigStore);

    const state = computed(() => {
        const id = courseId();
        return id ? store.getOrCreate(id) : undefined;
    });

    effect(() => {
        const current = state();
        // A state already loaded, or already loading for another caller following the same course, ignores this;
        // see AthenaCourseConfigState#ensureLoaded. Outside the computed above so this effect rerunning for some
        // unrelated reason can never retrigger it.
        untracked(() => current?.ensureLoaded());
    });

    return state;
}
