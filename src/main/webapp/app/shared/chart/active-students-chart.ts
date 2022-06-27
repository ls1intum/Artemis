import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';

export abstract class ActiveStudentsChart {
    startDateAlreadyPassed = true;
    currentOffsetToEndDate = 0; // the number of weeks between the end date of the course and the current date
    currentSpanSize: number;

    /**
     * sets values for the offset attributes of this directive
     * @param course the corresponding course
     * @param normalTimeSpan the amount of weeks a view of a chart normally contains
     * @protected
     */
    protected determineDisplayedPeriod(course: Course, normalTimeSpan: number): void {
        const now = dayjs();
        this.currentSpanSize = normalTimeSpan;
        if (course.startDate) {
            this.handleCourseStartDate(dayjs(course.startDate), now, normalTimeSpan);
        }
        if (course.endDate && now.isAfter(course.endDate)) {
            this.handleCourseEndDate(course, now);
        }
    }

    /**
     * Helper method that handles the existence of a course start date
     * @param courseStartDate the start date of a course
     * @param currentDate the current date
     * @param normalTimeSpan the normal time span an active students line chart displays
     * @private
     */
    private handleCourseStartDate(courseStartDate: dayjs.Dayjs, currentDate: dayjs.Dayjs, normalTimeSpan: number): void {
        if (currentDate.isBefore(courseStartDate)) {
            this.startDateAlreadyPassed = false;
        } else {
            this.currentSpanSize = Math.min(this.determineDifferenceBetweenIsoWeeks(courseStartDate, currentDate) + 1, normalTimeSpan);
        }
    }

    /**
     * Helper method that handles the existence of a course end date and its side effects if a start date exists as well
     * @param course the corresponding course
     * @param currentDate the current date
     * @private
     */
    private handleCourseEndDate(course: Course, currentDate: dayjs.Dayjs): void {
        this.currentOffsetToEndDate = this.determineDifferenceBetweenIsoWeeks(dayjs(course.endDate), currentDate);
        if (course.startDate) {
            this.currentSpanSize = Math.min(this.determineDifferenceBetweenIsoWeeks(dayjs(course.startDate), dayjs(course.endDate)) + 1, this.currentSpanSize);
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
        const normalizedDate1 = date1.isoWeekday(1).hour(2).minute(0).second(0).millisecond(0);
        const normalizedDate2 = date2.isoWeekday(1).hour(2).minute(0).second(0).millisecond(0);
        return normalizedDate2.diff(normalizedDate1, 'week');
    }
}
