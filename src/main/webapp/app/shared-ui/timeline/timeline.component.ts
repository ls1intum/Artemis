import { TumUiTooltipDirective } from '@tumaet/ui-angular';
import { Component, Signal, WritableSignal, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DatePickerModule } from 'primeng/datepicker';
// TooltipModule remains for the still-PrimeNG `pTooltip` on the per-item invalid-date info icon; the variant-group
// lock overlay below uses the tum-ui kit tooltip.
import { TooltipModule } from 'primeng/tooltip';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faLock } from '@fortawesome/free-solid-svg-icons';
import dayjs, { Dayjs } from 'dayjs/esm';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { TranslateService } from '@ngx-translate/core';

export interface TimelineItem {
    kind: 'required' | 'optional';
    labelStringKey: string;
    date: WritableSignal<Dayjs | undefined>;
    warningStringKey?: Signal<string | undefined>;
    otherRequiredItem?: TimelineItem;
    /**
     * Overrides which earlier items this item's date is checked against for ordering. By default (when undefined) an
     * item must not precede any earlier item in the {@link ExerciseTimelineComponent.timelineItems} array. Pass an
     * explicit list to restrict the check to only those items instead — e.g. an exercise-variant-group's example
     * solution publication date only needs to be `>= releaseDate` (see `ExerciseVariantGroup#areDatesValid`), not
     * `>= dueDate` as a single exercise's date would.
     */
    orderCheckAgainst?: TimelineItem[];
}

export interface TimelineStatus {
    valid: boolean;
    empty: boolean;
}

type InternalTimelineItem = TimelineItem & {
    internalDate: Date | undefined;
    isInputRequiredButUndefined: boolean;
    hasInvalidDateOrder: boolean;
    isOtherRequiredItemDateUndefined: boolean;
    isInvalidInput: boolean;
    hasWarning: boolean;
    tooltip: string | undefined;
};

export enum TimelineValidationMode {
    SEQUENTIALLY_STRICT = 'SEQUENTIALLY_STRICT',
    SEQUENTIALLY_ALLOW_EQUAL = 'SEQUENTIALLY_ALLOW_EQUAL',
}

@Component({
    selector: 'jhi-timeline',
    imports: [DatePickerModule, FormsModule, TooltipModule, TumUiTooltipDirective, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
    templateUrl: './timeline.component.html',
    styleUrl: './timeline.component.scss',
})
export class TimelineComponent {
    private translateService = inject(TranslateService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);
    private readonly fullDateTimePattern = /^\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}$/;
    private readonly dateTimeFormat = 'DD.MM.YYYY HH:mm';
    protected readonly Date = Date;
    protected readonly faLock = faLock;
    /** Label keys of items whose currently-typed text is non-empty but not a valid date. Drives the
     *  invalid (red border + tooltip) state so a malformed entry is flagged instead of silently dropped. */
    private invalidInputKeys = signal<Set<string>>(new Set());

    timelineItems = input.required<TimelineItem[]>();
    validationMode = input<TimelineValidationMode>(TimelineValidationMode.SEQUENTIALLY_ALLOW_EQUAL);
    readonly = input<boolean>(false);
    /**
     * When true the dates are governed by the exercise's variant group: every datepicker is disabled and a click
     * anywhere on the timeline emits {@link lockedClick} so the host can open the group-edit dialog.
     */
    lockedToGroup = input<boolean>(false);
    /** Emitted when the user clicks the timeline while {@link lockedToGroup} is set. */
    lockedClick = output<void>();
    /** Effective read-only state: either explicitly {@link readonly} or locked to the variant group. */
    isReadonly = computed<boolean>(() => this.readonly() || this.lockedToGroup());
    internalTimelineItems = computed<InternalTimelineItem[]>(() => this.computeInternalTimelineItems());
    timelineStatus = computed<TimelineStatus>(() => this.computeExerciseTimelineStatus());
    timelineStatusChange = output<TimelineStatus>();

    constructor() {
        effect(() => {
            const timelineStatus = this.timelineStatus();
            this.timelineStatusChange.emit(timelineStatus);
        });
    }

    updateDate(item: TimelineItem, newInternalDate: Date | string | null) {
        const currentDate = item.date();
        const newDate = newInternalDate instanceof Date ? dayjs(newInternalDate) : undefined;
        const oldAndNewDateUndefined = currentDate === undefined && newDate === undefined;
        const oldAndNewDatesAreTheSame = currentDate !== undefined && newDate !== undefined && currentDate.isSame(newDate);
        if (oldAndNewDateUndefined || oldAndNewDatesAreTheSame) return;
        item.date.set(newDate);
    }

    handleManualInput(item: TimelineItem, event: Event) {
        const value = (event.target as HTMLInputElement).value;
        const parsedDate = this.parseManualInput(value);
        if (parsedDate !== undefined) {
            this.setDateIfChanged(item, parsedDate);
            this.setInvalidInput(item, false);
        } else if (value === '') {
            this.setDateIfChanged(item, undefined);
            this.setInvalidInput(item, false);
        }
        // A not-yet-parseable value is left alone while typing and only flagged on blur (see handleBlur).
    }

    handleBlur(item: TimelineItem, event: Event) {
        const input = (event.target as HTMLInputElement).value;
        const inputWasCleared = input === '';
        const currentInputIsInvalidDate = this.parseManualInput(input) === undefined;
        // Keep the entered text (keepInvalid) and flag it instead of silently reverting it (PR #13009 review).
        this.setInvalidInput(item, currentInputIsInvalidDate && !inputWasCleared);
    }

    private setDateIfChanged(item: TimelineItem, newDate?: Dayjs) {
        const currentDate = item.date();
        if (currentDate?.isSame(newDate)) return;
        item.date.set(newDate);
    }

    /** Adds or removes the item's label key from the invalid-input set (no-op if already in that state). */
    private setInvalidInput(item: TimelineItem, invalid: boolean) {
        this.invalidInputKeys.update((keys) => {
            if (invalid === keys.has(item.labelStringKey)) {
                return keys;
            }
            const next = new Set(keys);
            if (invalid) {
                next.add(item.labelStringKey);
            } else {
                next.delete(item.labelStringKey);
            }
            return next;
        });
    }

    private parseManualInput(value: string): Dayjs | undefined {
        if (!this.fullDateTimePattern.test(value)) return undefined;
        const parsedDate = dayjs(value, this.dateTimeFormat, true);
        return parsedDate.isValid() ? parsedDate : undefined;
    }

    private computeInternalTimelineItems(): InternalTimelineItem[] {
        const invalidInputKeys = this.invalidInputKeys();
        return this.timelineItems().map((item, index, items) => {
            this.currentLocale();
            const date = item.date();
            const itemsToCheckAgainst = item.orderCheckAgainst ?? items.slice(0, index);
            const hasInvalidDateOrder =
                date !== undefined &&
                itemsToCheckAgainst.some((previousItem) => {
                    const previousDate = previousItem.date();
                    return previousDate !== undefined && this.isDateOrderInvalid(date, previousDate);
                });
            const isInputRequiredButUndefined = item.kind === 'required' && date === undefined;
            const otherRequiredItem = item.otherRequiredItem;
            const isOtherRequiredItemDateUndefined = date !== undefined && otherRequiredItem !== undefined && otherRequiredItem.date() === undefined;
            const isInvalidInput = invalidInputKeys.has(item.labelStringKey);
            const hasError = isInvalidInput || hasInvalidDateOrder || isInputRequiredButUndefined || isOtherRequiredItemDateUndefined;
            const warningStringKey = item.warningStringKey?.();
            const hasWarning = !hasError && warningStringKey !== undefined;
            let tooltip: string | undefined;
            if (isInvalidInput) {
                tooltip = this.translateService.instant('artemisApp.exercise.timelineDateInvalidTooltip');
            } else if (hasInvalidDateOrder) {
                const tooltipKey =
                    this.validationMode() === TimelineValidationMode.SEQUENTIALLY_STRICT
                        ? 'artemisApp.exercise.timelineDateStrictOrderTooltip'
                        : 'artemisApp.exercise.timelineDateOrderTooltip';
                tooltip = this.translateService.instant(tooltipKey);
            } else if (isInputRequiredButUndefined) {
                tooltip = this.translateService.instant('artemisApp.exercise.timelineDateRequiredTooltip');
            } else if (isOtherRequiredItemDateUndefined && otherRequiredItem) {
                const otherInputName = this.translateService.instant(otherRequiredItem.labelStringKey);
                tooltip = this.translateService.instant('artemisApp.exercise.timelineOtherRequiredDateTooltip', { otherInputName });
            } else if (warningStringKey !== undefined) {
                tooltip = this.translateService.instant(warningStringKey);
            }

            return {
                kind: item.kind,
                labelStringKey: item.labelStringKey,
                date: item.date,
                otherRequiredItem: item.otherRequiredItem,
                internalDate: date?.toDate(),
                isInputRequiredButUndefined,
                hasInvalidDateOrder,
                isOtherRequiredItemDateUndefined,
                isInvalidInput,
                hasWarning,
                tooltip,
            };
        });
    }

    private isDateOrderInvalid(date: Dayjs, previousDate: Dayjs): boolean {
        if (this.validationMode() === TimelineValidationMode.SEQUENTIALLY_STRICT) {
            return !date.isAfter(previousDate);
        }
        return date.isBefore(previousDate);
    }

    private computeExerciseTimelineStatus(): TimelineStatus {
        const items = this.internalTimelineItems();
        return {
            valid: items.every((item) => !item.hasInvalidDateOrder && !item.isInputRequiredButUndefined && !item.isOtherRequiredItemDateUndefined && !item.isInvalidInput),
            empty: items.some((item) => item.date() === undefined),
        };
    }
}
