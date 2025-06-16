import { Injectable, Signal, computed, signal } from '@angular/core';
import { CalendarEvent } from '../entities/calendar-event.model';
import { HttpClient } from '@angular/common/http';
import dayjs, { Dayjs } from 'dayjs/esm';
import isSameOrBefore from 'dayjs/plugin/isSameOrBefore';

dayjs.extend(isSameOrBefore);

@Injectable({
    providedIn: 'root',
})
export class CalendarEventService {
    private eventCache = new Map<string, CalendarEvent[]>();
    private loadingSet = signal(new Set<string>());
    private cacheWindow: Dayjs[] = [];

    constructor(private http: HttpClient) {
        const center = dayjs().startOf('month');
        const window = [-2, -1, 0, 1, 2].map((offset) => center.add(offset, 'month'));
        this.cacheWindow = window;
        const keys = window.map((d) => d.format('YYYY-MM'));
        this.loadMonths(keys);
    }

    getEventsOf(day: Dayjs): CalendarEvent[] {
        const edgeLeft = this.cacheWindow[0];
        const edgeRight = this.cacheWindow[this.cacheWindow.length - 1];
        if (day.isSame(edgeLeft, 'month')) {
            this.slideWindow('left');
        } else if (day.isSame(edgeRight, 'month')) {
            this.slideWindow('right');
        }

        const dayKey = day.format('YYYY-MM-DD');
        return this.eventCache.get(dayKey) ?? [];
    }

    isMonthLoadingSignal(month: Dayjs): Signal<boolean> {
        const key = month.format('YYYY-MM');
        return computed(() => this.loadingSet().has(key));
    }

    private slideWindow(direction: 'left' | 'right'): void {
        const oldEgde = direction === 'left' ? this.cacheWindow[0] : this.cacheWindow[4];
        const newMonth = direction === 'left' ? oldEgde.subtract(1, 'month') : oldEgde.add(1, 'month');
        if (direction === 'left') {
            const firstDayOfMonthToEvict = this.cacheWindow.pop()!;
            this.cacheWindow.unshift(newMonth);
            this.evictMonthOf(firstDayOfMonthToEvict);
        } else {
            const firstDayOfMonthToEvict = this.cacheWindow.shift()!;
            this.cacheWindow.push(newMonth);
            this.evictMonthOf(firstDayOfMonthToEvict);
        }
        this.loadMonths([newMonth.format('YYYY-MM')]);
    }

    private loadMonths(monthKeys: string[]): void {
        const nonLoadingMonthKeys = monthKeys.filter((key) => !this.loadingSet().has(key));
        this.loadingSet.update((current) => {
            const updated = new Set(current);
            nonLoadingMonthKeys.forEach((key) => updated.add(key));
            return updated;
        });
        const monthParameters = nonLoadingMonthKeys.join(',');
        this.http.get<Record<string, CalendarEvent[]>>(`/api/calendar-events?months=${monthParameters}`).subscribe({
            next: (result) => {
                for (const [dayKey, events] of Object.entries(result)) {
                    this.eventCache.set(dayKey, events);
                }

                this.loadingSet.update((current) => {
                    const updated = new Set(current);
                    monthKeys.forEach((key) => updated.delete(key));
                    return updated;
                });
            },

            error: () => {
                this.loadingSet.update((current) => {
                    const updated = new Set(current);
                    monthKeys.forEach((key) => updated.delete(key));
                    return updated;
                });
            },
        });
    }

    private evictMonthOf(dayInMonth: Dayjs): void {
        const targetMonth = dayInMonth.format('YYYY-MM');
        for (const key of this.eventCache.keys()) {
            if (key.startsWith(targetMonth)) {
                this.eventCache.delete(key);
            }
        }
    }
}
