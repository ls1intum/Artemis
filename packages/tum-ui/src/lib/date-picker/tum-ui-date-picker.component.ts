import {
    ChangeDetectionStrategy,
    Component,
    DestroyRef,
    ElementRef,
    TemplateRef,
    ViewContainerRef,
    booleanAttribute,
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
import { DOCUMENT } from '@angular/common';
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

/** Date-and-time field with typed input and an accessible calendar dialog. */
@Component({
    selector: 'tum-ui-date-picker',
    templateUrl: './tum-ui-date-picker.component.html',
    styleUrl: './tum-ui-date-picker.component.scss',
    host: {
        // The application stylesheet excludes TUM UI controls from the JHipster validity accent by this class.
        class: 'tum-ui-date-picker',
    },
    imports: [A11yModule, FaIconComponent, FaStackComponent, FaStackItemSizeDirective, TumUiButtonComponent, TumUiCalendarComponent, TumUiTooltipDirective, TumUiTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiDatePickerComponent implements FormValueControl<dayjs.Dayjs | undefined> {
    private readonly overlayService = inject(TumUiOverlayService);
    private readonly viewContainerRef = inject(ViewContainerRef);
    private readonly destroyRef = inject(DestroyRef);
    private readonly document = inject(DOCUMENT);

    /**
     * Last committed date. Invalid text remains visible without updating it.
     * Observe `inputValidityChange` when validity must react to uncommitted text.
     */
    readonly value = model<dayjs.Dayjs | undefined>(undefined);

    /** Adds an external validation error without discarding the last committed value. */
    readonly invalid = input(false, { transform: booleanAttribute });
    readonly disabled = input(false, { transform: booleanAttribute });
    /** Hides the visible label while retaining the input's accessible name. */
    readonly hideLabelName = input(false, { transform: booleanAttribute });
    /** Hides the built-in validation message without changing validity or `aria-invalid`. */
    readonly hideValidationMessage = input(false, { transform: booleanAttribute });
    /** Shows the browser time-zone indicator beside the label. */
    readonly shouldDisplayTimeZoneWarning = input(true, { transform: booleanAttribute });

    /** ID used to associate the input, label, validation message, and dialog. */
    readonly inputId = input(`tum-ui-date-picker-${nextDatePickerId++}`);
    /** Visible label text or package translation key. */
    readonly labelName = input<string>();
    /** Accessible name used when no visible label is rendered. */
    readonly ariaLabel = input<string>();
    /** Emits text-input validity independently of the external `invalid` state. */
    readonly inputValidityChange = output<boolean>();
    /** Emits when the text input loses focus. */
    readonly touch = output<void>();

    protected readonly faCalendar = faCalendar;
    protected readonly faXmark = faXmark;
    protected readonly faGlobe = faGlobe;
    protected readonly faClock = faClock;
    protected readonly faChevronUp = faChevronUp;
    protected readonly faChevronDown = faChevronDown;
    protected get currentTimeZone(): string {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
    }

    // Equivalent Dayjs instances must not reset uncommitted text; key linked state by its displayed value.
    private readonly valueKey = computed(() => {
        const current = this.value();
        return current ? formatDisplay(current) : '';
    });
    private readonly isInputValid = linkedSignal(() => {
        this.valueKey();
        return true;
    });
    protected readonly isOpen = signal(false);
    protected readonly panelId = computed(() => `${this.inputId()}-dialog`);
    protected readonly activeMonth = signal(dayjs().startOf('month'));
    protected readonly timeText = signal('');
    protected readonly inputText = linkedSignal(() => this.valueKey());

    private readonly panel = viewChild.required('panel', { read: TemplateRef });
    private readonly dateInput = viewChild.required<ElementRef<HTMLInputElement>>('dateInput');
    private readonly triggerWrapper = viewChild.required<ElementRef<HTMLElement>>('triggerWrapper');
    private overlayRef?: OverlayRef;
    private restoreFocusElement?: HTMLElement;

    protected readonly showErrorBorder = computed(() => this.invalid() || !this.isInputValid());
    protected readonly showClear = computed(() => !!this.inputText());
    protected readonly displayHour = computed(() => (TIME_REGEX.test(this.timeText()) ? this.timeText().split(':')[0] : '00'));
    protected readonly displayMinute = computed(() => (TIME_REGEX.test(this.timeText()) ? this.timeText().split(':')[1] : '00'));

    constructor() {
        this.destroyRef.onDestroy(() => this.overlayRef?.dispose());
        effect(() => this.inputValidityChange.emit(this.isInputValid()));
        effect(() => {
            if (this.disabled()) {
                this.close();
            }
        });
    }
    /** Whether the entered text parses successfully and the external `invalid` state is clear. */
    readonly isValid = computed(() => !this.invalid() && this.isInputValid());

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
        this.touch.emit();
    }
    protected stepHour(delta: number): void {
        const { hour, minute } = this.currentTimeParts();
        this.commitTime((hour + delta + 24) % 24, minute);
    }

    protected stepMinute(delta: number): void {
        const { hour, minute } = this.currentTimeParts();
        this.commitTime(hour, (minute + delta + 60) % 60);
    }

    protected onHourInput(input: HTMLInputElement): void {
        const parsed = this.parseTimePart(input.value, 23);
        if (parsed === undefined) {
            input.value = this.displayHour();
            return;
        }
        this.commitTime(parsed, this.currentTimeParts().minute);
        input.value = this.displayHour();
    }

    protected onMinuteInput(input: HTMLInputElement): void {
        const parsed = this.parseTimePart(input.value, 59);
        if (parsed === undefined) {
            input.value = this.displayMinute();
            return;
        }
        this.commitTime(this.currentTimeParts().hour, parsed);
        input.value = this.displayMinute();
    }

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

    private currentTimeParts(): { hour: number; minute: number } {
        const text = this.timeText();
        if (!TIME_REGEX.test(text)) {
            return { hour: 0, minute: 0 };
        }
        const [hour, minute] = text.split(':').map(Number);
        return { hour, minute };
    }

    private parseTimePart(raw: string, max: number): number | undefined {
        const trimmed = raw.trim();
        if (!/^\d{1,2}$/.test(trimmed)) {
            return undefined;
        }
        const value = Number(trimmed);
        return value <= max ? value : undefined;
    }
    private commitTime(hour: number, minute: number): void {
        this.timeText.set(`${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`);
        const base = this.value() ?? dayjs().startOf('day');
        this.commit(base.hour(hour).minute(minute).second(0).millisecond(0));
    }

    protected onDaySelect(day: dayjs.Dayjs): void {
        const time = this.value() ?? dayjs().startOf('day');
        this.commit(combineDateAndTime(day, time));
    }

    protected clear(): void {
        this.isInputValid.set(true);
        this.inputText.set('');
        if (this.value() !== undefined) {
            this.value.set(undefined);
        }
        this.dateInput().nativeElement.focus();
    }

    protected toggle(): void {
        if (this.isOpen()) {
            this.close();
        } else {
            this.open();
        }
    }

    protected openFromInput(event: Event): void {
        event.preventDefault();
        this.open();
    }

    protected open(): void {
        if (this.isOpen() || this.disabled()) {
            return;
        }
        const anchor = this.value() ?? dayjs();
        this.activeMonth.set(anchor.startOf('month'));
        this.timeText.set(this.value()?.format('HH:mm') ?? '');
        const activeElement = this.document.activeElement;
        this.restoreFocusElement = activeElement && typeof (activeElement as HTMLElement).focus === 'function' ? (activeElement as HTMLElement) : undefined;
        this.overlayRef = this.overlayService.createConnectedOverlay(this.triggerWrapper(), 'bottom', { hasBackdrop: true });
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
        if (!this.disabled() && this.restoreFocusElement?.isConnected) {
            this.restoreFocusElement.focus();
        }
        this.restoreFocusElement = undefined;
    }

    focus(options?: FocusOptions): void {
        this.dateInput().nativeElement.focus(options);
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
