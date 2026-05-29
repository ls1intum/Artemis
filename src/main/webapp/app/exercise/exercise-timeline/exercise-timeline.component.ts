import { Component, WritableSignal, computed, effect, inject, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DatePickerModule } from 'primeng/datepicker';
import { TooltipModule } from 'primeng/tooltip';
import dayjs, { Dayjs } from 'dayjs/esm';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { TranslateService } from '@ngx-translate/core';

export type TimelineItem = {
    kind: 'required' | 'optional';
    labelStringKey: string;
    date: WritableSignal<Dayjs | undefined>;
    clearable?: boolean;
};

export type ExerciseTimelineStatus = {
    valid: boolean;
    empty: boolean;
};

type InternalTimelineItem = TimelineItem & {
    internalDate: Date | undefined;
    isInputRequiredButUndefined: boolean;
    isBeforePreviousDate: boolean;
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

    updateDate(item: TimelineItem, newInternalDate: Date | string | null): void {
        if (item.clearable === false && newInternalDate === null) {
            return;
        }

        const currentDate = item.date();
        const newDate = newInternalDate instanceof Date ? dayjs(newInternalDate) : undefined;
        const oldAndNewDateUndefined = currentDate === undefined && newDate === undefined;
        const oldAndNewDatesAreTheSame = currentDate !== undefined && newDate !== undefined && currentDate.isSame(newDate);
        if (oldAndNewDateUndefined || oldAndNewDatesAreTheSame) return;
        item.date.set(newDate);
    }

    handleManualInput(item: TimelineItem, event: Event): void {
        const value = (event.target as HTMLInputElement).value;
        if (value.trim() === '') {
            if (item.clearable !== false) {
                this.updateDate(item, null);
            }
            return;
        }

        if (!this.fullDateTimePattern.test(value)) {
            return;
        }

        const parsedDate = dayjs(value, this.dateTimeFormat, true);
        if (parsedDate.isValid()) {
            this.setDateIfChanged(item, parsedDate);
        }
    }

    restoreNonClearableDateIfInvalid(item: TimelineItem, event: Event): void {
        if (item.clearable !== false) {
            return;
        }

        const input = event.target as HTMLInputElement;
        const value = input.value.trim();
        const parsedDate = this.fullDateTimePattern.test(value) ? dayjs(value, this.dateTimeFormat, true) : undefined;
        if (parsedDate?.isValid()) {
            return;
        }

        input.value = item.date()?.format(this.dateTimeFormat) ?? '';
    }

    private setDateIfChanged(item: TimelineItem, newDate: Dayjs): void {
        const currentDate = item.date();
        if (currentDate?.isSame(newDate)) return;
        item.date.set(newDate);
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
            let tooltip: string | undefined;
            if (isBeforePreviousDate) {
                tooltip = this.translateService.instant('artemisApp.exercise.timelineDateOrderTooltip');
            } else if (isInputRequiredButUndefined) {
                tooltip = this.translateService.instant('artemisApp.exercise.timelineDateRequiredTooltip');
            }

            return {
                kind: item.kind,
                labelStringKey: item.labelStringKey,
                date: item.date,
                clearable: item.clearable,
                internalDate: date?.toDate(),
                isInputRequiredButUndefined,
                isBeforePreviousDate,
                tooltip,
            };
        });
    }

    private computeExerciseTimelineStatus(): ExerciseTimelineStatus {
        const items = this.internalTimelineItems();
        return {
            valid: items.every((item) => !item.isBeforePreviousDate && !item.isInputRequiredButUndefined),
            empty: items.some((item) => item.date() === undefined),
        };
    }
}
