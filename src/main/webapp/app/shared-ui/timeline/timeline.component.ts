import { Component, Signal, WritableSignal, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DatePickerModule } from 'primeng/datepicker';
import { TooltipModule } from 'primeng/tooltip';
import dayjs, { Dayjs } from 'dayjs/esm';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { TranslateService } from '@ngx-translate/core';

export interface TimelineItem {
    kind: 'required' | 'optional';
    labelStringKey: string;
    date: WritableSignal<Dayjs | undefined>;
    warningStringKey?: Signal<string | undefined>;
    errorStringKey?: Signal<string | undefined>;
    disabled?: boolean;
}

export interface InvalidTimelineItem {
    labelStringKey: string;
    reasonKey: string;
    dateName: string;
}

export interface TimelineStatus {
    valid: boolean;
    empty: boolean;
    invalidItems: InvalidTimelineItem[];
}

type InternalTimelineItem = TimelineItem & {
    internalDate: Date | undefined;
    isInputRequiredButUndefined: boolean;
    hasInvalidDateOrder: boolean;
    isInvalidInput: boolean;
    isDisabled: boolean;
    hasExternalError: boolean;
    hasWarning: boolean;
    tooltip: string | undefined;
};

@Component({
    selector: 'jhi-timeline',
    imports: [DatePickerModule, FormsModule, TooltipModule, TranslateDirective],
    templateUrl: './timeline.component.html',
    styleUrl: './timeline.component.scss',
})
export class TimelineComponent {
    private translateService = inject(TranslateService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);
    private readonly fullDateTimePattern = /^\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}$/;
    private readonly dateTimeFormat = 'DD.MM.YYYY HH:mm';
    protected readonly Date = Date;
    /** Label keys of items whose typed text is non-empty but not a valid date. Drives the invalid state. */
    private invalidInputKeys = signal<Set<string>>(new Set());

    timelineItems = input.required<TimelineItem[]>();
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
            const hasInvalidDateOrder =
                date !== undefined &&
                items.slice(0, index).some((previousItem) => {
                    const previousDate = previousItem.date();
                    return previousDate !== undefined && !date.isAfter(previousDate);
                });
            const isInputRequiredButUndefined = item.kind === 'required' && date === undefined;
            const isInvalidInput = invalidInputKeys.has(item.labelStringKey);
            const isDisabled = item.disabled ?? false;
            const hasInternalError = isInvalidInput || hasInvalidDateOrder || isInputRequiredButUndefined;
            const errorStringKey = item.errorStringKey?.();
            const hasExternalError = errorStringKey !== undefined;
            const warningStringKey = item.warningStringKey?.();
            const hasWarning = !hasInternalError && !hasExternalError && warningStringKey !== undefined;
            let tooltip: string | undefined;
            if (isInvalidInput) {
                tooltip = this.translateService.instant('artemisApp.exercise.timelineDateInvalidTooltip');
            } else if (hasInvalidDateOrder) {
                tooltip = this.translateService.instant('artemisApp.exercise.timelineDateStrictOrderTooltip');
            } else if (isInputRequiredButUndefined) {
                tooltip = this.translateService.instant('artemisApp.exercise.timelineDateRequiredTooltip');
            } else if (errorStringKey !== undefined) {
                tooltip = this.translateService.instant(errorStringKey);
            } else if (warningStringKey !== undefined) {
                tooltip = this.translateService.instant(warningStringKey);
            }

            return {
                kind: item.kind,
                labelStringKey: item.labelStringKey,
                date: item.date,
                internalDate: date?.toDate(),
                isInputRequiredButUndefined,
                hasInvalidDateOrder,
                isInvalidInput,
                isDisabled,
                hasExternalError,
                // Carried through so the invalid-item reason can reuse the key the tooltip already shows.
                errorStringKey: item.errorStringKey,
                hasWarning,
                tooltip,
            };
        });
    }

    private computeExerciseTimelineStatus(): TimelineStatus {
        const items = this.internalTimelineItems();
        const invalidItems = items.flatMap((item) => {
            const reasonKey = this.determineInvalidReasonKey(item);
            // Explicitly undefined, not falsy: an item whose errorStringKey resolves to an empty string still counts
            // as an external error and is still painted as one, so dropping it here would report the timeline valid
            // while the field shows red.
            if (reasonKey === undefined) {
                return [];
            }
            return [{ labelStringKey: item.labelStringKey, reasonKey, dateName: this.translateService.instant(item.labelStringKey) }];
        });
        return {
            // Derived from the reasons rather than enumerated separately, so validity and the explanation of it cannot
            // drift apart. determineInvalidReasonKey covers exactly the flags develop gates validity on.
            valid: invalidItems.length === 0,
            empty: items.some((item) => item.date() === undefined),
            invalidItems,
        };
    }

    /** Mirrors the tooltip precedence in {@link computeInternalTimelineItems}. */
    private determineInvalidReasonKey(item: InternalTimelineItem): string | undefined {
        if (item.isInvalidInput) {
            return 'artemisApp.exercise.form.timeline.invalidInput';
        }
        if (item.hasInvalidDateOrder) {
            return 'artemisApp.exercise.form.timeline.strictOrder';
        }
        if (item.isInputRequiredButUndefined) {
            return 'artemisApp.exercise.form.timeline.required';
        }
        // The item supplies its own key here, which is already what the tooltip shows for an external error.
        return item.errorStringKey?.();
    }
}
