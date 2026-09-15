import { Injectable, Signal, computed, effect, inject, signal, untracked } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AthenaCourseConfigDTO, AthenaCourseConfigService } from 'app/course/manage/services/athena-course-config.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';

/** The two independently switchable Athena feedback features of a course. */
export type AthenaFeature = 'formativeFeedbackEnabled' | 'gradingFeedbackEnabled';

/** Both features, for the places that handle each of them on its own. */
export const ATHENA_FEATURES: readonly AthenaFeature[] = ['formativeFeedbackEnabled', 'gradingFeedbackEnabled'];

/** What a course whose Athena configuration has not loaded (yet) is treated as. */
const DISABLED_CONFIG: AthenaCourseConfigDTO = { gradingFeedbackEnabled: false, formativeFeedbackEnabled: false, defaultFeedbackDetail: 0, defaultFeedbackFormality: 0 };

/** The two course-level feedback style defaults, on the same 1-3 scale as a student's own learner-profile preference. */
export type AthenaFeedbackStyleField = 'defaultFeedbackDetail' | 'defaultFeedbackFormality';

/** Both style defaults, for the places that handle each of them on its own. */
export const ATHENA_FEEDBACK_STYLE_FIELDS: readonly AthenaFeedbackStyleField[] = ['defaultFeedbackDetail', 'defaultFeedbackFormality'];

/** Every field {@link AthenaCourseConfigState} tracks a revision for: the two on/off features and the two style defaults. */
type AthenaConfigField = AthenaFeature | AthenaFeedbackStyleField;

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

    /** The course default for feedback detail; 0 means no course default is set. Only the settings page writes this. */
    readonly defaultFeedbackDetail: Signal<number> = computed(() => this.config()?.defaultFeedbackDetail ?? 0);

    /** The course default for feedback formality; 0 means no course default is set. Only the settings page writes this. */
    readonly defaultFeedbackFormality: Signal<number> = computed(() => this.config()?.defaultFeedbackFormality ?? 0);

    /**
     * Whether either feature is on, for the single course-overview toggle. There is no separate stored "master" flag:
     * this is derived from the two features so that a course configured before this toggle existed still shows the
     * right state.
     */
    readonly masterEnabled: Signal<boolean> = computed(() => this.formativeFeedbackEnabled() || this.gradingFeedbackEnabled());

    /**
     * The instance-wide allowed-feedback-requests cap the last load reported; undefined until a load has answered.
     * Read-only and never switched, so unlike the two features above it is set straight from the load response
     * rather than routed through {@link applyFeature}, which only ever touches the one feature a switch was about.
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

    /**
     * Whether {@link load} is in flight, so {@link ensureLoaded} does not start a second request while one is
     * outstanding — the two features and their confirmed state stay per-instance, but this flag alone must not let
     * every caller sharing this instance fire its own request. Reset once the request settles, successfully or not,
     * so the next caller that mounts and asks {@link ensureLoaded} — typically the next page the instructor opens
     * for this course, possibly long after the first page closed — starts a fresh request rather than trusting an
     * answer that another instructor may have changed since. {@link load} folds that fresh answer into whatever is
     * already on screen instead of replacing it outright, so this revalidation never causes the flash a cached
     * instance exists to avoid.
     */
    private loadStarted = false;

    /**
     * The state the server confirmed per field; a failed switch rolls back to it. A field the server has not
     * spoken about yet is missing here rather than stored as disabled/0, so that a load answering afterwards still
     * counts.
     */
    private readonly confirmed: Partial<AthenaCourseConfigDTO> = {};

    /** How often each field has been switched, so that only its latest switch writes back an answer. */
    private readonly revisions: Record<AthenaConfigField, number> = {
        formativeFeedbackEnabled: 0,
        gradingFeedbackEnabled: 0,
        defaultFeedbackDetail: 0,
        defaultFeedbackFormality: 0,
    };

    /** The latest switch of each field that has answered, so a switch still in flight can be told from a past one. */
    private readonly settled: Record<AthenaConfigField, number> = {
        formativeFeedbackEnabled: 0,
        gradingFeedbackEnabled: 0,
        defaultFeedbackDetail: 0,
        defaultFeedbackFormality: 0,
    };

    constructor(
        private readonly courseId: number,
        private readonly athenaCourseConfigService: AthenaCourseConfigService,
        private readonly alertService: AlertService,
    ) {}

    /**
     * Load the stored configuration of a course. Also how a cached instance revalidates itself on a later mount
     * (see {@link loadStarted}): each answer is merged into whatever is on screen rather than assumed to be the
     * first one, so an instructor who has had the page open for a while still picks up a change another instructor
     * made in the meantime.
     *
     * The answer describes the course as of some point during the request, not as of when it arrives, so a field
     * switched while this request was on its way must not accept it: the answer may predate that switch even though
     * it arrives after the switch's own request has already confirmed the newer value. This applies equally to the
     * two on/off features and the two feedback style defaults - a slider left dragging while the settings page
     * revalidates in the background needs the same protection a toggle does. A field only takes the answer where
     * nothing happened to it for the whole request: no switch already in flight when the request was made (a
     * snapshot of {@link revisions} and {@link settled} taken before it is sent apart), and none started before the
     * answer arrived either — as opposed to a switch that had already settled before this request was even made,
     * which is merely old news the answer is expected to already reflect.
     */
    load(): void {
        const revisionAtRequest = deepClone(this.revisions);
        const settledAtRequest = deepClone(this.settled);

        this.athenaCourseConfigService.getCourseConfig(this.courseId).subscribe({
            next: (loaded) => {
                this.allowedFeedbackRequests.set(loaded.allowedFeedbackRequests);
                for (const feature of ATHENA_FEATURES) {
                    if (this.wasIdleForWholeRequest(feature, revisionAtRequest, settledAtRequest)) {
                        this.confirmed[feature] = loaded[feature];
                        this.applyFeature(feature, loaded[feature]);
                    }
                }
                // The settings page keeps its sliders interactive while a cached mount revalidates in the background
                // (see ensureLoaded), so a style default needs the same guard as the two features above: an instructor
                // dragging one while this GET is still on its way must not have it snapped back by an answer that
                // predates that drag.
                for (const field of ATHENA_FEEDBACK_STYLE_FIELDS) {
                    if (this.wasIdleForWholeRequest(field, revisionAtRequest, settledAtRequest)) {
                        const value = loaded[field] ?? 0;
                        this.confirmed[field] = value;
                        this.applyStyle(field, value);
                    }
                }
                this.isLoaded.set(true);
                this.loadStarted = false;
            },
            error: (error: HttpErrorResponse) => {
                // The toggles still need to stop showing a loading placeholder, with "both off" per the fallback
                // documented on setEnabled — a caller waiting on isLoaded must not wait forever just because this
                // attempt failed. But nothing here is confirmed, so this must not be the last word either: clearing
                // loadStarted lets a later mount for this course retry instead of every future caller being stuck
                // with an unconfirmed "both off" for the rest of the session.
                this.isLoaded.set(true);
                this.loadStarted = false;
                onError(this.alertService, error);
            },
        });
    }

    /**
     * Starts {@link load} unless one is already in flight, so a state shared by several callers (see
     * {@link AthenaCourseConfigStore}) is fetched at most once concurrently no matter how many of them ask for it at
     * the same time. {@link loadStarted} is cleared once that request settles, whether it succeeded or failed, so a
     * later caller for the same course — typically the next page the instructor opens for it — starts a fresh
     * request rather than trusting however old the cached instance's last answer is.
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
     * cases show "both features off" rather than blocking the toggles, so the instructor can always switch a feature
     * on and find out from the alert if that could not be saved. A failure while the load is still on its way falls
     * back to "off" for the same reason; correcting that is what the load is still applied for afterwards.
     *
     * The no-op check below only skips a switch that repeats what {@link config} already shows, never one repeating
     * the "off" the feature merely displays as before anything about it is known: that display is not a confirmed
     * "off" the request would be redundant against, and the instructor pressing Disabled on a feature that looks
     * disabled only because it never loaded must still reach the server, in case the server disagrees.
     *
     * @param feature the feature to switch
     * @param enabled whether the feature should be enabled
     */
    setEnabled(feature: AthenaFeature, enabled: boolean): void {
        if (this.config()?.[feature] === enabled) {
            return;
        }

        const revision = ++this.revisions[feature];
        this.applyFeature(feature, enabled);

        // Only the switched feature is sent: restating the other one would write back whatever this client last read
        // for it, undoing a change made elsewhere in the meantime.
        this.athenaCourseConfigService.updateCourseConfig(this.courseId, { [feature]: enabled }).subscribe({
            next: (response) => {
                // The response is the complete stored configuration, not just the switched feature: merge in the
                // other feature too, unless a switch of it is still in flight and would otherwise have its own
                // optimistic value overwritten by what this request read before that switch was made.
                const body = response.body;
                for (const other of ATHENA_FEATURES) {
                    if (other === feature || body?.[other] === undefined || this.settled[other] !== this.revisions[other]) {
                        continue;
                    }
                    this.confirmed[other] = body[other];
                    this.applyFeature(other, body[other]);
                }

                const stored = body?.[feature] ?? enabled;
                this.confirmed[feature] = stored;
                this.settle(feature, revision);
                this.applyFeatureIfLatest(feature, revision, stored);
            },
            error: (error: HttpErrorResponse) => {
                this.settle(feature, revision);
                this.applyFeatureIfLatest(feature, revision, this.confirmed[feature] ?? false);
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
     * Sets one of the two course-level feedback style defaults and saves it right away, rolling back to the last
     * value the server confirmed if the request fails. Unlike {@link setEnabled} there is no other feature whose
     * write could race this one, but the same request-ordering hazards {@link setEnabled} guards against still
     * apply within a single field: a background revalidation (see {@link load}) can answer while the settings page
     * keeps its slider interactive, and dragging a slider twice before the first save answers queues a second switch
     * behind it. Both are guarded the same way, by a per-field revision that only the latest switch of that field may
     * write back.
     *
     * @param field the field to change
     * @param value the new value (1-3), or 0 to clear the course default and fall back to the student's own preference
     */
    setFeedbackStyleDefault(field: AthenaFeedbackStyleField, value: number): void {
        if ((this.config()?.[field] ?? 0) === value) {
            return;
        }

        const revision = ++this.revisions[field];
        this.applyStyle(field, value);

        this.athenaCourseConfigService.updateCourseConfig(this.courseId, { [field]: value }).subscribe({
            next: (response) => {
                const stored = response.body?.[field] ?? value;
                this.confirmed[field] = stored;
                this.settle(field, revision);
                this.applyStyleIfLatest(field, revision, stored);
            },
            error: (error: HttpErrorResponse) => {
                this.settle(field, revision);
                this.applyStyleIfLatest(field, revision, this.confirmed[field] ?? 0);
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
    private applyFeature(feature: AthenaFeature, enabled: boolean): void {
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
    private applyFeatureIfLatest(feature: AthenaFeature, revision: number, enabled: boolean): void {
        if (this.revisions[feature] === revision) {
            this.applyFeature(feature, enabled);
        }
    }

    /**
     * Shows one feedback style default, leaving everything else at whatever it currently is. See {@link applyFeature}.
     *
     * @param field the field to show
     * @param value the value to show it in
     */
    private applyStyle(field: AthenaFeedbackStyleField, value: number): void {
        this.config.update((current) => cloneWith(current ?? DISABLED_CONFIG, { [field]: value }));
    }

    /**
     * Shows the outcome of a style-default save, unless the same field has been switched again since. See
     * {@link applyFeatureIfLatest}.
     *
     * @param field the field the switch was about
     * @param revision the switch count this switch was started with
     * @param value the value the switch ended in
     */
    private applyStyleIfLatest(field: AthenaFeedbackStyleField, revision: number, value: number): void {
        if (this.revisions[field] === revision) {
            this.applyStyle(field, value);
        }
    }

    /**
     * Records that a switch has answered, so that a load arriving afterwards can tell whether the field is still
     * being switched. An answer cannot lower this: an older switch answering after a newer one leaves it where it is.
     *
     * @param field the field the switch was about
     * @param revision the switch count that switch was started with
     */
    private settle(field: AthenaConfigField, revision: number): void {
        this.settled[field] = Math.max(this.settled[field], revision);
    }

    /**
     * Whether a field had no switch in flight for the whole duration of a {@link load} request: none already
     * unanswered when the request was made, and none started before the request's answer arrived either. Only such a
     * field may take the request's answer - see {@link load}.
     *
     * @param field the field to check
     * @param revisionAtRequest a snapshot of {@link revisions} taken before the request was sent
     * @param settledAtRequest a snapshot of {@link settled} taken before the request was sent
     */
    private wasIdleForWholeRequest(field: AthenaConfigField, revisionAtRequest: Record<AthenaConfigField, number>, settledAtRequest: Record<AthenaConfigField, number>): boolean {
        return settledAtRequest[field] === revisionAtRequest[field] && this.revisions[field] === revisionAtRequest[field];
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
 * holding a few extra booleans for the rest of the session. Reusing the instance does not mean trusting its last
 * answer forever, though: {@link AthenaCourseConfigState#ensureLoaded} revalidates against the server on every mount,
 * it just shows the cached values while that request is on its way instead of a loading placeholder.
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
