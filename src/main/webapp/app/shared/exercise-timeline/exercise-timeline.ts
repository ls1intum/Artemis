import { Component, WritableSignal, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DatePickerModule } from 'primeng/datepicker';
import { InputMaskModule } from 'primeng/inputmask';

export type TimelineItem =
    | { kind: 'required'; labelStringKey: string; date: WritableSignal<Date | undefined> }
    | { kind: 'optional'; labelStringKey: string; date: WritableSignal<Date | undefined> };

@Component({
    selector: 'jhi-exercise-timeline',
    imports: [DatePickerModule, FormsModule, InputMaskModule, TranslateDirective],
    templateUrl: './exercise-timeline.html',
    styleUrl: './exercise-timeline.scss',
})
export class ExerciseTimeline {
    timelineItems = input.required<TimelineItem[]>();

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
}
