import { Component, WritableSignal, computed, effect, inject, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DatePickerModule } from 'primeng/datepicker';
import { InputMaskModule } from 'primeng/inputmask';
import { TooltipModule } from 'primeng/tooltip';
import dayjs, { Dayjs } from 'dayjs/esm';
import { getCurrentLocaleSignal } from 'app/shared/util/global.utils';
import { TranslateService } from '@ngx-translate/core';

export type TimelineItem =
    | { kind: 'required'; labelStringKey: string; date: WritableSignal<Dayjs | undefined> }
    | { kind: 'optional'; labelStringKey: string; date: WritableSignal<Dayjs | undefined> };

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
    imports: [DatePickerModule, FormsModule, InputMaskModule, TooltipModule, TranslateDirective],
    templateUrl: './exercise-timeline.html',
    styleUrl: './exercise-timeline.scss',
})
export class ExerciseTimeline {
    private translateService = inject(TranslateService);
    private currentLocale = getCurrentLocaleSignal(this.translateService);
    timelineItems = input.required<TimelineItem[]>();
    readonly = input.required<boolean>();
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
        const currentDate = item.date();
        let newDate: Dayjs | undefined;
        if (newInternalDate instanceof Date) {
            newDate = dayjs(newInternalDate);
        } else {
            const parsedDate = typeof newInternalDate === 'string' ? this.parseMaskedDate(newInternalDate) : undefined;
            newDate = parsedDate ? dayjs(parsedDate) : undefined;
        }

        const oldAndNewDateUndefined = currentDate === undefined && newDate === undefined;
        const oldAndNewDatesAreTheSame = currentDate !== undefined && newDate !== undefined && currentDate.isSame(newDate);
        if (oldAndNewDateUndefined || oldAndNewDatesAreTheSame) return;

        item.date.set(newDate);
    }

    private parseMaskedDate(value: string): Date | undefined {
        const match = /^(\d{2}) \. (\d{2}) \. (\d{4}) \| (\d{2}) : (\d{2})$/.exec(value);
        if (!match) {
            return undefined;
        }

        const [, day, month, year, hour, minute] = match.map(Number);
        const date = new Date(year, month - 1, day, hour, minute);
        if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day || date.getHours() !== hour || date.getMinutes() !== minute) {
            return undefined;
        }
        return date;
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
