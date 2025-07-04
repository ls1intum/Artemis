import { Component, InputSignal, input } from '@angular/core';
import { NgClass } from '@angular/common';
import dayjs, { Dayjs } from 'dayjs/esm';

@Component({
    selector: 'day-badge',
    standalone: true,
    imports: [NgClass],
    templateUrl: './day-badge.component.html',
    styleUrls: ['./day-badge.component.scss'],
})
export class DayBadgeComponent {
    day: InputSignal<Dayjs> = input.required<Dayjs>();
    selected: InputSignal<boolean> = input(false);

    get dayNumber(): number {
        return this.day().date();
    }

    isToday(): boolean {
        return this.day().isSame(dayjs(), 'day');
    }
}
