import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
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
import { EMPTY, Observable, catchError, ignoreElements, tap } from 'rxjs';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { METIS_DEFAULT_CONFIG, METIS_SCALE_MAX, METIS_SCALE_MIN, MetisExerciseConfigDTO, MetisGroupWindowDTO, MetisTutorialGroupDTO } from 'app/metis/shared/entities/metis.model';
import { MetisService } from 'app/metis/shared/services/metis.service';
import { MetisSettingsQueue } from 'app/metis/manage/exercise-settings/metis-settings-queue.service';
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

/** The two parts of the settings, which are stored by separate requests. */
type SettingsPart = 'config' | 'groupWindows';

/** Names the picker of one end of a group window, for the tracking of its text. */
function textKey(groupWindow: MetisGroupWindowDTO, end: WindowEnd): string {
    return `${groupWindow.tutorialGroupId}:${end}`;
}

/** What a write stores: the config, or every group window of the exercise. */
type SettingsWrite = { kind: 'config'; value: MetisExerciseConfigDTO } | { kind: 'groupWindows'; value: MetisGroupWindowDTO[] };

/** A queued write: what it stores, the exercise it stores for, and the version of its part of the settings of that exercise it was made in. */
type QueuedWrite = SettingsWrite & { exerciseId: number; version: number };

/**
 * Section of the programming exercise edit form that configures the Metis comprehension check: whether it runs, when
 * students may answer (for everyone and per tutorial group), how many questions are asked, the time limits and the
 * rating scale.
 *
 * The parent form's Save posts the exercise, not the Metis config, so every change is written through
 * {@link MetisService} immediately, and only while the server would accept it and once the stored state has been read.
 * The reads and writes of the config and of the group windows of every settings section go through one queue of the
 * application ({@link MetisSettingsQueue}), so they reach the server in the order they were made; a refused write brings
 * its part of the form, the config or the group windows, back to what the server stored. The queue outlives the section:
 * the parent's Save and Cancel leave the page at once and the simple mode hides the section, so closing it takes no new
 * writes but still sends the ones it holds, and a section opened again reads the stored state only after them. The edit page
 * is reused when its route moves to another exercise and then hands that exercise to the section, which starts over for it
 * the same way: the writes queued for the exercise before still go to that one, and nothing of it stays in the form. The
 * parent reads {@link isFormValid} and {@link isCheckEnabled} for its status bar.
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
    private readonly settingsQueue = inject(MetisSettingsQueue);
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

    /** Whether the section has closed; a closed section takes no new writes, while the queue still sends those it queued before. */
    private closed = false;
    /** The exercise whose settings the form holds, and the count of the times the form started over for an exercise. */
    private formExerciseId?: number;
    private session = 0;
    /**
     * Counts per exercise and part of the form the times that part was reset to what the server stored after a refused
     * write of it. A write that was queued before the reset carries the refused value or an edit the reset discarded, so it
     * is dropped instead of sent; the writes of the other part, which the reset left alone, still go out.
     */
    private readonly versions = new Map<number, Record<SettingsPart, number>>();

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
    /** The pickers of group windows whose typed text is not a date yet, by tutorial group and end; they keep their last valid value meanwhile. */
    private readonly invalidGroupWindowTexts = signal<ReadonlySet<string>>(new Set());
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
    /** Whether a group window cannot be written: it ends at or before its start, or one of its pickers holds text that is not a date. */
    private readonly hasUnwritableGroupWindow = computed(
        () =>
            this.hasInvalidGroupWindow() ||
            this.groupWindows().some((groupWindow) => (['windowStart', 'windowEnd'] as const).some((end) => this.invalidGroupWindowTexts().has(textKey(groupWindow, end)))),
    );

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
    readonly isFormValid = computed(() => !this.isCheckEnabled() || (this.isConfigWritable() && !this.hasUnwritableGroupWindow()));

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
        this.destroyRef.onDestroy(() => (this.closed = true));
        // The edit page hands over another exercise when its route moves on without leaving it
        effect(() => {
            const exerciseId = this.exerciseId();
            untracked(() => {
                if (this.formExerciseId !== undefined && exerciseId !== this.formExerciseId) {
                    this.open(exerciseId);
                }
            });
        });
    }

    ngOnInit(): void {
        this.open(this.exerciseId());
    }

    /**
     * Loads the config, the group windows and the tutorial groups of the course for an exercise. The section reads the stored
     * state anew each time it is created or handed another exercise, after the writes that a section closed before, or the form
     * of the exercise before, still has queued. A config or windows that cannot be read leave their part of the form without
     * writes; the global HTTP error handling says why.
     * @param exerciseId the exercise whose settings the form holds from now on
     */
    private open(exerciseId: number): void {
        if (this.formExerciseId !== undefined) {
            // Nothing of the exercise before stays in the form, and nothing is written until the state of this one is read
            this.configLoaded.set(false);
            this.groupWindowsLoaded.set(false);
            this.showConfig(cloneWith(METIS_DEFAULT_CONFIG, { exerciseId: 0 }));
            this.lastWrittenConfig = this.config();
            this.groupWindows.set([]);
            this.invalidGroupWindowTexts.set(new Set());
            this.isStartTextValid.set(true);
            this.isEndTextValid.set(true);
            this.selectedGroupId.set(undefined);
            this.scaleLocked.set(false);
            this.tutorialGroups.set([]);
        }
        this.formExerciseId = exerciseId;
        const session = ++this.session;
        const isCurrent = () => !this.closed && session === this.session;
        // Queued like the writes: a section closed a moment ago may still be writing, and this one reads what they stored
        this.settingsQueue.enqueue(() =>
            isCurrent()
                ? this.metisService.config(exerciseId).pipe(
                      tap((config) => {
                          if (isCurrent()) {
                              this.applyStoredConfig(config);
                          }
                      }),
                      catchError(() => EMPTY),
                  )
                : EMPTY,
        );
        this.settingsQueue.enqueue(() =>
            isCurrent()
                ? this.metisService.groupWindows(exerciseId).pipe(
                      tap((groupWindows) => {
                          if (isCurrent()) {
                              this.applyStoredGroupWindows(groupWindows);
                          }
                      }),
                      catchError(() => EMPTY),
                  )
                : EMPTY,
        );
        this.metisService
            .tutorialGroups(this.courseId())
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((tutorialGroups) => {
                if (session === this.session) {
                    this.tutorialGroups.set(tutorialGroups);
                }
            });
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
            this.queueWrite({ kind: 'config', value: config });
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
     * Notes whether the text of a picker of the common window is a date. A picker reports the date that its text became before
     * it reports the text valid again, so the write that the invalid text held back is sent once the text is valid.
     * @param end which end the picker sets
     * @param valid whether its text is a date
     */
    protected onWindowTextValidityChange(end: WindowEnd, valid: boolean): void {
        const textValid = end === 'windowStart' ? this.isStartTextValid : this.isEndTextValid;
        if (textValid() === valid) {
            return;
        }
        textValid.set(valid);
        if (valid) {
            this.persistConfig();
        }
    }

    /**
     * Notes whether the text of a picker of a group window is a date: the windows are not written while it is not, and the
     * write held back meanwhile is sent once it is, as for the common window.
     * @param groupWindow the window row of the picker
     * @param end which end the picker sets
     * @param valid whether its text is a date
     */
    protected onGroupWindowTextValidityChange(groupWindow: MetisGroupWindowDTO, end: WindowEnd, valid: boolean): void {
        const key = textKey(groupWindow, end);
        if (this.invalidGroupWindowTexts().has(key) !== valid) {
            return;
        }
        this.invalidGroupWindowTexts.update((keys) => {
            const next = new Set(keys);
            if (valid) {
                next.delete(key);
            } else {
                next.add(key);
            }
            return next;
        });
        if (valid) {
            this.persistGroupWindows();
        }
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
        if (this.groupWindowsLoaded() && !this.hasUnwritableGroupWindow()) {
            this.queueWrite({ kind: 'groupWindows', value: this.groupWindows() });
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
     * Queues a write for the exercise the form holds behind every read and write queued before it, unless the section has closed.
     * @param write what to store
     */
    private queueWrite(write: SettingsWrite): void {
        const exerciseId = this.formExerciseId;
        if (this.closed || exerciseId === undefined) {
            return;
        }
        const version = this.versionsOf(exerciseId)[write.kind];
        const queued: QueuedWrite =
            write.kind === 'config' ? { kind: 'config', value: write.value, exerciseId, version } : { kind: 'groupWindows', value: write.value, exerciseId, version };
        this.settingsQueue.enqueue(() => this.sendUnlessOutdated(queued));
    }

    /**
     * Sends a queued write to the exercise it was made for, unless its part of the settings of that exercise was reset to the
     * server's state after it was queued.
     * @param write the queued write
     */
    private sendUnlessOutdated(write: QueuedWrite): Observable<unknown> {
        if (write.version !== this.versionsOf(write.exerciseId)[write.kind]) {
            return EMPTY;
        }
        const request: Observable<unknown> =
            write.kind === 'config' ? this.metisService.saveConfig(write.value) : this.metisService.saveGroupWindows(write.exerciseId, write.value);
        return request.pipe(catchError((error: unknown) => this.recoverFromRefusedWrite(write, error)));
    }

    /** The versions of the two parts of the settings of an exercise. */
    private versionsOf(exerciseId: number): Record<SettingsPart, number> {
        let versions = this.versions.get(exerciseId);
        if (!versions) {
            versions = { config: 0, groupWindows: 0 };
            this.versions.set(exerciseId, versions);
        }
        return versions;
    }

    /**
     * Handles a write the server refused. The global HTTP error handling already shows the server's message; here the
     * part of the form the write stored is brought back to what the server stored, so the refused value is neither shown
     * nor sent again, and the writes of that part queued meanwhile are dropped. The other part keeps its changes and its
     * queued writes: the queue runs this while no write is on its way, so the stored state it reads is the one the writes
     * before left. A refused scale change also locks the scale. The queue waits for the stored state before it goes on. A
     * refused write of an exercise the form no longer holds drops the writes of its part queued behind it all the same, but
     * leaves the form alone.
     * @param write the refused write
     * @param error the error response of the refused write
     */
    private recoverFromRefusedWrite(write: QueuedWrite, error: unknown): Observable<never> {
        const part = write.kind;
        const exerciseId = write.exerciseId;
        const isShown = () => exerciseId === this.formExerciseId;
        if (!isShown()) {
            this.versionsOf(exerciseId)[part] += 1;
            return EMPTY;
        }
        if (error instanceof HttpErrorResponse && error.error?.errorKey === SCALE_LOCKED_ERROR_KEY) {
            this.scaleLocked.set(true);
        }
        const reload: Observable<unknown> =
            part === 'config'
                ? this.metisService.config(exerciseId).pipe(
                      tap((config) => {
                          if (isShown()) {
                              this.applyStoredConfig(config);
                          }
                      }),
                  )
                : this.metisService.groupWindows(exerciseId).pipe(
                      tap((groupWindows) => {
                          if (isShown()) {
                              this.applyStoredGroupWindows(groupWindows);
                          }
                      }),
                  );
        return reload.pipe(
            tap(() => (this.versionsOf(exerciseId)[part] += 1)),
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
