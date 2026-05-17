import { Component, WritableSignal, computed, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DatePickerModule } from 'primeng/datepicker';
import { InputMaskModule } from 'primeng/inputmask';
import { TooltipModule } from 'primeng/tooltip';

export type TimelineItem =
    | { kind: 'required'; labelStringKey: string; date: WritableSignal<Date | undefined> }
    | { kind: 'optional'; labelStringKey: string; date: WritableSignal<Date | undefined> };

type InternalTimelineItem = TimelineItem & {
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
    timelineItems = input.required<TimelineItem[]>();
    internalTimelineItems = computed<InternalTimelineItem[]>(() => this.computeInternalTimelineItems());

    updateDate(item: TimelineItem, value: Date | string | null): void {
        if (value instanceof Date) {
            item.date.set(value);
            return;
        }
        item.date.set(typeof value === 'string' ? this.parseMaskedDate(value) : undefined);
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
            const date = item.date();
            const isBeforePreviousDate =
                date !== undefined &&
                items.slice(0, index).some((previousItem) => {
                    const previousDate = previousItem.date();
                    return previousDate !== undefined && date < previousDate;
                });
            const isInputRequiredButUndefined = item.kind === 'required' && date === undefined;
            let tooltip: string | undefined;
            if (isBeforePreviousDate) {
                tooltip = 'This date must be after or equal to all preceding dates.';
            } else if (isInputRequiredButUndefined) {
                tooltip = 'This date is required.';
            }

            return {
                kind: item.kind,
                labelStringKey: item.labelStringKey,
                date: item.date,
                isInputRequiredButUndefined,
                isBeforePreviousDate,
                tooltip,
            };
        });
    }
}
