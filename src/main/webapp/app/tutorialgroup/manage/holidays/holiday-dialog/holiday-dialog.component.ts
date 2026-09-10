import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { TumUiButtonDirective, TumUiDatePickerComponent, TumUiDialogComponent, TumUiFormFieldComponent, TumUiInputDirective, TumUiMessageComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { Holiday, endOfHolidayDay, lastDayCovered } from 'app/tutorialgroup/manage/holidays/holiday.model';

/** Mirrors the `@Size` the server puts on the reason, so the field stops where the request would be rejected. */
const REASON_MAX_LENGTH = 256;

/** What the dialog hands back on save: the span exactly as it will be stored. */
export interface HolidaySubmission {
    readonly start: dayjs.Dayjs;
    readonly end: dayjs.Dayjs;
    readonly reason: string;
}

/**
 * Creates and edits a holiday.
 *
 * The reader picks when it starts and when it ends, and that one span expresses every shape the old page made them
 * choose a kind for first: a single day, a run of days such as a two-week break, and a slot within one day. It opens on
 * 00:00 to 23:59, so the whole-day case - by far the common one - is already filled in and needs no switch of its own;
 * narrowing a holiday to part of a day is a matter of editing the times the fields already carry.
 */
@Component({
    selector: 'jhi-holiday-dialog',
    templateUrl: './holiday-dialog.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        TranslateDirective,
        ArtemisTranslatePipe,
        TumUiButtonDirective,
        TumUiDatePickerComponent,
        TumUiDialogComponent,
        TumUiFormFieldComponent,
        TumUiInputDirective,
        TumUiMessageComponent,
    ],
})
export class HolidayDialogComponent {
    readonly visible = model(false);
    /** The holiday being edited, or undefined to create one. */
    readonly holiday = input<Holiday | undefined>(undefined);
    /** Prefills the date when the reader opened the dialog by clicking a day in the calendar. */
    readonly initialDay = input<dayjs.Dayjs | undefined>(undefined);
    /** Sessions the span currently covers, so the dialog can say what saving would cancel. */
    readonly sessionCountForSelectedSpan = input(0);
    readonly saving = input(false);

    readonly save = output<HolidaySubmission>();
    /** The chosen span changed, so the page can count the sessions it covers. */
    readonly selectedSpanChange = output<{ start: dayjs.Dayjs; end: dayjs.Dayjs }>();

    private readonly translateService = inject(TranslateService);
    private readonly locale = getCurrentLocaleSignal(this.translateService);

    protected readonly start = signal<dayjs.Dayjs | undefined>(undefined);
    protected readonly end = signal<dayjs.Dayjs | undefined>(undefined);
    protected readonly reason = signal('');
    /**
     * Whether what is typed in each field parses.
     *
     * A picker keeps its last committed value when the text becomes invalid, so without this the reader could type
     * nonsense, see it sitting in the field, and still save the date it had replaced.
     */
    protected readonly startTextIsValid = signal(true);
    protected readonly endTextIsValid = signal(true);

    protected readonly reasonMaxLength = REASON_MAX_LENGTH;

    protected readonly isEditMode = computed(() => this.holiday() !== undefined);

    /** Measured against the last day the span covers: an end at midnight belongs to the day before it. */
    protected readonly spansMultipleDays = computed(() => {
        const start = this.start();
        const end = this.end();
        return !!start && !!end && !start.startOf('day').isSame(lastDayCovered(start, end), 'day');
    });

    /** Names the days rather than repeating the dates, which the fields already show. */
    protected readonly startWeekday = computed(() => this.start()?.locale(this.locale()).format('dddd') ?? '');
    protected readonly endWeekday = computed(() => this.end()?.locale(this.locale()).format('dddd') ?? '');

    protected readonly endIsBeforeStart = computed(() => {
        const start = this.start();
        const end = this.end();
        return !!start && !!end && !end.isAfter(start);
    });

    protected readonly canSave = computed(
        () => !!this.start() && !!this.end() && this.startTextIsValid() && this.endTextIsValid() && this.reason().trim().length > 0 && !this.endIsBeforeStart() && !this.saving(),
    );

    /**
     * Reloads the form whenever the dialog opens on a holiday, so a cancelled edit never leaks into the next one.
     *
     * Untracked around the writes because filling the form reads the very fields it writes - through emitSpan - and an
     * effect tracking those would re-run on the reader's first edit and reset the dialog under them.
     *
     * Held in a field rather than run from a constructor so it carries a name; protected because a private one reads
     * as unused, which is the same reason the sidebar-sync effects elsewhere in the client are declared that way.
     */
    protected readonly fillFormOnOpen = effect(() => {
        if (!this.visible()) {
            return;
        }
        const holiday = this.holiday();
        const initialDay = this.initialDay();
        untracked(() => {
            // A dialog reopened after invalid text was left in a field starts from the values it is given.
            this.startTextIsValid.set(true);
            this.endTextIsValid.set(true);
            if (holiday) {
                this.start.set(holiday.start);
                this.end.set(holiday.end);
                this.reason.set(holiday.reason);
            } else {
                const day = (initialDay ?? dayjs()).startOf('day');
                this.start.set(day);
                this.end.set(endOfHolidayDay(day));
                this.reason.set('');
            }
            this.emitSpan();
        });
    });

    protected onStartChange(value: dayjs.Dayjs | undefined): void {
        if (!value) {
            this.start.set(undefined);
            return;
        }
        const previousStart = this.start();
        this.start.set(value);

        const end = this.end();
        if (end && previousStart) {
            // Moving the start carries a holiday of one day with it, so the common case needs one edit rather than
            // two. Moved by whole days rather than rebuilt, so the time it ends at - and which midnight that is -
            // survive the move.
            const coveredOneDay = previousStart.startOf('day').isSame(lastDayCovered(previousStart, end), 'day');
            const dayDelta = value.startOf('day').diff(previousStart.startOf('day'), 'day');
            if (coveredOneDay) {
                if (dayDelta !== 0) {
                    this.end.set(end.add(dayDelta, 'day'));
                }
            } else if (!end.isAfter(value)) {
                this.end.set(endOfHolidayDay(value));
            }
        }
        this.emitSpan();
    }

    protected onEndChange(value: dayjs.Dayjs | undefined): void {
        this.end.set(value);
        this.emitSpan();
    }

    private emitSpan(): void {
        const start = this.start();
        const end = this.end();
        if (start && end && end.isAfter(start)) {
            this.selectedSpanChange.emit({ start, end });
        }
    }

    protected onReasonInput(event: Event): void {
        this.reason.set((event.target as HTMLInputElement).value);
    }

    protected onSubmit(event: Event): void {
        // The dialog owns its state, so the browser must not navigate away on submit.
        event.preventDefault();
        const start = this.start();
        const end = this.end();
        if (!start || !end || !this.canSave()) {
            return;
        }
        this.save.emit({ start, end, reason: this.reason().trim() });
    }

    protected onCancel(): void {
        this.visible.set(false);
    }
}
