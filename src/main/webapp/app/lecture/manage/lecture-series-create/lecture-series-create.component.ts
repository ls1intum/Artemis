import { Component, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { FloatLabelModule } from 'primeng/floatlabel';
import { InputMaskModule } from 'primeng/inputmask';
import dayjs, { Dayjs } from 'dayjs/esm';

type WeekdayIndex = 1 | 2 | 3 | 4 | 5 | 6 | 7;

interface WeekdayOption {
    label: string;
    weekdayIndex: WeekdayIndex;
}

@Component({
    selector: 'jhi-lecture-series-create',
    imports: [SelectModule, FormsModule, DatePickerModule, FloatLabelModule, InputMaskModule],
    templateUrl: './lecture-series-create.component.html',
    styleUrl: './lecture-series-create.component.scss',
})
export class LectureSeriesCreateComponent {
    courseId = input.required<number>();

    weekdayOptions: WeekdayOption[] = [
        { label: 'Monday', weekdayIndex: 1 },
        { label: 'Tuesday', weekdayIndex: 2 },
        { label: 'Wednesday', weekdayIndex: 3 },
        { label: 'Thursday', weekdayIndex: 4 },
        { label: 'Friday', weekdayIndex: 5 },
        { label: 'Saturday', weekdayIndex: 6 },
        { label: 'Sunday', weekdayIndex: 7 },
    ];
    selectedWeekdayIndex = signal<WeekdayIndex | undefined>(undefined);

    onSelectedWeekdayOptionChange(optionWeekdayIndex: WeekdayIndex) {
        this.selectedWeekdayIndex.set(optionWeekdayIndex);
    }

    startTime = signal<string | undefined>(undefined);
    onStartTimeChange(time: string) {
        this.startTime.set(time);
    }

    endTime = signal<string | undefined>(undefined);
    onEndTimeChange(time: string) {
        this.endTime.set(time);
    }

    getHourAndMinute(time: string): [number, number] | undefined {
        const [hh, mm] = time.split(':');
        return [parseInt(hh, 10), parseInt(mm, 10)];
    }

    endDate = signal<Date | undefined>(undefined);

    generateStartDatesFromToday(weekdayIndex: number, hour: number, minute: number, endDate: Date): Dayjs[] {
        const start = dayjs();
        const end = dayjs(endDate).endOf('day');
        let first = start.isoWeekday(weekdayIndex).hour(hour).minute(minute).second(0).millisecond(0);
        if (first.isBefore(start)) first = first.add(1, 'week');
        const result: Dayjs[] = [];
        for (let currentDate = first; !currentDate.isAfter(end); currentDate = currentDate.add(1, 'week')) result.push(currentDate);
        return result;
    }
}
