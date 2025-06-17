import { Component, Input } from '@angular/core';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'all-day-event-section',
    imports: [],
    templateUrl: './all-day-event-section.component.html',
    styleUrl: './all-day-event-section.component.scss',
})
export class AllDayEventSectionComponent {
    @Input() days: dayjs.Dayjs[] = [];
}
