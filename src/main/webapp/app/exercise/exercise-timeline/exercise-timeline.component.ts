import { Component, WritableSignal, computed, effect, inject, input, output, signal } from '@angular/core';
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
    otherRequiredItem?: TimelineItem;
}

export interface ExerciseTimelineStatus {
    valid: boolean;
    empty: boolean;
}

type InternalTimelineItem = TimelineItem & {
    internalDate: Date | undefined;
    isInputRequiredButUndefined: boolean;
    isBeforePreviousDate: boolean;
    isOtherRequiredItemDateUndefined: boolean;
    isInvalidInput: boolean;
    tooltip: string | undefined;
};

@Component({
    selector: 'jhi-exercise-timeline',
    imports: [DatePickerModule, FormsModule, TooltipModule, TranslateDirective],
    templateUrl: './exercise-timeline.component.html',
    styleUrl: './exercise-timeline.component.scss',
})
export class ExerciseTimelineComponent {
    private translateService = inject(TranslateService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);
    private readonly fullDateTimePattern = /^\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}$/;
    private readonly dateTimeFormat = 'DD.MM.YYYY HH:mm';
    protected readonly Date = Date;
    /** Label keys of items whose currently-typed text is non-empty but not a valid date. Drives the
     *  invalid (red border + tooltip) state so a malformed entry is flagged instead of silently dropped. */
    private invalidInputKeys = signal<Set<string>>(new Set());

    timelineItems = input.required<TimelineItem[]>();
    readonly = input<boolean>(false);
    internalTimelineItems = computed<InternalTimelineItem[]>(() => this.computeInternalTimelineItems());
    timelineStatus = computed<ExerciseTimelineStatus>(() => this.computeExerciseTimelineStatus());
    timelineStatusChange = output<ExerciseTimelineStatus>();

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
        // A non-empty, not-yet-parseable value is left untouched while the user is still typing; it is
        // only flagged as invalid once they leave the field (see handleBlur).
    }

    handleBlur(item: TimelineItem, event: Event) {
        const input = (event.target as HTMLInputElement).value;
        const inputWasCleared = input === '';
        const currentInputIsInvalidDate = this.parseManualInput(input) === undefined;
        // Previously an invalid entry was silently reverted to the last valid value, leaving the user
        // unaware of the mistake (PR #13009 review). Instead keep the entered text (keepInvalid) and flag
        // the field invalid so the red border + tooltip explain the problem and the form blocks saving.
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
            const isBeforePreviousDate =
                date !== undefined &&
                items.slice(0, index).some((previousItem) => {
                    const previousDate = previousItem.date();
                    return previousDate !== undefined && date.isBefore(previousDate);
                });
            const isInputRequiredButUndefined = item.kind === 'required' && date === undefined;
            const otherRequiredItem = item.otherRequiredItem;
            const isOtherRequiredItemDateUndefined = date !== undefined && otherRequiredItem !== undefined && otherRequiredItem.date() === undefined;
            const isInvalidInput = invalidInputKeys.has(item.labelStringKey);
            let tooltip: string | undefined;
            if (isInvalidInput) {
                tooltip = this.translateService.instant('artemisApp.exercise.timelineDateInvalidTooltip');
            } else if (isBeforePreviousDate) {
                tooltip = this.translateService.instant('artemisApp.exercise.timelineDateOrderTooltip');
            } else if (isInputRequiredButUndefined) {
                tooltip = this.translateService.instant('artemisApp.exercise.timelineDateRequiredTooltip');
            } else if (isOtherRequiredItemDateUndefined && otherRequiredItem) {
                const otherInputName = this.translateService.instant(otherRequiredItem.labelStringKey);
                tooltip = this.translateService.instant('artemisApp.exercise.timelineOtherRequiredDateTooltip', { otherInputName });
            }

            return {
                kind: item.kind,
                labelStringKey: item.labelStringKey,
                date: item.date,
                otherRequiredItem: item.otherRequiredItem,
                internalDate: date?.toDate(),
                isInputRequiredButUndefined,
                isBeforePreviousDate,
                isOtherRequiredItemDateUndefined,
                isInvalidInput,
                tooltip,
            };
        });
    }

    private computeExerciseTimelineStatus(): ExerciseTimelineStatus {
        const items = this.internalTimelineItems();
        return {
            valid: items.every((item) => !item.isBeforePreviousDate && !item.isInputRequiredButUndefined && !item.isOtherRequiredItemDateUndefined && !item.isInvalidInput),
            empty: items.some((item) => item.date() === undefined),
        };
    }
}
