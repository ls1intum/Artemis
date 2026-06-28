import { Component, WritableSignal, computed, effect, inject, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
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
    tooltip: string | undefined;
};

@Component({
    selector: 'jhi-exercise-timeline',
    imports: [DatePickerModule, FormsModule, TooltipModule, TranslateDirective, ArtemisTranslatePipe],
    templateUrl: './exercise-timeline.component.html',
    styleUrl: './exercise-timeline.component.scss',
})
export class ExerciseTimelineComponent {
    private translateService = inject(TranslateService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);
    private readonly fullDateTimePattern = /^\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}$/;
    private readonly dateTimeFormat = 'DD.MM.YYYY HH:mm';
    protected readonly Date = Date;

    timelineItems = input.required<TimelineItem[]>();
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
        }
        if (value === '') {
            this.setDateIfChanged(item, undefined);
        }
    }

    handleBlur(item: TimelineItem, event: Event) {
        const inputElement = event.target as HTMLInputElement;
        const input = inputElement.value;
        const inputWasCleared = input === '';
        const currentInputIsInvalidDate = this.parseManualInput(input) === undefined;
        if (currentInputIsInvalidDate && !inputWasCleared) {
            const previousDate = item.date();
            inputElement.value = previousDate ? previousDate.format(this.dateTimeFormat) : '';
        }
    }

    private setDateIfChanged(item: TimelineItem, newDate?: Dayjs) {
        const currentDate = item.date();
        if (currentDate?.isSame(newDate)) return;
        item.date.set(newDate);
    }

    private parseManualInput(value: string): Dayjs | undefined {
        if (!this.fullDateTimePattern.test(value)) return undefined;
        const parsedDate = dayjs(value, this.dateTimeFormat, true);
        return parsedDate.isValid() ? parsedDate : undefined;
    }

    private computeInternalTimelineItems(): InternalTimelineItem[] {
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
            let tooltip: string | undefined;
            if (isBeforePreviousDate) {
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
                tooltip,
            };
        });
    }

    private computeExerciseTimelineStatus(): ExerciseTimelineStatus {
        const items = this.internalTimelineItems();
        return {
            valid: items.every((item) => !item.isBeforePreviousDate && !item.isInputRequiredButUndefined && !item.isOtherRequiredItemDateUndefined),
            empty: items.some((item) => item.date() === undefined),
        };
    }
}
