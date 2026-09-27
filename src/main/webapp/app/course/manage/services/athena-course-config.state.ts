import { Service, Signal, computed, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { AthenaCourseConfigDTO, AthenaCourseConfigService } from 'app/course/manage/services/athena-course-config.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';

/** The two independently switchable Athena feedback features of a course. */
export type AthenaFeature = 'formativeFeedbackEnabled' | 'gradingFeedbackEnabled';

/** Both features, for the places that handle each of them on its own. */
export const ATHENA_FEATURES: readonly AthenaFeature[] = ['formativeFeedbackEnabled', 'gradingFeedbackEnabled'];

/** The two course-level feedback style defaults, applied to students who have not set their own feedback preference. */
export type AthenaFeedbackStyleField = 'defaultFeedbackDetail' | 'defaultFeedbackFormality';

/** Both style defaults, for the places that handle each of them on its own. */
export const ATHENA_FEEDBACK_STYLE_FIELDS: readonly AthenaFeedbackStyleField[] = ['defaultFeedbackDetail', 'defaultFeedbackFormality'];

/** Every field of the configuration that is saved on its own. */
type AthenaConfigField = AthenaFeature | AthenaFeedbackStyleField;

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
 * An instance belongs to one course for its whole life and is shared by every caller following that course (see
 * {@link AthenaCourseConfigStore}). Use {@link createAthenaCourseConfigState} to follow a course that can change while
 * the toggles stay on screen.
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

    /** The course default for feedback detail; 0 means no course default is set. */
    readonly defaultFeedbackDetail: Signal<number> = computed(() => this.config()?.defaultFeedbackDetail ?? 0);

    /** The course default for feedback formality; 0 means no course default is set. */
    readonly defaultFeedbackFormality: Signal<number> = computed(() => this.config()?.defaultFeedbackFormality ?? 0);

    /**
     * Whether {@link load} is in flight, so that {@link ensureLoaded} does not start a second request while one is
     * outstanding for the callers sharing this instance. Cleared once the request settles, successfully or not, so the
     * next caller that mounts for this course starts a fresh request rather than trusting an answer another instructor
     * may have changed since.
     */
    private loadStarted = false;

    /**
     * The state the server confirmed per field; a failed switch rolls back to it. A field the server has not spoken
     * about yet is missing here rather than stored as disabled or 0, so that a load answering afterwards still counts.
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
     * Load the stored configuration of a course. Also how a shared instance revalidates itself on a later mount (see
     * {@link ensureLoaded}): each answer is merged into whatever is on screen rather than assumed to be the first one,
     * so a page opened again later still picks up a change another instructor made in the meantime.
     *
     * The answer describes the course as of some point during the request, not as of when it arrives, so a field only
     * takes it where nothing happened to that field for the whole request: no switch already in flight when the
     * request was sent, and none started before the answer arrived. A switch that had settled before the request was
     * sent is old news the answer already reflects. The answer is also recorded as the confirmed state of a field the
     * server has not spoken about yet, even one still being switched, so a switch that fails rolls back to what is
     * stored rather than to "disabled".
     */
    load(): void {
        const revisionsAtRequest = deepClone(this.revisions);
        const settledAtRequest = deepClone(this.settled);

        this.athenaCourseConfigService.getCourseConfig(this.courseId).subscribe({
            next: (loaded) => {
                for (const feature of ATHENA_FEATURES) {
                    if (this.wasIdleForWholeRequest(feature, revisionsAtRequest, settledAtRequest)) {
                        this.confirmed[feature] = loaded[feature];
                        this.apply(feature, loaded[feature]);
                    } else if (this.confirmed[feature] === undefined) {
                        // The server has not spoken about this feature yet, so the answer is its first word even though
                        // a switch overlapped the request. A switch still in flight keeps what it put on screen and, if
                        // it fails, rolls back to this; one that already failed left only a fallback on screen.
                        this.confirmed[feature] = loaded[feature];
                        if (this.settled[feature] === this.revisions[feature]) {
                            this.apply(feature, loaded[feature]);
                        }
                    }
                }
                // The style defaults follow the same rule as the two features above.
                for (const field of ATHENA_FEEDBACK_STYLE_FIELDS) {
                    const value = loaded[field] ?? 0;
                    if (this.wasIdleForWholeRequest(field, revisionsAtRequest, settledAtRequest)) {
                        this.confirmed[field] = value;
                        this.applyStyle(field, value);
                    } else if (this.confirmed[field] === undefined) {
                        this.confirmed[field] = value;
                        if (this.settled[field] === this.revisions[field]) {
                            this.applyStyle(field, value);
                        }
                    }
                }
                this.loadStarted = false;
            },
            error: (error: HttpErrorResponse) => {
                // Nothing was confirmed, so a later mount for this course must be able to try again.
                this.loadStarted = false;
                onError(this.alertService, error);
            },
        });
    }

    /**
     * Starts {@link load} unless one is already in flight, so a state shared by several callers is fetched at most once
     * at a time no matter how many of them mount together.
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
     * Sets one of the two course-level feedback style defaults and saves it right away, rolling back to the last value
     * the server confirmed if the request fails. Guarded like {@link setEnabled}: clicking twice before the first save
     * answers queues a second save behind it, and only the latest one may write its answer back.
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

        // Only the changed field is sent, for the same reason as in setEnabled.
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
     * Shows one feedback style default, leaving everything else at whatever it currently is. See {@link apply}.
     *
     * @param field the field to show
     * @param value the value to show it in
     */
    private applyStyle(field: AthenaFeedbackStyleField, value: number): void {
        this.config.update((current) => cloneWith(current ?? DISABLED_CONFIG, { [field]: value }));
    }

    /**
     * Shows the outcome of a style-default save, unless the same field has been changed again since. See
     * {@link applyIfLatest}.
     *
     * @param field the field the save was about
     * @param revision the switch count this save was started with
     * @param value the value the save ended in
     */
    private applyStyleIfLatest(field: AthenaFeedbackStyleField, revision: number, value: number): void {
        if (this.revisions[field] === revision) {
            this.applyStyle(field, value);
        }
    }

    /**
     * Records that a switch has answered, so that a load arriving afterwards can tell whether the feature is still
     * being switched. An answer cannot lower this: an older switch answering after a newer one leaves it where it is.
     *
     * @param feature the feature the switch was about
     * @param revision the switch count that switch was started with
     */
    private settle(feature: AthenaConfigField, revision: number): void {
        this.settled[feature] = Math.max(this.settled[feature], revision);
    }

    /**
     * Whether a field had no switch in flight for the whole duration of a {@link load} request: none unanswered when
     * the request was sent, and none started before its answer arrived.
     *
     * @param field the field to check
     * @param revisionsAtRequest a snapshot of {@link revisions} taken before the request was sent
     * @param settledAtRequest a snapshot of {@link settled} taken before the request was sent
     */
    private wasIdleForWholeRequest(field: AthenaConfigField, revisionsAtRequest: Record<AthenaConfigField, number>, settledAtRequest: Record<AthenaConfigField, number>): boolean {
        return settledAtRequest[field] === revisionsAtRequest[field] && this.revisions[field] === revisionsAtRequest[field];
    }
}

/**
 * One {@link AthenaCourseConfigState} per course, shared by every caller of {@link createAthenaCourseConfigState}.
 *
 * The course overview card and the settings page each mount their own component for a course's Athena configuration,
 * and an instructor commonly moves from one to the other for the same course. Handing out the same instance for a
 * course already seen lets the second page show the first page's answer right away instead of starting from "both
 * off" and flashing disabled until its own request answers. Reusing the instance does not mean trusting its last
 * answer: {@link AthenaCourseConfigState#ensureLoaded} revalidates against the server on every mount.
 *
 * The entries are small, so a course is kept for as long as the same user stays logged in. When the user changes, as
 * on logout or a login as someone else in the same tab, every entry is dropped so that nothing the previous user saw
 * is shown to the next one.
 */
@Service()
export class AthenaCourseConfigStore {
    private readonly athenaCourseConfigService = inject(AthenaCourseConfigService);
    private readonly alertService = inject(AlertService);
    private readonly accountService = inject(AccountService);

    private readonly states = new Map<number, AthenaCourseConfigState>();

    private currentUserId?: number = this.accountService.userIdentity()?.id;

    constructor() {
        this.accountService
            .getAuthenticationState()
            .pipe(takeUntilDestroyed())
            .subscribe((user) => {
                if (this.currentUserId !== user?.id) {
                    this.currentUserId = user?.id;
                    this.states.clear();
                }
            });
    }

    /**
     * The Athena configuration state of the given course, created if this is the first time it is asked for. Free of
     * side effects, since it is called from a `computed`: it never starts the load itself (see
     * {@link createAthenaCourseConfigState}).
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
 * following the same course (see {@link AthenaCourseConfigStore}) and revalidated against the server on every mount.
 *
 * Angular reuses a route whose only change is its `:courseId`, so the toggles can stay on screen while their course is
 * replaced by another one. Each course therefore has a state of its own rather than one state being reset: answers
 * still on their way for the previous course land in the state that was left behind, so none of them can show up
 * for the new course, and a switch of the new course never starts from what was known about the previous one. A course
 * replaced by a copy with the same id, as the onboarding wizard does on every change, keeps its state, provided
 * `courseId` is a computed signal that only notifies when the id itself changes.
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
        // Loading writes the configuration on screen, which must not become something this effect reruns for.
        untracked(() => current?.ensureLoaded());
    });

    return state;
}
