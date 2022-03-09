import { Directive } from '@angular/core';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';

@Directive()
export class ActiveStudentsChartDirective {
    startDateAlreadyPassed = true;
    currentSpanToStartDate: number;
    currentOffsetToEndDate = 0;

    protected determineDisplayedPeriod(course: Course, normalTimeSpan: number) {
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

    protected determineDifferenceBetweenIsoWeeks(date1: dayjs.Dayjs, date2: dayjs.Dayjs) {
        return (date2.isoWeek() - date1.isoWeek() + date1.isoWeeksInYear()) % date1.isoWeeksInYear();
    }
}
