import {
    ChangeDetectionStrategy,
    Component,
    DestroyRef,
    ElementRef,
    TemplateRef,
    ViewContainerRef,
    computed,
    effect,
    inject,
    input,
    linkedSignal,
    model,
    output,
    signal,
    viewChild,
} from '@angular/core';
import { A11yModule } from '@angular/cdk/a11y';
import { OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import type { FormValueControl } from '@angular/forms/signals';
import dayjs from 'dayjs/esm';
import { FaIconComponent, FaStackComponent, FaStackItemSizeDirective } from '@fortawesome/angular-fontawesome';
import { faCalendar, faChevronDown, faChevronUp, faClock, faGlobe, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonComponent } from '../button/tum-ui-button.component';
import { TumUiOverlayService } from '../overlay/tum-ui-overlay.service';
import { TumUiTooltipDirective } from '../tooltip/tum-ui-tooltip.directive';
import { TumUiCalendarComponent } from './tum-ui-calendar.component';
import { DISPLAY_REGEX, TIME_REGEX, combineDateAndTime, formatDisplay, parseDisplay, valuesEqual } from './tum-ui-date-picker.util';
import { TumUiTranslatePipe } from '../i18n/tum-ui-translate.pipe';

let nextDatePickerId = 0;

/**
 * Date+time picker on Angular CDK overlay + a hand-built dayjs calendar.
 *
 * Signal-based and PrimeNG-free. Implements the Signal Forms {@link FormValueControl} contract (its only
 * required member is the `value` model), so it is bindable via `[field]`/`Control` with no ControlValueAccessor;
 * it also works imperatively via `[value]`/`(valueChange)` + `value()`/`isValid()`. Ports the keepInvalid typed-text
 * and blur-format validation from the legacy jhi-date-time-picker; the calendar opens via {@link TumUiOverlayService}.
 */
@Component({
    selector: 'tum-ui-date-picker',
    templateUrl: './tum-ui-date-picker.component.html',
    styleUrl: './tum-ui-date-picker.component.scss',
    imports: [A11yModule, FaIconComponent, FaStackComponent, FaStackItemSizeDirective, TumUiButtonComponent, TumUiCalendarComponent, TumUiTooltipDirective, TumUiTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiDatePickerComponent implements FormValueControl<dayjs.Dayjs | undefined> {
    private readonly overlayService = inject(TumUiOverlayService);
    private readonly viewContainerRef = inject(ViewContainerRef);
    private readonly destroyRef = inject(DestroyRef);

    /**
     * Signal Forms FormValueControl contract member; also serves the `[value]` input, the `value()` accessor,
     * and the change notification. `model()` auto-creates the `valueChange` output that consumers bind with
     * `(valueChange)` and that powers two-way `[(value)]`, so we deliberately do NOT declare a second
     * `valueChange` output (that would shadow the model's and silently break two-way binding). It fires only
     * when the value actually changes (commit / clear / empty) — never on a keepInvalid keystroke, so an
     * invalid edit does not round-trip through a consumer and wipe the typed text.
     */
    readonly value = model<dayjs.Dayjs | undefined>(undefined);

    readonly error = input(false);
    readonly disabled = input(false);
    readonly hideLabelName = input(false);
    readonly hideValidationMessage = input(false);
    readonly shouldDisplayTimeZoneWarning = input(true);
    /** `id` of the inner `<input>` (and the label's `for`). Defaults to a unique per-instance id; set it explicitly when you need a stable, known id. */
    readonly inputId = input(`tum-ui-date-picker-${nextDatePickerId++}`);
    readonly labelName = input<string>();
    /**
     * Accessible name for the inner `<input>`, forwarded as `aria-label`. Set it when the field renders
     * without a visible label (`hideLabelName`) so screen-reader users still hear which date it is
     * (e.g. `"Delete from"`); otherwise the visible `<label for>` already names the input.
     */
    readonly ariaLabel = input<string>();
    readonly baseZIndex = input(1060);

    /**
     * Emits whether the currently typed text parses to a valid date, on every parse-validity change (NOT the
     * combined {@link isValid}, which also folds in the external `[error]`). Because an unparseable edit
     * deliberately does NOT emit `valueChange` (keepInvalid), a consumer that must gate a destructive action on
     * the field being parseable (e.g. disable a submit button) should listen here rather than infer validity
     * from `valueChange`. Mirror of the {@link hasValidInput} accessor as an output.
     */
    readonly parseValidChange = output<boolean>();

    protected readonly faCalendar = faCalendar;
    protected readonly faXmark = faXmark;
    protected readonly faGlobe = faGlobe;
    protected readonly faClock = faClock;
    protected readonly faChevronUp = faChevronUp;
    protected readonly faChevronDown = faChevronDown;

    /** The viewer's local IANA time zone, shown in the timezone-warning tooltip (mirrors the legacy picker). */
    protected get currentTimeZone(): string {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
    }

    private readonly valueKey = computed(() => {
        const current = this.value();
        return current ? formatDisplay(current) : '';
    });
    private readonly isInputValid = linkedSignal(() => {
        this.valueKey();
        return true;
    });
    protected readonly isOpen = signal(false);
    protected readonly activeMonth = signal(dayjs().startOf('month'));
    protected readonly timeText = signal('');
    protected readonly inputText = linkedSignal(() => this.valueKey());

    private readonly panel = viewChild.required('panel', { read: TemplateRef });
    private readonly triggerWrapper = viewChild.required<ElementRef<HTMLElement>>('triggerWrapper');
    private overlayRef?: OverlayRef;

    protected readonly showErrorBorder = computed(() => this.error() || !this.isInputValid());
    protected readonly showClear = computed(() => !!this.inputText());
    protected readonly displayHour = computed(() => (TIME_REGEX.test(this.timeText()) ? this.timeText().split(':')[0] : '00'));
    protected readonly displayMinute = computed(() => (TIME_REGEX.test(this.timeText()) ? this.timeText().split(':')[1] : '00'));

    constructor() {
        this.destroyRef.onDestroy(() => this.overlayRef?.dispose());
        effect(() => this.parseValidChange.emit(this.isInputValid()));
        effect(() => {
            if (this.disabled()) {
                this.close();
            }
        });
    }

    /**
     * Overall validity: the typed input parses AND the component is not externally `[error]`-flagged.
     * A signal (the kit is signal-first); use it to gate an imperative action such as enabling a submit button.
     */
    readonly isValid = computed(() => !this.error() && this.isInputValid());

    /**
     * Whether the currently typed text parses to a valid date, ignoring the external `[error]` input.
     * Note: `valueChange` already fires only on committed / cleared values, so a `(valueChange)` handler can
     * consume `$event` directly — this accessor exists for imperative parse-only checks that must disregard
     * an external error (e.g. a from>to range the consumer flags itself).
     */
    readonly hasValidInput = computed(() => this.isInputValid());

    protected onInput(raw: string): void {
        this.inputText.set(raw);
        const parsed = parseDisplay(raw);
        if (parsed) {
            this.commit(parsed);
        } else if (!raw.trim()) {
            if (this.value() !== undefined) {
                this.value.set(undefined);
            } else {
                this.isInputValid.set(true);
            }
        } else {
            this.isInputValid.set(false);
        }
    }

    protected onBlur(raw: string): void {
        const trimmed = raw.trim();
        if (trimmed && !DISPLAY_REGEX.test(trimmed)) {
            this.isInputValid.set(false);
        }
    }

    /** Increment (`+1`) or decrement (`-1`) the hour, wrapping 23→0 / 0→23 like the legacy PrimeNG spinner. */
    protected stepHour(delta: number): void {
        const { hour, minute } = this.currentTimeParts();
        this.commitTime((hour + delta + 24) % 24, minute);
    }

    /** Increment (`+1`) or decrement (`-1`) the minute, wrapping 59→0 / 0→59 without carrying into the hour. */
    protected stepMinute(delta: number): void {
        const { hour, minute } = this.currentTimeParts();
        this.commitTime(hour, (minute + delta + 60) % 60);
    }

    /**
     * Commit a typed hour. Fires on `change` (blur / Enter), not per keystroke, so the `[value]` binding never
     * fights the caret mid-edit. Unparseable / out-of-range text is rejected and the field reverts to the last
     * committed hour; a valid value is clamped-parsed, committed, and re-rendered zero-padded.
     */
    protected onHourInput(input: HTMLInputElement): void {
        const parsed = this.parseTimePart(input.value, 23);
        if (parsed === undefined) {
            input.value = this.displayHour();
            return;
        }
        this.commitTime(parsed, this.currentTimeParts().minute);
        input.value = this.displayHour();
    }

    /** Commit a typed minute; see {@link onHourInput} for the change-vs-input and revert rationale. */
    protected onMinuteInput(input: HTMLInputElement): void {
        const parsed = this.parseTimePart(input.value, 59);
        if (parsed === undefined) {
            input.value = this.displayMinute();
            return;
        }
        this.commitTime(this.currentTimeParts().hour, parsed);
        input.value = this.displayMinute();
    }

    /**
     * ArrowUp / ArrowDown on a spinner field nudge it by one (native-time / PrimeNG parity). It steps from the
     * text **currently in the field** — which may be an uncommitted typed edit that has not fired `change` yet —
     * not from the last committed `timeText()`. So typing `10` over `08` and pressing ArrowUp before blur yields
     * `11` (not `09`), preserving the edit; an unparseable/empty field falls back to the committed value.
     */
    protected onTimeKeydown(event: KeyboardEvent, input: HTMLInputElement, field: 'hour' | 'minute'): void {
        const delta = event.key === 'ArrowUp' ? 1 : event.key === 'ArrowDown' ? -1 : 0;
        if (delta === 0) {
            return;
        }
        event.preventDefault();
        const { hour, minute } = this.currentTimeParts();
        if (field === 'hour') {
            const base = this.parseTimePart(input.value, 23) ?? hour;
            this.commitTime((base + delta + 24) % 24, minute);
            input.value = this.displayHour();
        } else {
            const base = this.parseTimePart(input.value, 59) ?? minute;
            this.commitTime(hour, (base + delta + 60) % 60);
            input.value = this.displayMinute();
        }
    }

    /** The current spinner time as numbers; `{0, 0}` when nothing is set yet (empty picker). */
    private currentTimeParts(): { hour: number; minute: number } {
        const text = this.timeText();
        if (TIME_REGEX.test(text)) {
            const [hour, minute] = text.split(':').map(Number);
            return { hour, minute };
        }
        return { hour: 0, minute: 0 };
    }

    /** Parse 1–2 digits into `[0, max]`; returns undefined for empty / non-numeric / out-of-range text. */
    private parseTimePart(raw: string, max: number): number | undefined {
        const trimmed = raw.trim();
        if (!/^\d{1,2}$/.test(trimmed)) {
            return undefined;
        }
        const value = Number(trimmed);
        return value <= max ? value : undefined;
    }

    /**
     * Apply an hour+minute to the value. With no value yet, base the date on today — NOT activeMonth (always
     * month-start), which would silently commit the 1st of the current month. Mirrors onDaySelect's `dayjs()`
     * fallback. `timeText` is set up-front so the spinner reflects the change even when `commit` short-circuits
     * on an unchanged instant (e.g. re-typing the current time).
     */
    private commitTime(hour: number, minute: number): void {
        this.timeText.set(`${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`);
        const base = this.value() ?? dayjs().startOf('day');
        this.commit(base.hour(hour).minute(minute).second(0).millisecond(0));
    }

    protected onDaySelect(day: dayjs.Dayjs): void {
        const time = this.value() ?? dayjs();
        this.commit(combineDateAndTime(day, time));
    }

    protected clear(): void {
        this.isInputValid.set(true);
        this.inputText.set('');
        if (this.value() !== undefined) {
            this.value.set(undefined);
        }
    }

    protected toggle(): void {
        if (this.isOpen()) {
            this.close();
        } else {
            this.open();
        }
    }

    protected open(): void {
        if (this.isOpen() || this.disabled()) {
            return;
        }
        const anchor = this.value() ?? dayjs();
        this.activeMonth.set(anchor.startOf('month'));
        this.timeText.set(this.value()?.format('HH:mm') ?? '');
        this.overlayRef = this.overlayService.createConnectedOverlay(this.triggerWrapper(), 'bottom', { hasBackdrop: true });
        this.overlayRef.overlayElement.style.zIndex = String(this.baseZIndex());
        this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
        this.overlayRef.backdropClick().subscribe(() => this.close());
        this.overlayRef.keydownEvents().subscribe((event) => {
            if (event.key === 'Escape') {
                this.close();
            }
        });
        this.isOpen.set(true);
    }

    protected close(): void {
        if (!this.isOpen()) {
            return;
        }
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
        this.isOpen.set(false);
    }

    private commit(next: dayjs.Dayjs): void {
        this.isInputValid.set(true);
        if (valuesEqual(this.value(), next)) {
            this.inputText.set(formatDisplay(next));
            return;
        }
        this.activeMonth.set(next.startOf('month'));
        this.timeText.set(next.format('HH:mm'));
        this.value.set(next);
    }
}
