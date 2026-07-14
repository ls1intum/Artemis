import {
    ChangeDetectionStrategy,
    Component,
    DestroyRef,
    ElementRef,
    TemplateRef,
    ViewContainerRef,
    computed,
    inject,
    input,
    linkedSignal,
    model,
    signal,
    viewChild,
} from '@angular/core';
import { A11yModule } from '@angular/cdk/a11y';
import { OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import type { FormValueControl } from '@angular/forms/signals';
import dayjs from 'dayjs/esm';
import { FaIconComponent, FaStackComponent, FaStackItemSizeDirective } from '@fortawesome/angular-fontawesome';
import { faCalendar, faClock, faGlobe, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';
import { TumUiOverlayService } from 'app/shared-ui/tum-ui/overlay/tum-ui-overlay.service';
import { TumUiTooltipDirective } from 'app/shared-ui/tum-ui/tooltip/tum-ui-tooltip.directive';
import { TumUiCalendarComponent } from 'app/shared-ui/tum-ui/date-picker/tum-ui-calendar.component';
import { DISPLAY_REGEX, TIME_REGEX, combineDateAndTime, formatDisplay, parseDisplay, valuesEqual } from 'app/shared-ui/tum-ui/date-picker/tum-ui-date-picker.util';

/**
 * Owned date+time picker on Angular CDK overlay + a hand-built dayjs calendar, part of the tum-aet-ui kit.
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
    imports: [
        A11yModule,
        FaIconComponent,
        FaStackComponent,
        FaStackItemSizeDirective,
        TumUiButtonComponent,
        TumUiCalendarComponent,
        TumUiTooltipDirective,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
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
    readonly inputId = input('date-input-field');
    readonly labelName = input<string>();
    readonly baseZIndex = input(1060);

    protected readonly faCalendar = faCalendar;
    protected readonly faXmark = faXmark;
    protected readonly faGlobe = faGlobe;
    protected readonly faClock = faClock;

    /** The viewer's local IANA time zone, shown in the timezone-warning tooltip (mirrors the legacy picker). */
    protected get currentTimeZone(): string {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
    }

    // Reset to true whenever `value()` changes from the outside (or via a commit), so a stale error border
    // does not linger over a freshly-supplied valid date. Stays false while the user types unparseable text
    // (value() unchanged), which is exactly the keepInvalid window.
    private readonly isInputValid = linkedSignal(() => {
        this.value();
        return true;
    });
    protected readonly isOpen = signal(false);
    protected readonly activeMonth = signal(dayjs().startOf('month'));
    protected readonly timeText = signal('');
    protected readonly inputText = linkedSignal(() => {
        const current = this.value();
        return current ? formatDisplay(current) : '';
    });

    private readonly panel = viewChild.required('panel', { read: TemplateRef });
    private readonly triggerWrapper = viewChild.required<ElementRef<HTMLElement>>('triggerWrapper');
    private overlayRef?: OverlayRef;

    protected readonly showErrorBorder = computed(() => this.error() || !this.isInputValid());
    protected readonly showClear = computed(() => !!this.inputText());

    constructor() {
        this.destroyRef.onDestroy(() => this.overlayRef?.dispose());
    }

    /** Overall validity: not externally-errored and the typed input parses. Read imperatively by consumers. */
    isValid(): boolean {
        return !(this.error() || !this.isInputValid());
    }

    /**
     * Whether the currently typed text parses to a valid date, independent of the external `error` input.
     * Consumers should gate `(valueChange)` on this rather than {@link isValid}: otherwise, once the consumer
     * flags an external error (e.g. a from>to range), `isValid()` stays false and every subsequent edit would
     * propagate `undefined`, wiping the value so the error could never be corrected.
     */
    hasValidInput(): boolean {
        return this.isInputValid();
    }

    protected onInput(raw: string): void {
        this.inputText.set(raw);
        const parsed = parseDisplay(raw);
        if (parsed) {
            this.commit(parsed);
        } else if (!raw.trim()) {
            // Emptied: clear the value; the model's valueChange fires and the linkedSignal re-validates.
            if (this.value() !== undefined) {
                this.value.set(undefined);
            } else {
                this.isInputValid.set(true);
            }
        } else {
            // keepInvalid: preserve the typed text (set above), flag invalid, leave value() untouched.
            // Intentionally NO notification: value() is unchanged, so a consumer never round-trips undefined
            // back into [value] and wipes the text while the user is still editing.
            this.isInputValid.set(false);
        }
    }

    protected onBlur(raw: string): void {
        const trimmed = raw.trim();
        if (trimmed && !DISPLAY_REGEX.test(trimmed)) {
            this.isInputValid.set(false);
        }
    }

    protected onTimeChange(raw: string): void {
        const trimmed = raw.trim();
        if (!TIME_REGEX.test(trimmed)) {
            return;
        }
        const [hour, minute] = trimmed.split(':').map(Number);
        // With no value yet, base the date on today — NOT activeMonth (always month-start), which would
        // silently commit the 1st of the current month. Mirrors onDaySelect's `dayjs()` fallback.
        const base = this.value() ?? dayjs().startOf('day');
        this.commit(base.hour(hour).minute(minute).second(0).millisecond(0));
    }

    protected onDaySelect(day: dayjs.Dayjs): void {
        const time = this.value() ?? dayjs();
        this.commit(combineDateAndTime(day, time));
    }

    protected clear(): void {
        // Clear both the display text and the value. Setting inputText explicitly covers the case where the
        // field held unparseable text while value() was already undefined (so value.set is a no-op).
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
        this.overlayRef = this.overlayService.createConnectedOverlay(this.triggerWrapper(), 'bottom', true);
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
            // No value change (e.g. re-typing the current value after an invalid edit): nothing to notify.
            // Normalize the display text in case it still held the raw/invalid characters.
            this.inputText.set(formatDisplay(next));
            return;
        }
        this.activeMonth.set(next.startOf('month'));
        this.timeText.set(next.format('HH:mm'));
        this.value.set(next);
    }
}
