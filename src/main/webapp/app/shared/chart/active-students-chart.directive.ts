import { Directive } from '@angular/core';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';

@Directive()
export class ActiveStudentsChartDirective {
    startDateAlreadyPassed = true;
    currentSpanToStartDate: number; // the number of weeks between the start date of the course and the current date
    currentOffsetToEndDate = 0; // the number of weeks between the end date of the course and the current date

    /**
     * sets values for the offset attributes of this directive
     * @param course the corresponding course
     * @param normalTimeSpan the amount of weeks a view of a chart normally contains
     * @protected
     */
    protected determineDisplayedPeriod(course: Course, normalTimeSpan: number): void {
        const now = dayjs();
        this.currentSpanToStartDate = normalTimeSpan;
        if (course.startDate) {
            if (now.isBefore(course.startDate)) {
                this.startDateAlreadyPassed = false;
            } else if (this.determineDifferenceBetweenIsoWeeks(dayjs(course.startDate), now) < normalTimeSpan - 1) {
                this.currentSpanToStartDate = this.determineDifferenceBetweenIsoWeeks(dayjs(course.startDate), now) + 1;
            }
        }
        if (course.endDate && now.isAfter(course.endDate) && this.determineDifferenceBetweenIsoWeeks(dayjs(course.endDate), now) > 0) {
            this.currentOffsetToEndDate = this.determineDifferenceBetweenIsoWeeks(dayjs(course.endDate), now);
        }
    }

    /**
     * Auxiliary method returning the number of weeks between two dates.
     * Note: The week of the most recent date is not included, e.g. date1: 01.05.22, date2: 9.05.22 returns 1
     * @param date1 the date that is assumed to be before date 2
     * @param date2 the date that is assumed to be the most recent date
     * @protected
     */
    protected determineDifferenceBetweenIsoWeeks(date1: dayjs.Dayjs, date2: dayjs.Dayjs): number {
        return (date2.isoWeek() - date1.isoWeek() + date1.isoWeeksInYear()) % date1.isoWeeksInYear();
    }
}
