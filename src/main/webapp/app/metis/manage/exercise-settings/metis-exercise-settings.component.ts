import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faPlus, faTrash } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import {
    TumAetUiButtonDirective,
    TumAetUiDatePickerComponent,
    TumAetUiInputDirective,
    TumAetUiInputNumberComponent,
    TumAetUiMessageComponent,
    TumAetUiSelectButtonComponent,
    TumAetUiSelectComponent,
    TumAetUiTableDirective,
    TumAetUiToggleSwitchComponent,
} from '@tumaet/ui-angular';
import dayjs from 'dayjs/esm';
import { EMPTY, Observable, Subject, catchError, concatMap, forkJoin, ignoreElements, tap } from 'rxjs';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { METIS_DEFAULT_CONFIG, METIS_SCALE_MAX, METIS_SCALE_MIN, MetisExerciseConfigDTO, MetisGroupWindowDTO, MetisTutorialGroupDTO } from 'app/metis/shared/entities/metis.model';
import { MetisService } from 'app/metis/shared/services/metis.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

/** One entry of a select or select button: a label in the current language and the number it stands for. */
interface LabelledNumberOption {
    label: string;
    value: number;
}

/** The two five-step scales the instructor tunes with a select button. */
type ScaleName = 'answerLength' | 'complexity';

/** The two time limits of the timer card. */
type SecondsField = 'minSecondsPerQuestion' | 'maxSecondsPerQuestion';

/** The two ends of a group window. */
type WindowEnd = 'windowStart' | 'windowEnd';

/** The ordered steps of the answer-length and complexity scales. */
const SCALE_STEPS: readonly number[] = Array.from({ length: METIS_SCALE_MAX - METIS_SCALE_MIN + 1 }, (_, index) => METIS_SCALE_MIN + index);
/** How many main questions a student can be asked. */
const MAIN_QUESTION_COUNTS: readonly number[] = [1, 2, 3, 4, 5];
/** The caps on follow-ups per main question. */
const FOLLOW_UP_CAPS: readonly number[] = [0, 1, 2, 3];
/** The Likert scales a rating can use. */
const LIKERT_SCALE_POINTS: readonly number[] = [5, 7];
/** Length of a newly added group window when the common window does not suggest one. */
const DEFAULT_WINDOW_LENGTH_MINUTES = 20;
/** The range the server accepts for both time limits, in seconds. */
const MIN_SECONDS_LIMIT = 30;
const MAX_SECONDS_LIMIT = 3600;
/** The longest hint for the question generator the server stores. */
const MAX_HINT_LENGTH = 2000;
/** The error key of the server's refusal to change the rating scale once a student has started the check. */
const SCALE_LOCKED_ERROR_KEY = 'metis.scaleLocked';

/** Whether a time limit lies in the range the server accepts. */
function isLimitInRange(seconds: number): boolean {
    return seconds >= MIN_SECONDS_LIMIT && seconds <= MAX_SECONDS_LIMIT;
}

/** A queued write: what to store, and the version of the form it was made in. */
interface QueuedWrite<T> {
    formVersion: number;
    value: T;
}

/**
 * Section of the programming exercise edit form that configures the Metis comprehension check: whether it runs, when
 * students may answer (for everyone and per tutorial group), how many questions are asked, the time limits and the
 * rating scale.
 *
 * The parent form's Save posts the exercise, not the Metis config, so every change is written through
 * {@link MetisService} immediately, and only while the server would accept it and once the stored state has been read.
 * The writes are queued, so they reach the server in the order they were made; a refused write brings the form back to
 * what the server stored. The queues outlive the section: the parent's Save and Cancel leave the page at once and the
 * simple mode hides the section, so closing it takes no new writes but still sends the ones it holds. The parent reads
 * {@link isFormValid} and {@link isCheckEnabled} for its status bar.
 */
@Component({
    selector: 'jhi-metis-exercise-settings',
    templateUrl: './metis-exercise-settings.component.html',
    styleUrl: './metis-exercise-settings.component.scss',
    imports: [
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiDatePickerComponent,
        TumAetUiInputDirective,
        TumAetUiInputNumberComponent,
        TumAetUiMessageComponent,
        TumAetUiSelectButtonComponent,
        TumAetUiSelectComponent,
        TumAetUiTableDirective,
        TumAetUiToggleSwitchComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MetisExerciseSettingsComponent implements OnInit {
    private readonly metisService = inject(MetisService);
    private readonly serverDateService = inject(ArtemisServerDateService);
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);

    readonly exercise = input.required<ProgrammingExercise>();
    readonly courseId = input.required<number>();

    protected readonly faPlus = faPlus;
    protected readonly faTrash = faTrash;
    protected readonly mainQuestionCounts = MAIN_QUESTION_COUNTS;
    protected readonly followUpCaps = FOLLOW_UP_CAPS;
    protected readonly minSecondsLimit = MIN_SECONDS_LIMIT;
    protected readonly maxSecondsLimit = MAX_SECONDS_LIMIT;
    protected readonly maxHintLength = MAX_HINT_LENGTH;

    private readonly currentLocale = getCurrentLocaleSignal(this.translateService);
    /** The parent shows the section in its edit mode only, so the exercise always has its id. */
    private readonly exerciseId = computed(() => this.exercise().id!);

    /** Config writes in the order they were made; each one waits for the previous one. */
    private readonly configWrites = new Subject<QueuedWrite<MetisExerciseConfigDTO>>();
    /** Group window writes in the order they were made; each one waits for the previous one. */
    private readonly groupWindowWrites = new Subject<QueuedWrite<MetisGroupWindowDTO[]>>();
    /**
     * Counts the times the form was reset to what the server stored after a refused write. A write that was queued
     * before the reset carries the refused value or an edit the reset discarded, so it is dropped instead of sent.
     */
    private formVersion = 0;

    /** The configuration as the form holds it: the server's, plus the changes made since. */
    protected readonly config = signal<MetisExerciseConfigDTO>(cloneWith(METIS_DEFAULT_CONFIG, { exerciseId: 0 }));
    /**
     * The configuration of the most recent write, or the stored one when nothing was written since it was read: what the server
     * holds once the queue has sent everything. Switching the check off falls back to it when a hidden field holds a value
     * the server would refuse.
     */
    private lastWrittenConfig = this.config();
    /**
     * Whether the config was read from the server. Until then the toggle is disabled and nothing is written: the form holds
     * placeholder defaults, not the settings of the exercise, and no exercise id to write them to.
     */
    protected readonly configLoaded = signal(false);
    /** The common answer window. Both ends are optional and bound two-way to the pickers, so they live beside the config and are merged into it on every write. */
    protected readonly windowStart = signal<dayjs.Dayjs | undefined>(undefined);
    protected readonly windowEnd = signal<dayjs.Dayjs | undefined>(undefined);
    /** The pickers keep their last valid value while the typed text is not a date yet, so that text is tracked separately. */
    protected readonly isStartTextValid = signal(true);
    protected readonly isEndTextValid = signal(true);
    protected readonly groupWindows = signal<MetisGroupWindowDTO[]>([]);
    /**
     * Whether the group windows were read from the server. Until then no row is shown and nothing is written: a write replaces
     * every window of the exercise, so one sent without the stored rows would delete the windows of the other groups.
     */
    protected readonly groupWindowsLoaded = signal(false);
    protected readonly tutorialGroups = signal<MetisTutorialGroupDTO[]>([]);
    /** The tutorial group chosen for the next window row. */
    protected readonly selectedGroupId = signal<number | undefined>(undefined);
    /** Set once the server refused a new rating scale: a student has started the check, so the scale stays as it is. */
    protected readonly scaleLocked = signal(false);

    /** Whether the check runs for this exercise; the parent's status bar shows the section as empty while it does not. */
    readonly isCheckEnabled = computed(() => this.config().enabled);

    readonly isEndBeforeStart = computed(() => {
        const windowStart = this.windowStart();
        const windowEnd = this.windowEnd();
        return !!windowStart?.isValid() && !!windowEnd?.isValid() && !windowEnd.isAfter(windowStart);
    });

    readonly isMinAboveMax = computed(() => this.config().minSecondsPerQuestion > this.config().maxSecondsPerQuestion);

    /** Whether the shortest or the longest time limit lies outside the range the server accepts. */
    protected readonly isMinSecondsOutOfRange = computed(() => !isLimitInRange(this.config().minSecondsPerQuestion));
    protected readonly isMaxSecondsOutOfRange = computed(() => !isLimitInRange(this.config().maxSecondsPerQuestion));

    protected readonly hasInvalidGroupWindow = computed(() => this.groupWindows().some((groupWindow) => this.isGroupWindowInvalid(groupWindow)));

    /** Whether the server would store the config as the form holds it; nothing else is written. */
    private readonly isConfigWritable = computed(
        () =>
            this.isStartTextValid() &&
            this.isEndTextValid() &&
            !this.isEndBeforeStart() &&
            !this.isMinAboveMax() &&
            !this.isMinSecondsOutOfRange() &&
            !this.isMaxSecondsOutOfRange() &&
            (this.config().instructorHint?.length ?? 0) <= MAX_HINT_LENGTH,
    );

    /** Read by the parent form. A disabled check hides its fields, so nothing can be wrong with them. */
    readonly isFormValid = computed(() => !this.isCheckEnabled() || (this.isConfigWritable() && !this.hasInvalidGroupWindow()));

    /** The tutorial groups without a window row: the candidates for a new row. */
    protected readonly availableGroups = computed(() => {
        const groupsWithWindow = new Set(this.groupWindows().map((groupWindow) => groupWindow.tutorialGroupId));
        return this.tutorialGroups().filter((group) => !groupsWithWindow.has(group.id));
    });

    protected readonly answerLengthOptions = computed(() => this.buildScaleOptions('answerLength'));
    protected readonly complexityOptions = computed(() => this.buildScaleOptions('complexity'));

    protected readonly likertScaleOptions = computed<LabelledNumberOption[]>(() => {
        this.currentLocale();
        return LIKERT_SCALE_POINTS.map((points) => ({ label: this.translateService.instant(`artemisApp.metis.settings.rating.points${points}`), value: points }));
    });

    /** A follow-up is asked at ratings up to the threshold, which has to leave at least the top rating without one. */
    protected readonly followUpThresholds = computed(() => Array.from({ length: this.config().likertScalePoints - 1 }, (_, index) => index + 1));

    constructor() {
        // concatMap sends one write at a time, in order; the inner catchError keeps a refused write from ending the queue.
        // takeUntilDestroyed stands in front of concatMap, so closing the section only ends the input: concatMap still
        // sends the writes it holds and then completes. Behind concatMap it would unsubscribe, which drops the waiting
        // writes and cancels the one on its way, and the parent's Save does not carry them.
        this.configWrites
            .pipe(
                takeUntilDestroyed(),
                concatMap((write) => this.sendUnlessOutdated(write, (config) => this.metisService.saveConfig(config))),
            )
            .subscribe();
        this.groupWindowWrites
            .pipe(
                takeUntilDestroyed(),
                concatMap((write) => this.sendUnlessOutdated(write, (groupWindows) => this.metisService.saveGroupWindows(this.exerciseId(), groupWindows))),
            )
            .subscribe();
    }

    /**
     * Loads the config, the group windows and the tutorial groups of the course. The section is created with the form,
     * so there is nothing to keep across lifetimes. A config or windows that cannot be read leave their part of the form
     * without writes; the global HTTP error handling says why.
     */
    ngOnInit(): void {
        const exerciseId = this.exerciseId();
        this.metisService
            .config(exerciseId)
            .pipe(
                catchError(() => EMPTY),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((config) => this.applyStoredConfig(config));
        this.metisService
            .groupWindows(exerciseId)
            .pipe(
                catchError(() => EMPTY),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((groupWindows) => this.applyStoredGroupWindows(groupWindows));
        this.metisService
            .tutorialGroups(this.courseId())
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((tutorialGroups) => this.tutorialGroups.set(tutorialGroups));
    }

    /**
     * Applies a change to the config and writes it to the service. A shrinking Likert scale pulls the follow-up
     * threshold down with it, so the threshold always leaves the top rating without a follow-up.
     * @param changes the fields that changed
     */
    protected updateConfig(changes: Partial<MetisExerciseConfigDTO>): void {
        const config = cloneWith(this.config(), changes);
        if (config.followUpThreshold >= config.likertScalePoints) {
            config.followUpThreshold = config.likertScalePoints - 1;
        }
        this.config.set(config);
        this.persistConfig();
    }

    /**
     * Writes the config, with the current answer window, to the server. The parent's Save posts the exercise and
     * knows nothing of the Metis config, which is why every change is persisted right away instead of on Save. A config
     * the server would refuse (a window that ends before it starts, limits out of order or out of range, a text that is
     * not a date yet, a hint that is too long) stays in the form only; the next valid change writes it.
     *
     * Switching the check off is the exception: it has to reach the server even then, or the check would keep running
     * while the form shows it off and no longer blocks Save. The hidden fields therefore go back to the most recent
     * written values, which the server accepts, and that config is written switched off.
     */
    protected persistConfig(): void {
        if (!this.configLoaded()) {
            return;
        }
        this.config.set(cloneWith(this.config(), { windowStart: this.windowStart(), windowEnd: this.windowEnd() }));
        if (!this.config().enabled && !this.isConfigWritable()) {
            // The pickers leave the form with the cards, so text typed into them no longer counts; new pickers report their own.
            this.isStartTextValid.set(true);
            this.isEndTextValid.set(true);
            this.showConfig(cloneWith(this.lastWrittenConfig, { enabled: false }));
        }
        if (this.isConfigWritable()) {
            const config = this.config();
            this.lastWrittenConfig = config;
            this.configWrites.next({ formVersion: this.formVersion, value: config });
        }
    }

    /**
     * Takes a time limit from one of the number fields. A cleared field is ignored: the limits are required, so the
     * config keeps its previous value until a number is typed again.
     * @param field which of the two limits changed
     * @param seconds the typed value, or undefined while the field is empty
     */
    protected onSecondsChange(field: SecondsField, seconds: number | undefined): void {
        if (seconds === undefined) {
            return;
        }
        this.updateConfig(field === 'minSecondsPerQuestion' ? { minSecondsPerQuestion: seconds } : { maxSecondsPerQuestion: seconds });
    }

    /**
     * Takes the hint for the question generator into the form while it is typed, so its length is checked at once; an
     * emptied field clears it. It is written when the field is left (`change` calls {@link persistConfig}), not per
     * keystroke: every write also moves the checks of the exercise to the review setting, and a queue of keystroke
     * writes would still be sending long after the form closed.
     * @param event the input event of the textarea
     */
    protected onHintInput(event: Event): void {
        const text = (event.target as HTMLTextAreaElement).value;
        this.config.set(cloneWith(this.config(), { instructorHint: text || undefined }));
    }

    /**
     * The title of a tutorial group, for a window row.
     * @param tutorialGroupId the id of the group
     */
    protected groupTitle(tutorialGroupId: number): string {
        return this.tutorialGroups().find((group) => group.id === tutorialGroupId)?.title ?? `#${tutorialGroupId}`;
    }

    /**
     * Whether a group window ends at or before its start.
     * @param groupWindow the window row
     */
    protected isGroupWindowInvalid(groupWindow: MetisGroupWindowDTO): boolean {
        return !groupWindow.windowEnd.isAfter(groupWindow.windowStart);
    }

    /**
     * Adds a window row for the selected tutorial group. It starts with the common window, else at the due date, else
     * now, and ends with the common window when that comes later, else after the default length.
     */
    protected addGroupWindow(): void {
        const tutorialGroupId = this.selectedGroupId();
        if (tutorialGroupId === undefined) {
            return;
        }
        const windowStart = this.windowStart() ?? this.exercise().dueDate ?? this.serverDateService.now();
        const commonEnd = this.windowEnd();
        const windowEnd = commonEnd?.isAfter(windowStart) ? commonEnd : windowStart.add(DEFAULT_WINDOW_LENGTH_MINUTES, 'minute');
        this.groupWindows.update((groupWindows) => [...groupWindows, { exerciseId: this.exerciseId(), tutorialGroupId, windowStart, windowEnd }]);
        this.selectedGroupId.set(undefined);
        this.persistGroupWindows();
    }

    /**
     * Removes a window row; the group falls back to the common window.
     * @param groupWindow the row to remove
     */
    protected removeGroupWindow(groupWindow: MetisGroupWindowDTO): void {
        this.groupWindows.update((groupWindows) => groupWindows.filter((candidate) => candidate !== groupWindow));
        this.persistGroupWindows();
    }

    /**
     * Moves one end of a group window. A cleared picker is ignored: a group window needs both ends, and the previous
     * date stays until a new one is typed.
     * @param groupWindow the row to change
     * @param field which end moved
     * @param date the new date, or undefined when the picker was cleared
     */
    protected updateGroupWindow(groupWindow: MetisGroupWindowDTO, field: WindowEnd, date: dayjs.Dayjs | undefined): void {
        if (!date) {
            return;
        }
        const changes: Partial<Pick<MetisGroupWindowDTO, WindowEnd>> = field === 'windowStart' ? { windowStart: date } : { windowEnd: date };
        this.groupWindows.update((groupWindows) => groupWindows.map((candidate) => (candidate === groupWindow ? cloneWith(candidate, changes) : candidate)));
        this.persistGroupWindows();
    }

    /**
     * Writes the group windows to the server, for the same reason {@link persistConfig} writes the config right away, and
     * only once the stored windows have been read and while every window ends after it starts.
     */
    private persistGroupWindows(): void {
        if (this.groupWindowsLoaded() && !this.hasInvalidGroupWindow()) {
            this.groupWindowWrites.next({ formVersion: this.formVersion, value: this.groupWindows() });
        }
    }

    /** Puts a config the server stored into the form; from now on the form writes its changes. */
    private applyStoredConfig(config: MetisExerciseConfigDTO): void {
        this.showConfig(config);
        this.lastWrittenConfig = config;
        this.configLoaded.set(true);
    }

    /** Puts the group windows the server stored into the form; from now on the form shows the rows and writes their changes. */
    private applyStoredGroupWindows(groupWindows: MetisGroupWindowDTO[]): void {
        this.groupWindows.set(groupWindows);
        this.groupWindowsLoaded.set(true);
    }

    /** Shows a config in the form, with its window in the two pickers. */
    private showConfig(config: MetisExerciseConfigDTO): void {
        this.config.set(config);
        this.windowStart.set(config.windowStart);
        this.windowEnd.set(config.windowEnd);
    }

    /**
     * Sends a queued write, unless the form was reset to the server's state after it was queued.
     * @param write the queued write
     * @param save the request that stores its value
     */
    private sendUnlessOutdated<T>(write: QueuedWrite<T>, save: (value: T) => Observable<T>): Observable<T> {
        if (write.formVersion !== this.formVersion) {
            return EMPTY;
        }
        return save(write.value).pipe(catchError((error: unknown) => this.recoverFromRefusedWrite(error)));
    }

    /**
     * Handles a write the server refused. The global HTTP error handling already shows the server's message; here the
     * form is brought back to what the server stored, so the refused value is neither shown nor sent again, and the
     * writes queued meanwhile are dropped. A refused scale change also locks the scale. The queue waits for the stored
     * state before it goes on.
     * @param error the error response of the refused write
     */
    private recoverFromRefusedWrite(error: unknown): Observable<never> {
        if (error instanceof HttpErrorResponse && error.error?.errorKey === SCALE_LOCKED_ERROR_KEY) {
            this.scaleLocked.set(true);
        }
        const exerciseId = this.exerciseId();
        return forkJoin([this.metisService.config(exerciseId), this.metisService.groupWindows(exerciseId)]).pipe(
            tap(([config, groupWindows]) => {
                this.applyStoredConfig(config);
                this.applyStoredGroupWindows(groupWindows);
                this.formVersion += 1;
            }),
            ignoreElements(),
            catchError(() => EMPTY),
        );
    }

    /**
     * The five steps of a scale with their labels, rebuilt when the language changes, as the lecture editor builds its
     * creation-mode options.
     * @param scale the scale whose step labels to look up
     */
    private buildScaleOptions(scale: ScaleName): LabelledNumberOption[] {
        this.currentLocale();
        return SCALE_STEPS.map((step) => ({ label: this.translateService.instant(`artemisApp.metis.settings.${scale}.${step}`), value: step }));
    }
}
