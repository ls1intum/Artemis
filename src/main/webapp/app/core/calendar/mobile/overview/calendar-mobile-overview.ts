import { Component, OnInit } from '@angular/core';
import { NgStyle } from '@angular/common';
import dayjs, { Dayjs } from 'dayjs/esm';
import * as utils from 'app/core/calendar/shared/util/calendar-util';
import { CalendarMobileMonthSection } from 'app/core/calendar/mobile/month-section/calendar-mobile-month-section';
import { CalendarDayBadgeComponent } from 'app/core/calendar/shared/calendar-day-badge/calendar-day-badge.component';

enum Swipe {
    left,
    right,
    none,
}

@Component({
    selector: 'calendar-mobile-overview',
    imports: [NgStyle, CalendarMobileMonthSection, CalendarDayBadgeComponent],
    templateUrl: './calendar-mobile-overview.html',
    styleUrl: './calendar-mobile-overview.scss',
})
export class CalendarMobileOverviewComponent implements OnInit {
    readonly utils = utils;
    readonly transition = 'transform 0.5s cubic-bezier(0.33, 1, 0.68, 1)';

    firstDaysOfVisibleMonths: Dayjs[] = [];
    firstDaysOfVisibleWeeks?: Dayjs[];
    visibleDays?: Dayjs[];

    onSwipeMonthTransform = 'translateX(-100vw)';
    onSwipeWeekTransform = 'translateX(-100vw)';
    onSwipeDayTransform = 'translateX(-100vw)';
    transitionEnabled = false;

    private horizontalTouchStartPosition = 0;
    private horizontalTouchDelta = 0;

    ngOnInit(): void {
        const startThisMonth = dayjs().startOf('month');
        const startOfLastMonth = startThisMonth.subtract(1, 'month');
        const startOfNextMonth = startThisMonth.add(1, 'month');
        this.firstDaysOfVisibleMonths = [startOfLastMonth, startThisMonth, startOfNextMonth];
    }

    selectDay(day: Dayjs): void {
        const previousDay = day.subtract(1, 'day');
        const nextDay = day.add(1, 'day');
        this.visibleDays = [previousDay, day, nextDay];

        const firstDayOfThisWeek = day.startOf('isoWeek');
        const firstDayOfLastWeek = firstDayOfThisWeek.subtract(1, 'week');
        const firstDayOfNextWeek = firstDayOfThisWeek.add(1, 'week');
        this.firstDaysOfVisibleWeeks = [firstDayOfLastWeek, firstDayOfThisWeek, firstDayOfNextWeek];
    }

    unselectDay() {
        this.visibleDays = undefined;
        this.firstDaysOfVisibleWeeks = undefined;
    }

    isDayAndWeekSelected(): boolean {
        return this.visibleDays !== undefined && this.firstDaysOfVisibleWeeks !== undefined;
    }

    isSelected(day: Dayjs): boolean {
        if (this.isDayAndWeekSelected()) {
            const centeredDay = this.visibleDays![1];
            return day.isSame(centeredDay, 'day');
        } else {
            return false;
        }
    }

    onTouchStart(event: TouchEvent) {
        this.transitionEnabled = false;
        this.horizontalTouchStartPosition = event.touches[0].clientX;
    }

    onTouchMove(event: TouchEvent) {
        this.horizontalTouchDelta = event.touches[0].clientX - this.horizontalTouchStartPosition;
        const transform = `translateX(calc(${this.horizontalTouchDelta}px - 100vw))`;
        if (this.visibleDays) {
            const effectiveNudgeOnFirstDayOfCenteredWeek = this.centeredDayIsFirstDayOfCenteredWeek() && this.horizontalTouchDelta > 0;
            const effectiveNudgeOnLastDayOfCenteredWeek = this.centeredDayIsLastDayOfCenteredWeek() && this.horizontalTouchDelta < 0;
            if (effectiveNudgeOnFirstDayOfCenteredWeek || effectiveNudgeOnLastDayOfCenteredWeek) {
                this.onSwipeWeekTransform = transform;
            }
            this.onSwipeDayTransform = transform;
        } else {
            this.onSwipeMonthTransform = transform;
        }
    }

    onTouchEnd() {
        this.transitionEnabled = true;
        const swipe: Swipe = this.horizontalTouchDelta < -50 ? Swipe.left : this.horizontalTouchDelta > 50 ? Swipe.right : Swipe.none;
        if (this.visibleDays) {
            const effectiveSwipeOnFirstDayOfCenteredWeek = this.centeredDayIsFirstDayOfCenteredWeek() && (swipe === Swipe.right || swipe === Swipe.none);
            const effectiveSwipeOnLastDayOfCenteredWeek = this.centeredDayIsLastDayOfCenteredWeek() && (swipe === Swipe.left || swipe === Swipe.none);
            if (effectiveSwipeOnFirstDayOfCenteredWeek || effectiveSwipeOnLastDayOfCenteredWeek) {
                this.transitionToSwipeDestinationThenRecenter('weeks', swipe);
            }
            this.transitionToSwipeDestinationThenRecenter('days', swipe);
        } else {
            this.transitionToSwipeDestinationThenRecenter('months', swipe);
        }
        this.horizontalTouchDelta = 0;
    }

    private centeredDayIsLastDayOfCenteredWeek(): boolean {
        if (!this.visibleDays || !this.firstDaysOfVisibleWeeks) return false;
        const centeredDay = this.visibleDays[1];
        const lastDayOfCenteredWeek = this.firstDaysOfVisibleWeeks[1].endOf('isoWeek');
        return centeredDay.isSame(lastDayOfCenteredWeek, 'day');
    }

    private centeredDayIsFirstDayOfCenteredWeek(): boolean {
        if (!this.visibleDays || !this.firstDaysOfVisibleWeeks) return false;
        const centeredDay = this.visibleDays[1];
        const firstDayOfCenteredWeek = this.firstDaysOfVisibleWeeks[1];
        return centeredDay.isSame(firstDayOfCenteredWeek, 'day');
    }

    private transitionToSwipeDestinationThenRecenter(swipingTarget: 'months' | 'weeks' | 'days', swipe: Swipe) {
        const onSwipeTransform = swipe === Swipe.right ? 'translateX(0vw)' : swipe === Swipe.left ? 'translateX(-200vw)' : 'translateX(-100vw)';

        if (swipingTarget === 'months') {
            this.onSwipeMonthTransform = onSwipeTransform;
        } else if (swipingTarget === 'weeks') {
            this.onSwipeWeekTransform = onSwipeTransform;
        } else {
            this.onSwipeDayTransform = onSwipeTransform;
        }

        const dataToRecenter = swipingTarget == 'months' ? this.firstDaysOfVisibleMonths : swipingTarget === 'weeks' ? this.firstDaysOfVisibleWeeks : this.visibleDays;
        const recenteringUnit: dayjs.ManipulateType = swipingTarget == 'months' ? 'month' : swipingTarget === 'weeks' ? 'week' : 'day';
        if (swipe !== Swipe.none) {
            setTimeout(() => {
                if (swipe === Swipe.left) {
                    dataToRecenter?.push(dataToRecenter[2].add(1, recenteringUnit));
                    dataToRecenter?.shift();
                } else if (swipe === Swipe.right) {
                    dataToRecenter?.unshift(dataToRecenter[0].subtract(1, recenteringUnit));
                    dataToRecenter?.pop();
                }
                this.transitionEnabled = false;
                const centerTransform = 'translateX(-100vw)';
                if (swipingTarget === 'months') {
                    this.onSwipeMonthTransform = centerTransform;
                } else if (swipingTarget === 'weeks') {
                    this.onSwipeWeekTransform = centerTransform;
                } else {
                    this.onSwipeDayTransform = centerTransform;
                    this.updateVisibleMonthsIfNeeded();
                }
            }, 500);
        }
    }

    private updateVisibleMonthsIfNeeded() {
        if (!this.visibleDays) return;
        const centeredDay = this.visibleDays[1];
        const previousDay = this.visibleDays[0];
        const nextDay = this.visibleDays[2];
        if (!centeredDay.isSame(previousDay, 'month') || !centeredDay.isSame(nextDay, 'month')) {
            const newCenteredFirstDayOfMonth = centeredDay.startOf('month');
            const newPreviousFirstDayOfMonth = newCenteredFirstDayOfMonth.subtract(1, 'month');
            const newNextFirstDayOfMonth = newCenteredFirstDayOfMonth.add(1, 'month');
            this.firstDaysOfVisibleMonths = [newPreviousFirstDayOfMonth, newCenteredFirstDayOfMonth, newNextFirstDayOfMonth];
        }
    }
}
